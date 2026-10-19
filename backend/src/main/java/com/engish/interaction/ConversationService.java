package com.engish.interaction;

import com.engish.ai.AiService;
import com.engish.common.ApiException;
import com.engish.session.Session;
import com.engish.session.SessionDto;
import com.engish.session.SessionRepository;
import com.engish.session.SessionService;
import com.engish.session.SessionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Runs one conversational turn. The LLM call happens outside any DB transaction, and nothing is saved
 * unless the reaction validates, so a failed AI call never leaves a half-written turn.
 * The backend (not the model) decides when the conversation is finished.
 */
@Service
public class ConversationService {
    private static final int MIN_TURNS_BEFORE_WRAP_UP = 3;

    private final SessionService sessions;
    private final SessionRepository sessionRepo;
    private final TurnRepository turns;
    private final AiService ai;
    private final TransactionTemplate tx;

    public ConversationService(SessionService sessions, SessionRepository sessionRepo, TurnRepository turns,
                               AiService ai, TransactionTemplate tx) {
        this.sessions = sessions;
        this.sessionRepo = sessionRepo;
        this.turns = turns;
        this.ai = ai;
        this.tx = tx;
    }

    public SessionDto takeTurn(UUID id, UUID userId, String rawMessage) {
        Session s = sessions.getOwned(id, userId);
        if (s.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_ACTIVE", "This conversation has already ended.");
        }
        final String text = rawMessage.strip();
        final int n = s.getUserTurnCount() + 1;
        final int max = s.getScenario().getMaxTurns();
        final int startingCount = s.getUserTurnCount();

        List<SessionTurn> recent = new ArrayList<>(turns.findTop10BySessionIdOrderBySeqDesc(id));
        Collections.reverse(recent);

        CharacterReaction reaction = ai.generateReaction(s.getScenario(), s.getCharacter(), s, recent, text, max - n);

        boolean limitReached = n >= max;
        boolean naturalEnd = reaction.nextAction() == NextAction.WRAP_UP && n >= Math.min(MIN_TURNS_BEFORE_WRAP_UP, max);

        tx.executeWithoutResult(status -> {
            Session fresh = sessionRepo.findById(id).orElseThrow();
            if (fresh.getUserTurnCount() != startingCount || fresh.getStatus() != SessionStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "TURN_CONFLICT", "The conversation changed while the reply was being written. Refresh and try again.");
            }
            turns.save(SessionTurn.user(id, 2 * n - 1, text));
            turns.save(SessionTurn.character(id, 2 * n, reaction.characterMessage(), reaction.sceneState(),
                    reaction.mood(), reaction.goalProgress()));
            fresh.setUserTurnCount(n);
            fresh.setMood(reaction.mood());
            fresh.setSceneState(reaction.sceneState());
            fresh.setGoalProgress(reaction.goalProgress());
            if (limitReached || naturalEnd) {
                fresh.setStatus(SessionStatus.FINISHED);
            }
            fresh.setUpdatedAt(Instant.now());
            sessionRepo.save(fresh);
        });
        return sessions.get(id, userId);
    }
}
