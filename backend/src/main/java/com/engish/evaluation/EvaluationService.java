package com.engish.evaluation;

import com.engish.ai.AiService;
import com.engish.common.ApiException;
import com.engish.common.JobStatus;
import com.engish.feedback.AlternativeReplyService;
import com.engish.interaction.SessionTurn;
import com.engish.interaction.TurnRepository;
import com.engish.notification.NotificationService;
import com.engish.session.Session;
import com.engish.session.SessionRepository;
import com.engish.session.SessionService;
import com.engish.session.SessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evaluations run as background jobs. complete() only records the request and enqueues it; runJob() does the LLM work.
 * Jobs may run more than once (JobRunr is at-least-once), so every step here is idempotent.
 */
@Service
public class EvaluationService {
    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final SessionService sessions;
    private final SessionRepository sessionRepo;
    private final TurnRepository turns;
    private final EvaluationRepository evaluations;
    private final AlternativeReplyService alternatives;
    private final AiService ai;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final JobRequestScheduler jobs;
    private final NotificationService notifications;
    public EvaluationService(SessionService sessions, SessionRepository sessionRepo, TurnRepository turns,
                             EvaluationRepository evaluations, AlternativeReplyService alternatives, AiService ai,
                             ObjectMapper mapper, TransactionTemplate tx, JobRequestScheduler jobs, NotificationService notifications) {
        this.sessions = sessions;
        this.sessionRepo = sessionRepo;
        this.turns = turns;
        this.evaluations = evaluations;
        this.alternatives = alternatives;
        this.ai = ai;
        this.mapper = mapper;
        this.tx = tx;
        this.jobs = jobs;
        this.notifications = notifications;
    }

    /** Idempotent: starts the evaluation if needed (or restarts a failed or stale one) and returns the current state. */
    public ResultsDto complete(UUID id, UUID userId) {
        Session s = sessions.getOwned(id, userId);
        if (s.getUserTurnCount() < 1) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_TURNS", "Send at least one message before getting feedback.");
        }
        boolean enqueue = false;
        Evaluation existing = evaluations.findBySessionId(id).orElse(null);
        if (existing == null) {
            try {
                tx.executeWithoutResult(st -> {
                    Evaluation e = new Evaluation();
                    e.setSessionId(id);
                    e.setStatus(JobStatus.PENDING);
                    evaluations.save(e);
                });
                enqueue = true;
            } catch (DataIntegrityViolationException raceLost) {
                log.debug("Another request already created the evaluation for session {}", id);
            }
        } else if (existing.getStatus() == JobStatus.FAILED || isStale(existing)) {
            tx.executeWithoutResult(st -> evaluations.findBySessionId(id).ifPresent(e -> {
                e.setStatus(JobStatus.PENDING);
                e.setAttempts(0);
                e.setErrorMessage(null);
                e.setUpdatedAt(Instant.now());
                evaluations.save(e);
            }));
            enqueue = true;
        }
        if (enqueue) jobs.enqueue(new EvaluationJobRequest(id.toString()));
        return results(id, userId);
    }

    public ResultsDto results(UUID id, UUID userId) {
        Session s = sessions.getOwned(id, userId);
        Evaluation e = evaluations.findBySessionId(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESULTS_NOT_READY", "Results have not been requested for this session yet."));
        EvaluationResult details = e.getStatus() == JobStatus.DONE && e.getResultJson() != null ? fromJson(e.getResultJson()) : null;
        EvaluationDto dto = new EvaluationDto(e.getId(), e.getStatus(), e.getOverallScore(), e.getOutcome(), e.getSummary(),
                details, e.getErrorMessage(), e.getCreatedAt());
        return new ResultsDto(sessions.toDto(s), dto, alternatives.listFor(id));
    }

    /** Called by the JobRunr handler. Throwing makes JobRunr retry with backoff. */
    public void runJob(UUID sessionId) {
        int claimed = evaluations.claim(sessionId, List.of(JobStatus.PENDING, JobStatus.RUNNING), JobStatus.RUNNING, Instant.now());
        if (claimed == 0) return;   // already DONE or FAILED: a duplicate delivery

        int attempts = evaluations.findBySessionId(sessionId).map(Evaluation::getAttempts).orElse(MAX_ATTEMPTS);
        try {
            Session s = sessionRepo.findById(sessionId).orElseThrow();
            List<SessionTurn> all = turns.findBySessionIdOrderBySeqAsc(sessionId);
            EvaluationResult result = ai.evaluate(s.getScenario(), s.getCharacter(), s, all);   // LLM call: no transaction open
            String json = toJson(result);
            tx.executeWithoutResult(st -> {
                Evaluation e = evaluations.findBySessionId(sessionId).orElseThrow();
                if (e.getStatus() == JobStatus.DONE) return;
                e.setOverallScore(result.overallScore());
                e.setOutcome(result.outcome());
                e.setSummary(result.summary());
                e.setResultJson(json);
                e.setStatus(JobStatus.DONE);
                e.setErrorMessage(null);
                e.setUpdatedAt(Instant.now());
                evaluations.save(e);
                Session fresh = sessionRepo.findById(sessionId).orElseThrow();
                fresh.setStatus(SessionStatus.COMPLETED);
                fresh.setCompletedAt(Instant.now());
                fresh.setUpdatedAt(Instant.now());
                sessionRepo.save(fresh);
            });
            log.info("@@ Evaluation for session " + sessionId + " completed successfully on attempt " + attempts);
            notifications.feedbackReady(s.getUserId(), sessionId, s.getScenario().getTitle());
        } catch (RuntimeException ex) {
            boolean giveUp = attempts >= MAX_ATTEMPTS;
            String message = ex instanceof ApiException api ? api.getMessage() : "Something went wrong while preparing your feedback.";
            tx.executeWithoutResult(st -> evaluations.findBySessionId(sessionId).ifPresent(e -> {
                if (e.getStatus() == JobStatus.DONE) return;
                e.setStatus(giveUp ? JobStatus.FAILED : JobStatus.PENDING);
                e.setErrorMessage(truncate(message, 300));
                e.setUpdatedAt(Instant.now());
                evaluations.save(e);
            }));
            log.warn("Evaluation for session {} failed on attempt {} ({}), {}", sessionId, attempts,
                    ex.getClass().getSimpleName(), giveUp ? "giving up" : "will retry");
            if (giveUp) {
                sessionRepo.findById(sessionId).ifPresent(sess ->
                        notifications.feedbackFailed(sess.getUserId(), sessionId, sess.getScenario().getTitle()));
            }
            if (!giveUp) throw ex;
        }
    }

    private static boolean isStale(Evaluation e) {
        return e.getStatus().isActive() && e.getUpdatedAt().isBefore(Instant.now().minus(STALE_AFTER));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
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