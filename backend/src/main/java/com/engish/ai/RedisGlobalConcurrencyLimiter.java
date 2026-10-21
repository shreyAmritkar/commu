package com.engish.ai;

import com.engish.redis.RedisSettings;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed semaphore: a Redis sorted set whose members are permit ids and whose scores are lease expiry times (Redis clock).
 * Each held permit is renewed by a heartbeat; a crashed instance's permits expire by themselves.
 * Expiry does NOT stop an LLM call that is still running, so after a lost lease the call continues "untracked"
 * and the global count can be briefly exceeded (see the failure model in the docs).
 */
@Component
public class RedisGlobalConcurrencyLimiter implements GlobalConcurrencyLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisGlobalConcurrencyLimiter.class);
    private static final Permit NOOP = () -> { };

    private static final String NOW = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            """;

    private static final String EXTEND_TTL = """
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < lease then redis.call('PEXPIRE', KEYS[1], lease) end
            """;

    // KEYS[1]=set, ARGV[1]=limit, ARGV[2]=permit id, ARGV[3]=lease ms. Idempotent for an id that already holds a permit.
    private static final String ACQUIRE = NOW + """
            local lease = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) or redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[1]) then
              redis.call('ZADD', KEYS[1], now + lease, ARGV[2])
              """ + EXTEND_TTL + """
              return 1
            end
            return 0
            """;

    // KEYS[1]=set, ARGV[1]=permit id, ARGV[2]=lease ms. Renews only a lease that has not expired.
    private static final String RENEW = NOW + """
            local lease = tonumber(ARGV[2])
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) > now then
              redis.call('ZADD', KEYS[1], now + lease, ARGV[1])
              """ + EXTEND_TTL + """
              return 1
            end
            return 0
            """;

    private final RedisCommands<String, String> redis;
    private final LlmLimitProperties props;
    private final String key;
    private final long leaseMs;
    private final ScheduledExecutorService heartbeat;
    private final AtomicLong lastWarnMs = new AtomicLong();

    public RedisGlobalConcurrencyLimiter(@Qualifier("redisStringConnection") StatefulRedisConnection<String, String> connection,
                                         RedisSettings redisSettings, LlmLimitProperties props) {
        this.redis = connection.sync();
        this.props = props;
        this.key = redisSettings.keyPrefix() + ":llm:permits";
        this.leaseMs = props.leaseSeconds() * 1000L;
        this.heartbeat = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "llm-permit-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
    }

    @Override
    public Permit acquire(Duration maxWait) {
        if (props.globalMaxConcurrent() <= 0) return NOOP;   // global control disabled
        String id = UUID.randomUUID().toString();            // unique ownership of this permit
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (true) {
            try {
                if (tryAcquire(id)) return new LeasedPermit(id);
            } catch (RuntimeException e) {
                warnThrottled(e);
                safeRelease(id);   // the reply may have been lost after Redis granted the permit
                if (props.failOpen()) return NOOP;
                throw AiException.coordinationUnavailable();
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) throw AiException.busy();
            long sleepMs = Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1, 100 + ThreadLocalRandom.current().nextInt(150));
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw AiException.busy();
            }
        }
    }

    private boolean tryAcquire(String id) {
        Long r = redis.eval(ACQUIRE, ScriptOutputType.INTEGER, new String[]{key},
                String.valueOf(props.globalMaxConcurrent()), id, String.valueOf(leaseMs));
        return r != null && r == 1L;
    }

    private boolean renew(String id) {
        Long r = redis.eval(RENEW, ScriptOutputType.INTEGER, new String[]{key}, id, String.valueOf(leaseMs));
        return r != null && r == 1L;
    }

    private void safeRelease(String id) {
        try {
            redis.zrem(key, id);
        } catch (RuntimeException e) {
            warnThrottled(e);   // the lease will expire on its own
        }
    }

    private void warnThrottled(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastWarnMs.get();
        if (now - last > 30_000 && lastWarnMs.compareAndSet(last, now)) {
            log.warn("Redis unavailable for LLM concurrency control ({}). failOpen={}", e.getClass().getSimpleName(), props.failOpen());
        }
    }

    private final class LeasedPermit implements Permit {
        private final String id;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ScheduledFuture<?> task;

        LeasedPermit(String id) {
            this.id = id;
            long period = Math.max(1000, leaseMs / 3);
            this.task = heartbeat.scheduleWithFixedDelay(this::renewLease, period, period, TimeUnit.MILLISECONDS);
        }

        private void renewLease() {
            if (closed.get()) return;
            try {
                if (renew(id)) return;
                // Lease expired or Redis lost its data. The call is still running, so try to take a permit again.
                if (tryAcquire(id)) {
                    if (closed.get()) safeRelease(id);   // released while we were re-acquiring
                    else log.warn("An LLM permit lease was lost and has been re-acquired.");
                } else {
                    log.warn("An LLM permit lease was lost and global capacity is full; the call continues untracked until it finishes.");
                }
            } catch (RuntimeException e) {
                warnThrottled(e);   // keep the task alive; the next beat retries
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            task.cancel(false);
            safeRelease(id);
        }
    }
}