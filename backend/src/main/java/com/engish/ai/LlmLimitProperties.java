package com.engish.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "engish.llm")
public record LlmLimitProperties(int globalMaxConcurrent, int leaseSeconds, boolean failOpen) {
    public LlmLimitProperties {
        if (leaseSeconds < 6) leaseSeconds = 30;   // renewal runs at a third of the lease, so very short leases are unsafe
    }
}