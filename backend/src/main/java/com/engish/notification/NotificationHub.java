package com.engish.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Holds this instance's open SSE streams, per user. Other instances hold theirs; Redis pub/sub connects them. */
@Component
public class NotificationHub {
    private static final Logger log = LoggerFactory.getLogger(NotificationHub.class);

    private final Map<UUID, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sse-delivery");
        t.setDaemon(true);
        return t;
    });
    private final NotificationProperties props;
    private final ObjectMapper mapper;

    public NotificationHub(NotificationProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        pool.scheduleWithFixedDelay(this::heartbeat, props.heartbeatSeconds(), props.heartbeatSeconds(), TimeUnit.SECONDS);
    }

    public SseEmitter open(UUID userId) {
        log.info("@@ Opening SSE stream for user {}", userId);
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(props.streamTimeoutMinutes()));
        List<SseEmitter> evicted = new ArrayList<>();
        streams.compute(userId, (k, existing) -> {
            Deque<SseEmitter> d = existing == null ? new ConcurrentLinkedDeque<>() : existing;
            d.addLast(emitter);
            while (d.size() > props.maxStreamsPerUser()) {
                SseEmitter oldest = d.pollFirst();
                if (oldest != null) evicted.add(oldest);
            }
            return d;
        });
        evicted.forEach(this::evict);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> remove(userId, emitter));
        send(userId, emitter, SseEmitter.event().name("ready").data("ok"));
        return emitter;
    }

    /** Sends the notification to every stream this instance holds for the user. */
    public void deliver(UUID userId, NotificationDto dto) {
        log.info("@@ delivering notification");
        Deque<SseEmitter> list = streams.get(userId);
        if (list == null || list.isEmpty()){
            log.info("@@ No open SSE streams for user {}, notification {} will be delivered on next poll", userId, dto.id());
            return;
        };
        String json;
        try {
            json = mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.warn("@@ Could not serialise notification {}", dto.id());
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                pool.execute(() -> send(userId, emitter,
                        SseEmitter.event().name("notification").id(String.valueOf(dto.id())).data(json)));
            } catch (RuntimeException shuttingDown) {
                log.info("@@ SSE delivery pool is shutting down; notification {} will be delivered on next poll", dto.id());
                return;
            }
        }
    }

    private void heartbeat() {
        streams.forEach((userId, list) -> list.forEach(e -> send(userId, e, SseEmitter.event().comment("hb"))));
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        log.info("@@ Sending SSE event {} to user {}", event, userId);
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // already completed
            }
        }
    }

    /** Tells an old stream it was replaced (so that tab does not fight for the slot), then closes it. */
    private void evict(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("replaced").data("another tab"));
        } catch (IOException | IllegalStateException ignored) {
            // client already gone
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already completed
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (k, d) -> {
            d.remove(emitter);
            return d.isEmpty() ? null : d;
        });
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
        streams.values().forEach(d -> d.forEach(e -> {
            try {
                e.complete();
            } catch (RuntimeException ignored) {
                // closing anyway
            }
        }));
        streams.clear();
        log.info("Notification streams closed");
    }
}