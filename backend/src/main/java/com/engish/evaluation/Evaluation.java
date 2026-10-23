package com.engish.evaluation;

import com.engish.common.JobStatus;
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
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;
    private int attempts;
    private String errorMessage;
    private Integer overallScore;
    private String outcome;
    private String summary;
    private String resultJson;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}