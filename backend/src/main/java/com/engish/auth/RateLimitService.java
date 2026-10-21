package com.engish.auth;

import com.engish.redis.RedisSettings;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Distributed request rate limits (Bucket4j on Redis). Separate from the PostgreSQL daily quotas in QuotaService. */
@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    public enum Kind { AUTH, CREATE, TURN, COMPLETE, ALTERNATIVE }

    public record Decision(boolean allowed, boolean unavailable, long retryAfterSeconds) {
        static Decision ok() { return new Decision(true, false, 0); }
    }

    private final ProxyManager<String> proxies;
    private final String prefix;
    private final boolean failOpen;
    private final Map<Kind, BucketConfiguration> configs = new EnumMap<>(Kind.class);
    private final AtomicLong lastWarnMs = new AtomicLong();

    public RateLimitService(ProxyManager<String> proxies, RateLimitProperties p, RedisSettings redis) {
        this.proxies = proxies;
        this.prefix = redis.keyPrefix();
        this.failOpen = p.failOpen();
        Duration window = Duration.ofSeconds(p.windowSeconds());
        configs.put(Kind.AUTH, config(p.authPerIp(), window));
        configs.put(Kind.CREATE, config(p.createPerUser(), window));
        configs.put(Kind.TURN, config(p.turnPerUser(), window));
        configs.put(Kind.COMPLETE, config(p.completePerUser(), window));
        configs.put(Kind.ALTERNATIVE, config(p.alternativePerUser(), window));
    }

    private static BucketConfiguration config(int limit, Duration window) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillIntervally(limit, window).build())
                .build();
    }

    /** subject is the client IP for AUTH and the user id for everything else. */
    public Decision check(Kind kind, String subject) {
        String key = prefix + ":rl:" + kind.name().toLowerCase(Locale.ROOT) + ":" + subject;
        BucketConfiguration cfg = configs.get(kind);
        try {
            ConsumptionProbe probe = proxies.builder().build(key, () -> cfg).tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) return Decision.ok();
            long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
            return new Decision(false, false, seconds);
        } catch (RuntimeException e) {
            warnThrottled(e);
            return failOpen ? Decision.ok() : new Decision(false, true, 10);
        }
    }

    private void warnThrottled(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastWarnMs.get();
        if (now - last > 30_000 && lastWarnMs.compareAndSet(last, now)) {
            log.warn("Rate limiting could not reach Redis ({}). failOpen={}", e.getClass().getSimpleName(), failOpen);
        }
    }
}