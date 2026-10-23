package com.engish.notification;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class NotificationCleanup {
    private static final Logger log = LoggerFactory.getLogger(NotificationCleanup.class);

    private final NotificationService service;
    private final NotificationProperties props;

    public NotificationCleanup(NotificationService service, NotificationProperties props) {
        this.service = service;
        this.props = props;
    }

    @Recurring(id = "notifications-cleanup", cron = "30 3 * * *")
    @Job(name = "Delete old notifications")
    public void purge() {
        int deleted = service.purgeOlderThan(Instant.now().minus(props.retentionDays(), ChronoUnit.DAYS));
        log.info("Deleted {} notifications older than {} days", deleted, props.retentionDays());
    }
}