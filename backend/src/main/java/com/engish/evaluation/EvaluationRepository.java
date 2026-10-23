package com.engish.evaluation;

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

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findBySessionId(UUID sessionId);

    List<Evaluation> findBySessionIdIn(Collection<UUID> sessionIds);

    /** Atomically moves the row to RUNNING and counts the attempt. Returns 0 if it is already DONE or FAILED. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Evaluation e set e.status = :to, e.attempts = e.attempts + 1, e.updatedAt = :now "
            + "where e.sessionId = :sid and e.status in :from")
    int claim(@Param("sid") UUID sessionId, @Param("from") Collection<JobStatus> from,
              @Param("to") JobStatus to, @Param("now") Instant now);
}