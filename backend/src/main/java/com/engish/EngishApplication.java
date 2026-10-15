package com.engish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EngishApplication {
    public static void main(String[] args) {
        // Windows reports legacy zone names (e.g. Asia/Calcutta) that newer PostgreSQL rejects.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(EngishApplication.class, args);
    }
}
