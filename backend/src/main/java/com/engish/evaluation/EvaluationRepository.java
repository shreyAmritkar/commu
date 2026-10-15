package com.engish.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findBySessionId(UUID sessionId);

    List<Evaluation> findBySessionIdIn(Collection<UUID> sessionIds);
}
