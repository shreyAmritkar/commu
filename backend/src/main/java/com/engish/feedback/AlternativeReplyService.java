package com.engish.feedback;

import com.engish.ai.AiService;
import com.engish.common.ApiException;
import com.engish.common.QuotaService;
import com.engish.interaction.SessionTurn;
import com.engish.interaction.Speaker;
import com.engish.interaction.TurnRepository;
import com.engish.session.Session;
import com.engish.session.SessionService;
import com.engish.session.SessionStatus;
import com.engish.style.StyleProfile;
import com.engish.style.StyleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlternativeReplyService {
    private final SessionService sessions;
    private final TurnRepository turns;
    private final StyleRepository styles;
    private final AlternativeReplyRepository repo;
    private final AiService ai;
    private final ObjectMapper mapper;
    private final QuotaService quota;

    public AlternativeReplyService(SessionService sessions, TurnRepository turns, StyleRepository styles,
                                   AlternativeReplyRepository repo, AiService ai, ObjectMapper mapper, QuotaService quota) {
        this.sessions = sessions;
        this.turns = turns;
        this.styles = styles;
        this.repo = repo;
        this.ai = ai;
        this.mapper = mapper;
        this.quota = quota;
    }

    /** Generates (or returns the saved) alternative for one user turn in the given style, plus a comparison. */
    public AlternativeReplyDto generate(UUID sessionId,UUID userId, AlternativeReplyRequest req) {
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

        var existing = repo.findBySessionIdAndTurnIdAndStyleId(sessionId, turn.getId(), style.getId());
        if (existing.isPresent()) return toDto(existing.get(), style);
        quota.checkAlternative(userId);

        List<SessionTurn> all = turns.findBySessionIdOrderBySeqAsc(sessionId);
        List<SessionTurn> before = all.stream().filter(t -> t.getSeq() < turn.getSeq()).toList();
        before = before.subList(Math.max(0, before.size() - 6), before.size());
        String actualReaction = all.stream().filter(t -> t.getSeq() == turn.getSeq() + 1).map(SessionTurn::getMessage).findFirst().orElse(null);

        String alternative = ai.generateAlternative(s.getScenario(), s.getCharacter(), style, before, turn.getMessage());
        Comparison comparison = ai.compare(s.getScenario(), s.getCharacter(), style, before, turn.getMessage(), alternative, actualReaction);

        AlternativeReply saved = new AlternativeReply();
        saved.setSessionId(sessionId);
        saved.setTurnId(turn.getId());
        saved.setStyleId(style.getId());
        saved.setOriginalMessage(turn.getMessage());
        saved.setAlternativeMessage(alternative);
        saved.setComparisonJson(write(comparison));
        return toDto(repo.save(saved), style);
    }

    public List<AlternativeReplyDto> listFor(UUID sessionId) {
        List<AlternativeReply> list = repo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (list.isEmpty()) return List.of();
        Map<Long, StyleProfile> byId = styles.findAllById(list.stream().map(AlternativeReply::getStyleId).distinct().toList()).stream()
                .collect(Collectors.toMap(StyleProfile::getId, Function.identity()));
        return list.stream().map(a -> toDto(a, byId.get(a.getStyleId()))).toList();
    }

    private AlternativeReplyDto toDto(AlternativeReply a, StyleProfile style) {
        return new AlternativeReplyDto(a.getId(), a.getTurnId(), a.getStyleId(), style.getName(), style.getInspiredBy(),
                a.getOriginalMessage(), a.getAlternativeMessage(), read(a.getComparisonJson()), a.getCreatedAt());
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
