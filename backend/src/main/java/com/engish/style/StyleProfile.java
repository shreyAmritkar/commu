package com.engish.style;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "style_profiles")
@Getter @Setter @NoArgsConstructor
public class StyleProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String styleKey;
    private String name;
    private String description;
    private String inspiredBy;
    private int warmth;
    private int confidence;
    private int directness;
    private int humor;
    private int empathy;
    private int formality;
    private Instant createdAt;
}
