package com.engish.interaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnRepository extends JpaRepository<SessionTurn, Long> {
    List<SessionTurn> findBySessionIdOrderBySeqAsc(UUID sessionId);

    List<SessionTurn> findTop10BySessionIdOrderBySeqDesc(UUID sessionId);

    Optional<SessionTurn> findByIdAndSessionId(Long id, UUID sessionId);

    Optional<SessionTurn> findBySessionIdAndSeq(UUID sessionId, int seq);
}
