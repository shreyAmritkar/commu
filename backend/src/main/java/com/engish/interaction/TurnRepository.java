package com.engish.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnRepository extends JpaRepository<SessionTurn, Long> {
    List<SessionTurn> findBySessionIdOrderBySeqAsc(UUID sessionId);

    List<SessionTurn> findTop10BySessionIdOrderBySeqDesc(UUID sessionId);

    Optional<SessionTurn> findByIdAndSessionId(Long id, UUID sessionId);

    Optional<SessionTurn> findBySessionIdAndSeq(UUID sessionId, int seq);

    @Query("select count(t) from SessionTurn t, Session s where t.sessionId = s.id and s.userId = :userId and t.speaker = :speaker and t.createdAt >= :since")
    long countUserTurnsSince(@Param("userId") UUID userId, @Param("speaker") Speaker speaker,
            @Param("since") Instant since);
}
