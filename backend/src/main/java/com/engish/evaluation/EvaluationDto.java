package com.engish.evaluation;

import com.engish.common.JobStatus;

import java.time.Instant;

public record EvaluationDto(Long id, JobStatus status, Integer overallScore, String outcome, String summary,
                            EvaluationResult details, String errorMessage, Instant createdAt) {
}