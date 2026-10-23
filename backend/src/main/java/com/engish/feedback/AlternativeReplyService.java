package com.engish.feedback;

import com.engish.ai.AiService;
import com.engish.common.ApiException;
import com.engish.common.JobStatus;
import com.engish.common.QuotaService;
import com.engish.interaction.SessionTurn;
import com.engish.interaction.Speaker;
import com.engish.interaction.TurnRepository;
import com.engish.session.Session;
import com.engish.session.SessionRepository;
import com.engish.session.SessionService;
import com.engish.session.SessionStatus;
import com.engish.style.StyleProfile;
import com.engish.style.StyleRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlternativeReplyService {
    private static final Logger log = LoggerFactory.getLogger(AlternativeReplyService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final SessionService sessions;
    private final SessionRepository sessionRepo;
    private final TurnRepository turns;
    private final StyleRepository styles;
    private final AlternativeReplyRepository repo;
    private final AiService ai;
    private final ObjectMapper mapper;
    private final QuotaService quota;
    private final TransactionTemplate tx;
    private final JobRequestScheduler jobs;

    public AlternativeReplyService(SessionService sessions, SessionRepository sessionRepo, TurnRepository turns,
                                   StyleRepository styles, AlternativeReplyRepository repo, AiService ai, ObjectMapper mapper,
                                   QuotaService quota, TransactionTemplate tx, JobRequestScheduler jobs) {
        this.sessions = sessions;
        this.sessionRepo = sessionRepo;
        this.turns = turns;
        this.styles = styles;
        this.repo = repo;
        this.ai = ai;
        this.mapper = mapper;
        this.quota = quota;
        this.tx = tx;
        this.jobs = jobs;
    }

    /** Returns the saved alternative (any status) or starts a new background job for it. */
    public AlternativeReplyDto request(UUID sessionId, UUID userId, AlternativeReplyRequest req) {
        Session s = sessions.getOwned(sessionId, userId);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_COMPLETED", "Finish the conversation and view your feedback first.");
        }
        SessionTurn turn = turns.findByIdAndSessionId(req.turnId(), sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TURN_NOT_FOUND", "Turn not found in this session."));
        if (turn.getSpeaker() != Speaker.USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_USER_TURN", "Alternatives can only be generated for your own messages.");
        }
        StyleProfile style = req.styleId() == null ? s.getStyle() : styles.findById(req.styleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STYLE_NOT_FOUND", "Communication style not found."));

        AlternativeReply existing = repo.findBySessionIdAndTurnIdAndStyleId(sessionId, turn.getId(), style.getId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == JobStatus.FAILED || isStale(existing)) restart(existing.getId());
            return toDto(repo.findById(existing.getId()).orElseThrow(), style);
        }

        quota.checkAlternative(userId);   // only for genuinely new work, so viewing a saved result is free
        AlternativeReply created;
        try {
            created = tx.execute(st -> {
                AlternativeReply a = new AlternativeReply();
                a.setSessionId(sessionId);
                a.setTurnId(turn.getId());
                a.setStyleId(style.getId());
                a.setOriginalMessage(turn.getMessage());
                a.setStatus(JobStatus.PENDING);
                return repo.save(a);
            });
        } catch (DataIntegrityViolationException raceLost) {
            AlternativeReply winner = repo.findBySessionIdAndTurnIdAndStyleId(sessionId, turn.getId(), style.getId()).orElseThrow();
            return toDto(winner, style);
        }
        jobs.enqueue(new AlternativeJobRequest(created.getId()));
        return toDto(created, style);
    }

    public List<AlternativeReplyDto> listFor(UUID sessionId) {
        List<AlternativeReply> list = repo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (list.isEmpty()) return List.of();
        Map<Long, StyleProfile> byId = styles.findAllById(list.stream().map(AlternativeReply::getStyleId).distinct().toList()).stream()
                .collect(Collectors.toMap(StyleProfile::getId, Function.identity()));
        return list.stream().map(a -> toDto(a, byId.get(a.getStyleId()))).toList();
    }

    /** Called by the JobRunr handler. Throwing makes JobRunr retry with backoff. */
    public void runJob(Long alternativeId) {
        if (repo.claim(alternativeId, List.of(JobStatus.PENDING, JobStatus.RUNNING), JobStatus.RUNNING, Instant.now()) == 0) return;

        int attempts = repo.findById(alternativeId).map(AlternativeReply::getAttempts).orElse(MAX_ATTEMPTS);
        try {
            AlternativeReply row = repo.findById(alternativeId).orElseThrow();
            Session s = sessionRepo.findById(row.getSessionId()).orElseThrow();
            SessionTurn turn = turns.findById(row.getTurnId()).orElseThrow();
            StyleProfile style = styles.findById(row.getStyleId()).orElseThrow();

            List<SessionTurn> all = turns.findBySessionIdOrderBySeqAsc(row.getSessionId());
            List<SessionTurn> earlier = all.stream().filter(t -> t.getSeq() < turn.getSeq()).toList();
            List<SessionTurn> before = earlier.subList(Math.max(0, earlier.size() - 6), earlier.size());
            String actualReaction = all.stream().filter(t -> t.getSeq() == turn.getSeq() + 1)
                    .map(SessionTurn::getMessage).findFirst().orElse(null);

            String alternative = ai.generateAlternative(s.getScenario(), s.getCharacter(), style, before, turn.getMessage());
            Comparison comparison = ai.compare(s.getScenario(), s.getCharacter(), style, before, turn.getMessage(), alternative, actualReaction);
            String json = write(comparison);

            tx.executeWithoutResult(st -> repo.findById(alternativeId).ifPresent(a -> {
                if (a.getStatus() == JobStatus.DONE) return;
                a.setAlternativeMessage(alternative);
                a.setComparisonJson(json);
                a.setStatus(JobStatus.DONE);
                a.setErrorMessage(null);
                a.setUpdatedAt(Instant.now());
                repo.save(a);
            }));
        } catch (RuntimeException ex) {
            boolean giveUp = attempts >= MAX_ATTEMPTS;
            String message = ex instanceof ApiException api ? api.getMessage() : "Something went wrong while writing the alternative.";
            tx.executeWithoutResult(st -> repo.findById(alternativeId).ifPresent(a -> {
                if (a.getStatus() == JobStatus.DONE) return;
                a.setStatus(giveUp ? JobStatus.FAILED : JobStatus.PENDING);
                a.setErrorMessage(message.length() <= 300 ? message : message.substring(0, 300));
                a.setUpdatedAt(Instant.now());
                repo.save(a);
            }));
            log.warn("Alternative {} failed on attempt {} ({}), {}", alternativeId, attempts,
                    ex.getClass().getSimpleName(), giveUp ? "giving up" : "will retry");
            if (!giveUp) throw ex;
        }
    }

    private void restart(Long id) {
        tx.executeWithoutResult(st -> repo.findById(id).ifPresent(a -> {
            a.setStatus(JobStatus.PENDING);
            a.setAttempts(0);
            a.setErrorMessage(null);
            a.setUpdatedAt(Instant.now());
            repo.save(a);
        }));
        jobs.enqueue(new AlternativeJobRequest(id));
    }

    private static boolean isStale(AlternativeReply a) {
        return a.getStatus().isActive() && a.getUpdatedAt().isBefore(Instant.now().minus(STALE_AFTER));
    }

    private AlternativeReplyDto toDto(AlternativeReply a, StyleProfile style) {
        Comparison comparison = a.getStatus() == JobStatus.DONE && a.getComparisonJson() != null ? read(a.getComparisonJson()) : null;
        return new AlternativeReplyDto(a.getId(), a.getTurnId(), a.getStyleId(), style.getName(), style.getInspiredBy(), a.getStatus(),
                a.getOriginalMessage(), a.getAlternativeMessage(), comparison, a.getErrorMessage(), a.getCreatedAt());
    }

    private String write(Comparison c) {
        try {
            return mapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Comparison read(String json) {
        try {
            return mapper.readValue(json, Comparison.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}