package com.engish.session;

import com.engish.ai.AiService;
import com.engish.ai.Opening;
import com.engish.character.AiCharacter;
import com.engish.character.CharacterDto;
import com.engish.character.CharacterRepository;
import com.engish.common.ApiException;
import com.engish.evaluation.Evaluation;
import com.engish.evaluation.EvaluationRepository;
import com.engish.interaction.GoalProgress;
import com.engish.interaction.SessionTurn;
import com.engish.interaction.TurnDto;
import com.engish.interaction.TurnRepository;
import com.engish.scenario.Scenario;
import com.engish.scenario.ScenarioDto;
import com.engish.scenario.ScenarioRepository;
import com.engish.style.StyleDto;
import com.engish.style.StyleProfile;
import com.engish.style.StyleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionService {
    private final SessionRepository sessions;
    private final ScenarioRepository scenarios;
    private final StyleRepository styles;
    private final CharacterRepository characters;
    private final TurnRepository turns;
    private final EvaluationRepository evaluations;
    private final AiService ai;
    private final TransactionTemplate tx;

    public SessionService(SessionRepository sessions, ScenarioRepository scenarios, StyleRepository styles,
                          CharacterRepository characters, TurnRepository turns, EvaluationRepository evaluations,
                          AiService ai, TransactionTemplate tx) {
        this.sessions = sessions;
        this.scenarios = scenarios;
        this.styles = styles;
        this.characters = characters;
        this.turns = turns;
        this.evaluations = evaluations;
        this.ai = ai;
        this.tx = tx;
    }

    /** Generates the opening line first; the session is only stored if the AI call succeeds. */
    public SessionDto create(CreateSessionRequest req, String guestId) {
        Scenario scenario = scenarios.findById(req.scenarioId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND", "Scenario not found."));
        StyleProfile style = styles.findById(req.styleId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STYLE_NOT_FOUND", "Communication style not found."));
        AiCharacter character = characters.findByScenarioId(scenario.getId()).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "NO_CHARACTER", "This scenario has no character configured."));

        Opening opening = ai.generateOpening(scenario, character);

        UUID id = tx.execute(status -> {
            Session s = new Session();
            s.setGuestId(guestId);
            s.setScenario(scenario);
            s.setCharacter(character);
            s.setStyle(style);
            s.setStatus(SessionStatus.ACTIVE);
            s.setMood(opening.mood());
            s.setSceneState(opening.sceneState());
            s.setGoalProgress(GoalProgress.NONE);
            Session saved = sessions.save(s);
            turns.save(SessionTurn.character(saved.getId(), 0, opening.characterMessage(), opening.sceneState(),
                    opening.mood(), GoalProgress.NONE));
            return saved.getId();
        });
        return get(id, guestId);
    }

    public SessionDto get(UUID id, String guestId) {
        return toDto(getOwned(id, guestId));
    }

    /** Sessions are scoped to the guest that created them; others get a 404 rather than a 403. */
    public Session getOwned(UUID id, String guestId) {
        return sessions.findById(id)
                .filter(s -> s.getGuestId().equals(guestId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found."));
    }

    public List<SessionSummaryDto> history(String guestId) {
        List<Session> list = sessions.findTop50ByGuestIdOrderByCreatedAtDesc(guestId);
        if (list.isEmpty()) return List.of();
        Map<UUID, Integer> scores = evaluations.findBySessionIdIn(list.stream().map(Session::getId).toList()).stream()
                .collect(Collectors.toMap(Evaluation::getSessionId, Evaluation::getOverallScore));
        return list.stream().map(s -> new SessionSummaryDto(s.getId(), s.getScenario().getTitle(), s.getScenario().getCategory(),
                s.getCharacter().getName(), s.getStyle().getName(), s.getStatus(), s.getUserTurnCount(),
                s.getScenario().getMaxTurns(), scores.get(s.getId()), s.getCreatedAt())).toList();
    }

    public SessionDto toDto(Session s) {
        List<TurnDto> turnDtos = turns.findBySessionIdOrderBySeqAsc(s.getId()).stream().map(TurnDto::from).toList();
        return new SessionDto(s.getId(), s.getStatus(), ScenarioDto.from(s.getScenario(), s.getCharacter()),
                CharacterDto.from(s.getCharacter()), StyleDto.from(s.getStyle()), s.getUserTurnCount(),
                s.getScenario().getMaxTurns(), s.getMood().name(), s.getSceneState(), s.getGoalProgress().name(),
                s.getCreatedAt(), s.getCompletedAt(), turnDtos);
    }
}
