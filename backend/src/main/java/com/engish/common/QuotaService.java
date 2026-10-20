package com.engish.common;

import com.engish.feedback.AlternativeReplyRepository;
import com.engish.interaction.Speaker;
import com.engish.interaction.TurnRepository;
import com.engish.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Rolling 24-hour caps on the expensive actions, counted from the database. */
@Service
public class QuotaService {
    private static final int SESSIONS_PER_DAY = 20;
    private static final int TURNS_PER_DAY = 300;
    private static final int ALTERNATIVES_PER_DAY = 40;

    private final SessionRepository sessions;
    private final TurnRepository turns;
    private final AlternativeReplyRepository alternatives;

    public QuotaService(SessionRepository sessions, TurnRepository turns, AlternativeReplyRepository alternatives) {
        this.sessions = sessions;
        this.turns = turns;
        this.alternatives = alternatives;
    }

    public void checkSession(UUID userId) {
        if (sessions.countByUserIdAndCreatedAtAfter(userId, since()) >= SESSIONS_PER_DAY) throw exceeded("new conversations");
    }

    public void checkTurn(UUID userId) {
        if (turns.countUserTurnsSince(userId, Speaker.USER, since()) >= TURNS_PER_DAY) throw exceeded("messages");
    }

    public void checkAlternative(UUID userId) {
        if (alternatives.countSince(userId, since()) >= ALTERNATIVES_PER_DAY) throw exceeded("alternative replies");
    }

    private static Instant since() {
        return Instant.now().minus(24, ChronoUnit.HOURS);
    }

    private static ApiException exceeded(String what) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                "You have reached the daily limit for " + what + ". Please try again tomorrow.");
    }
}