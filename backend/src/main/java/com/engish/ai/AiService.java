package com.engish.ai;

import com.engish.character.AiCharacter;
import com.engish.evaluation.EvaluationResult;
import com.engish.feedback.Comparison;
import com.engish.interaction.CharacterReaction;
import com.engish.interaction.GoalProgress;
import com.engish.interaction.Mood;
import com.engish.interaction.NextAction;
import com.engish.interaction.SessionTurn;
import com.engish.scenario.Scenario;
import com.engish.session.Session;
import com.engish.style.StyleProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds prompts, calls the LLM, parses the JSON and validates it. Never touches the database. */
@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final List<String> DIMENSIONS = List.of("clarity", "tone", "empathy", "assertiveness", "strategy", "effectiveness");

    record RawOpening(String characterMessage, String sceneState, String mood) {}
    record RawReaction(String characterMessage, String sceneState, String mood, String goalProgress, String nextAction) {}
    record RawAlternative(String alternativeMessage) {}

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public AiService(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Opening generateOpening(Scenario s, AiCharacter c) {
        return callJson(PromptTemplates.opening(s, c), RawOpening.class, r -> new Opening(
                text(r.characterMessage(), "characterMessage", 1500), text(r.sceneState(), "sceneState", 300), Mood.parse(r.mood())));
    }

    public CharacterReaction generateReaction(Scenario s, AiCharacter c, Session session, List<SessionTurn> recent,
                                              String userMessage, int turnsRemaining) {
        return callJson(PromptTemplates.reaction(s, c, session, recent, userMessage, turnsRemaining), RawReaction.class,
                r -> new CharacterReaction(text(r.characterMessage(), "characterMessage", 1500), text(r.sceneState(), "sceneState", 300),
                        Mood.parse(r.mood()), GoalProgress.parse(r.goalProgress()), NextAction.parse(r.nextAction())));
    }

    public EvaluationResult evaluate(Scenario s, AiCharacter c, Session session, List<SessionTurn> all) {
        return callJson(PromptTemplates.evaluation(s, c, session, all), EvaluationResult.class, AiService::validateEvaluation);
    }

    public String generateAlternative(Scenario s, AiCharacter c, StyleProfile style, List<SessionTurn> before, String original) {
        return callJson(PromptTemplates.alternative(s, c, style, before, original), RawAlternative.class,
                r -> text(r.alternativeMessage(), "alternativeMessage", 1500));
    }

    public Comparison compare(Scenario s, AiCharacter c, StyleProfile style, List<SessionTurn> before,
                              String original, String alternative, String actualReaction) {
        return callJson(PromptTemplates.comparison(s, c, style, before, original, alternative, actualReaction), Comparison.class,
                r -> new Comparison(text(r.wording(), "wording", 800), text(r.tone(), "tone", 800), text(r.strategy(), "strategy", 800),
                        text(r.likelyEffect(), "likelyEffect", 800), text(r.keyTakeaway(), "keyTakeaway", 800)));
    }

    // ------------------------------------------------------------------ internals
    /** Calls the model in JSON mode, parses and validates; retries once on malformed output. */
    private <T, R> R callJson(PromptTemplates.Prompt prompt, Class<T> rawType, Function<T, R> convert) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = llm.complete(prompt.system(), prompt.user(), true);
            try {
                return convert.apply(mapper.readValue(extractJson(raw), rawType));
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.warn("Malformed model output (attempt {}): {}", attempt, e.getMessage());
            }
        }
        throw AiException.badResponse();
    }

    static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("no JSON object found");
        return raw.substring(start, end + 1);
    }

    private static String text(String v, String field, int max) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " is blank");
        String t = v.strip();
        if (t.length() > 1 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int score(int v) {
        return Math.max(1, Math.min(10, v));
    }

    private static List<String> list(List<String> v, String field) {
        if (v == null) throw new IllegalArgumentException(field + " missing");
        List<String> cleaned = v.stream().filter(x -> x != null && !x.isBlank()).map(String::strip).limit(5).toList();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(field + " empty");
        return cleaned;
    }

    private static EvaluationResult validateEvaluation(EvaluationResult r) {
        if (r.dimensions() == null) throw new IllegalArgumentException("dimensions missing");
        Map<String, EvaluationResult.Dimension> normalized = new LinkedHashMap<>();
        r.dimensions().forEach((k, v) -> { if (k != null && v != null) normalized.put(k.strip().toLowerCase(), v); });
        Map<String, EvaluationResult.Dimension> dims = new LinkedHashMap<>();
        for (String d : DIMENSIONS) {
            EvaluationResult.Dimension dim = normalized.get(d);
            if (dim == null) throw new IllegalArgumentException("dimension missing: " + d);
            dims.put(d, new EvaluationResult.Dimension(score(dim.score()), text(dim.comment(), d + ".comment", 700)));
        }
        String outcome = r.outcome() == null ? "" : r.outcome().strip().toUpperCase().replace(' ', '_');
        if (!List.of("ACHIEVED", "PARTIAL", "NOT_ACHIEVED").contains(outcome)) throw new IllegalArgumentException("bad outcome");
        return new EvaluationResult(score(r.overallScore()), outcome, text(r.summary(), "summary", 1200), dims,
                list(r.strengths(), "strengths"), list(r.improvements(), "improvements"), list(r.suggestions(), "suggestions"));
    }
}
