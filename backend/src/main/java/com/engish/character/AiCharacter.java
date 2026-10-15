package com.engish.character;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "characters")
@Getter @Setter @NoArgsConstructor
public class AiCharacter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String roleTitle;
    private String personality;
    private String speakingStyle;
    private String initialMood;
    private Instant createdAt;
}
