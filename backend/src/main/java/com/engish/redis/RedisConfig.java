package com.engish.redis;

import com.engish.auth.RateLimitProperties;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** One Lettuce client shared by Bucket4j (rate limits) and the LLM permit scripts. */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    RedisClient redisClient(RedisSettings s) {
        RedisURI.Builder uri = RedisURI.builder().withHost(s.host()).withPort(s.port()).withDatabase(s.database())
                .withSsl(s.ssl()).withTimeout(Duration.ofMillis(s.commandTimeoutMs()));
        if (s.password() != null && !s.password().isBlank()) uri.withPassword(s.password().toCharArray());

        RedisClient client = RedisClient.create(uri.build());
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)   // fail fast while disconnected
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(s.connectTimeoutMs())).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
        return client;
    }

    @Bean(name = "redisStringConnection", destroyMethod = "close")
    StatefulRedisConnection<String, String> redisStringConnection(RedisClient client, RedisSettings s) {
        try {
            return client.connect();
        } catch (RedisConnectionException e) {
            throw startupFailure(s, e);
        }
    }

    @Bean(name = "redisBucketConnection", destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> redisBucketConnection(RedisClient client, RedisSettings s) {
        try {
            return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        } catch (RedisConnectionException e) {
            throw startupFailure(s, e);
        }
    }

    @Bean
    ProxyManager<String> rateLimitProxyManager(@Qualifier("redisBucketConnection") StatefulRedisConnection<String, byte[]> connection,
                                               RateLimitProperties props) {
        // Idle bucket keys disappear from Redis shortly after they would have refilled completely.
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(60)))
                .build();
    }

    private static IllegalStateException startupFailure(RedisSettings s, RuntimeException cause) {
        return new IllegalStateException("Cannot connect to Redis at " + s.host() + ":" + s.port()
                + ". Rate limiting and LLM coordination require Redis (docker compose up -d).", cause);
    }
}