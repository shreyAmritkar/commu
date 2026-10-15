package com.engish.interaction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_turns")
@Getter @Setter @NoArgsConstructor
public class SessionTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID sessionId;
    private int seq;                      // 0 = opening line, user turn n = 2n-1, character reply n = 2n
    @Enumerated(EnumType.STRING)
    private Speaker speaker;
    private String message;
    private String sceneState;
    @Enumerated(EnumType.STRING)
    private Mood mood;
    @Enumerated(EnumType.STRING)
    private GoalProgress goalProgress;
    private Instant createdAt = Instant.now();

    public static SessionTurn user(UUID sessionId, int seq, String message) {
        SessionTurn t = new SessionTurn();
        t.sessionId = sessionId; t.seq = seq; t.speaker = Speaker.USER; t.message = message;
        return t;
    }

    public static SessionTurn character(UUID sessionId, int seq, String message, String sceneState, Mood mood, GoalProgress progress) {
        SessionTurn t = new SessionTurn();
        t.sessionId = sessionId; t.seq = seq; t.speaker = Speaker.CHARACTER; t.message = message;
        t.sceneState = sceneState; t.mood = mood; t.goalProgress = progress;
        return t;
    }
}
