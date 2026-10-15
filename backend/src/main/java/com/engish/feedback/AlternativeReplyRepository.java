package com.engish.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlternativeReplyRepository extends JpaRepository<AlternativeReply, Long> {
    List<AlternativeReply> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<AlternativeReply> findBySessionIdAndTurnIdAndStyleId(UUID sessionId, Long turnId, Long styleId);
}
