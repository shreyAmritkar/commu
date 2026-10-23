package com.engish.notification;

import com.engish.redis.RedisSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fan-out across instances. Pub/sub is at-most-once, which is fine: the
 * database row is the source of truth
 * and clients re-read unread notifications on reconnect and on a slow poll.
 */
@Component
public class NotificationBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);

    record Envelope(UUID userId, NotificationDto notification) {
    }

    private final StatefulRedisConnection<String, String> publisher;
    private final StatefulRedisPubSubConnection<String, String> subscriber;
    private final NotificationHub hub;
    private final ObjectMapper mapper;
    private final String channel;
    private final AtomicLong lastWarnMs = new AtomicLong();

    public NotificationBroadcaster(RedisClient client,
            @Qualifier("redisStringConnection") StatefulRedisConnection<String, String> publisher,
            RedisSettings settings, NotificationHub hub, ObjectMapper mapper) {
        this.publisher = publisher;
        this.hub = hub;
        this.mapper = mapper;
        this.channel = settings.keyPrefix() + ":notifications";
        try {
            this.subscriber = client.connectPubSub();
        } catch (RedisConnectionException e) {
            throw new IllegalStateException(
                    "Cannot connect to Redis for notifications at " + settings.host() + ":" + settings.port(), e);
        }
        this.subscriber.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String ch, String message) {
                onMessage(message);
            }
        });
        this.subscriber.sync().subscribe(channel); // Lettuce re-subscribes automatically after a reconnect
    }

    public void publish(UUID userId, NotificationDto dto) {
        log.info("@@ Publishing notification {} for user {}", dto, userId);
        try {
            publisher.sync().publish(channel, mapper.writeValueAsString(new Envelope(userId, dto)));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise a notification event");
        } catch (RuntimeException e) {
            warnThrottled(e);
            hub.deliver(userId, dto); // Redis is down: at least serve this instance's own streams; others catch up by
                                      // polling
        }
    }

    private void onMessage(String message) {
        try {
            Envelope env = mapper.readValue(message, Envelope.class);
            hub.deliver(env.userId(), env.notification());
        } catch (Exception e) {
            log.warn("Ignored an unreadable notification event. Payload: {}", message, e);
        }
    }

    private void warnThrottled(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastWarnMs.get();
        if (now - last > 30_000 && lastWarnMs.compareAndSet(last, now)) {
            log.warn("Could not publish a notification event to Redis ({})", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void close() {
        subscriber.close();
    }
}