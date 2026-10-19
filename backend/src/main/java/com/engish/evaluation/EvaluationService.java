package com.engish.evaluation;

import com.engish.ai.AiService;
import com.engish.common.ApiException;
import com.engish.feedback.AlternativeReplyService;
import com.engish.interaction.SessionTurn;
import com.engish.interaction.TurnRepository;
import com.engish.session.Session;
import com.engish.session.SessionRepository;
import com.engish.session.SessionService;
import com.engish.session.SessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EvaluationService {
    private final SessionService sessions;
    private final SessionRepository sessionRepo;
    private final TurnRepository turns;
    private final EvaluationRepository evaluations;
    private final AlternativeReplyService alternatives;
    private final AiService ai;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public EvaluationService(SessionService sessions, SessionRepository sessionRepo, TurnRepository turns,
                             EvaluationRepository evaluations, AlternativeReplyService alternatives, AiService ai,
                             ObjectMapper mapper, TransactionTemplate tx) {
        this.sessions = sessions;
        this.sessionRepo = sessionRepo;
        this.turns = turns;
        this.evaluations = evaluations;
        this.alternatives = alternatives;
        this.ai = ai;
        this.mapper = mapper;
        this.tx = tx;
    }

    /** Idempotent: the evaluation is generated once, saved, and reused afterwards. */
    public ResultsDto complete(UUID id, UUID userId) {
        Session s = sessions.getOwned(id, userId);
        if (evaluations.findBySessionId(id).isEmpty()) {
            if (s.getUserTurnCount() < 1) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_TURNS", "Send at least one message before getting feedback.");
            }
            List<SessionTurn> all = turns.findBySessionIdOrderBySeqAsc(id);
            EvaluationResult result = ai.evaluate(s.getScenario(), s.getCharacter(), s, all);
            String json = toJson(result);
            tx.executeWithoutResult(status -> {
                Evaluation e = new Evaluation();
                e.setSessionId(id);
                e.setOverallScore(result.overallScore());
                e.setOutcome(result.outcome());
                e.setSummary(result.summary());
                e.setResultJson(json);
                evaluations.save(e);
                Session fresh = sessionRepo.findById(id).orElseThrow();
                fresh.setStatus(SessionStatus.COMPLETED);
                fresh.setCompletedAt(Instant.now());
                fresh.setUpdatedAt(Instant.now());
                sessionRepo.save(fresh);
            });
        }
        return results(id, userId);
    }

    public ResultsDto results(UUID id, UUID userId) {
        Session s = sessions.getOwned(id, userId);
        Evaluation e = evaluations.findBySessionId(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESULTS_NOT_READY", "Results have not been generated for this session yet."));
        EvaluationDto dto = new EvaluationDto(e.getId(), e.getOverallScore(), e.getOutcome(), e.getSummary(), fromJson(e.getResultJson()), e.getCreatedAt());
        return new ResultsDto(sessions.toDto(s), dto, alternatives.listFor(id));
    }

    private String toJson(EvaluationResult r) {
        try {
            return mapper.writeValueAsString(r);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private EvaluationResult fromJson(String json) {
        try {
            return mapper.readValue(json, EvaluationResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
