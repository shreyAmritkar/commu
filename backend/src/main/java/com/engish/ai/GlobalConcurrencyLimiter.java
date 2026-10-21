package com.engish.ai;

import java.time.Duration;

/** Cluster-wide cap on simultaneous LLM calls. Use the permit in try-with-resources. */
public interface GlobalConcurrencyLimiter {

    /** Waits up to maxWait for a permit. Throws AiException (503) when full or when coordination is unavailable. */
    Permit acquire(Duration maxWait);

    interface Permit extends AutoCloseable {
        @Override
        void close();   // never throws; idempotent
    }
}