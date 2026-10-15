package com.engish.evaluation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluations")
@Getter @Setter @NoArgsConstructor
public class Evaluation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID sessionId;
    private int overallScore;
    private String outcome;
    private String summary;
    private String resultJson;
    private Instant createdAt = Instant.now();
}
