package com.engish.session;

import com.engish.character.AiCharacter;
import com.engish.interaction.GoalProgress;
import com.engish.interaction.Mood;
import com.engish.scenario.Scenario;
import com.engish.style.StyleProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter @Setter @NoArgsConstructor
public class Session {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "scenario_id")
    private Scenario scenario;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "character_id")
    private AiCharacter character;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "style_id")
    private StyleProfile style;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    private Mood mood;
    private String sceneState;
    @Enumerated(EnumType.STRING)
    private GoalProgress goalProgress = GoalProgress.NONE;
    private int userTurnCount;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant completedAt;
}
