package com.engish.feedback;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alternative_replies")
@Getter @Setter @NoArgsConstructor
public class AlternativeReply {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID sessionId;
    private Long turnId;
    private Long styleId;
    private String originalMessage;
    private String alternativeMessage;
    private String comparisonJson;
    private Instant createdAt = Instant.now();
}
