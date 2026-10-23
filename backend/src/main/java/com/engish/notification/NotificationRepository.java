package com.engish.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}