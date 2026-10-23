package com.engish.feedback;

import com.engish.common.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlternativeReplyRepository extends JpaRepository<AlternativeReply, Long> {
    List<AlternativeReply> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<AlternativeReply> findBySessionIdAndTurnIdAndStyleId(UUID sessionId, Long turnId, Long styleId);

    @Query("select count(a) from AlternativeReply a, Session s where a.sessionId = s.id and s.userId = :userId and a.createdAt >= :since")
    long countSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AlternativeReply a set a.status = :to, a.attempts = a.attempts + 1, a.updatedAt = :now "
            + "where a.id = :id and a.status in :from")
    int claim(@Param("id") Long id, @Param("from") Collection<JobStatus> from,
              @Param("to") JobStatus to, @Param("now") Instant now);
}