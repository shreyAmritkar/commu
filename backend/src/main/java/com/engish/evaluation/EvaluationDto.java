package com.engish.evaluation;

import java.time.Instant;

public record EvaluationDto(Long id, int overallScore, String outcome, String summary, EvaluationResult details, Instant createdAt) {
}
