package com.engish.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final NotificationBroadcaster broadcaster;

    public NotificationService(NotificationRepository repo, NotificationBroadcaster broadcaster) {
        this.repo = repo;
        this.broadcaster = broadcaster;
    }

    // Titles and ids only: never put transcript text in a notification.
    public void feedbackReady(UUID userId, UUID sessionId, String scenarioTitle) {
        create(userId, "FEEDBACK_READY", sessionId, "feedback-ready:" + sessionId, "Your feedback is ready", scenarioTitle);
    }

    public void feedbackFailed(UUID userId, UUID sessionId, String scenarioTitle) {
        create(userId, "FEEDBACK_FAILED", sessionId, "feedback-failed:" + sessionId,
                "We couldn't prepare your feedback", scenarioTitle + ". Open it to try again.");
    }

    /** Never throws: a notification problem must not break the job that triggered it. */
    private void create(UUID userId, String type, UUID sessionId, String dedupeKey, String title, String body) {
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setSessionId(sessionId);
            n.setDedupeKey(dedupeKey);
            n.setTitle(title);
            n.setBody(body.length() <= 255 ? body : body.substring(0, 255));
            Notification saved = repo.save(n);
            broadcaster.publish(userId, NotificationDto.from(saved));   // after the row is committed
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Notification {} already exists for user {}", dedupeKey, userId);
        } catch (RuntimeException e) {
            log.warn("Could not create notification {} ({})", type, e.getClass().getSimpleName());
        }
    }

    public NotificationsDto list(UUID userId) {
        List<NotificationDto> items = repo.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationDto::from).toList();
        return new NotificationsDto(repo.countByUserIdAndReadAtIsNull(userId), items);
    }

    public void markRead(UUID userId, Long id) {
        repo.markRead(id, userId, Instant.now());   // scoped to the owner; someone else's id is simply a no-op
    }

    public void markAllRead(UUID userId) {
        repo.markAllRead(userId, Instant.now());
    }

    public int purgeOlderThan(Instant cutoff) {
        return repo.deleteOlderThan(cutoff);
    }
}