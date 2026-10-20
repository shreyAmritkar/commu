package com.engish.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlternativeReplyRepository extends JpaRepository<AlternativeReply, Long> {
    List<AlternativeReply> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<AlternativeReply> findBySessionIdAndTurnIdAndStyleId(UUID sessionId, Long turnId, Long styleId);

    @Query("select count(a) from AlternativeReply a, Session s where a.sessionId = s.id and s.userId = :userId and a.createdAt >= :since")
    long countSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
