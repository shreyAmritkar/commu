package com.engish.scenario;

import com.engish.character.AiCharacter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scenarios")
@Getter @Setter @NoArgsConstructor
public class Scenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String slug;
    private String title;
    private String description;
    private String category;
    private String userRole;
    private String aiRole;
    private String emotionalContext;
    private String objective;
    private String successCriteria;   // newline-separated
    private String difficulty;
    private int maxTurns;
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "scenario_characters",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "character_id"))
    private List<AiCharacter> characters = new ArrayList<>();
}
