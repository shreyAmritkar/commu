package com.engish.session;

import com.engish.common.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionSummaryDto(UUID id, String scenarioTitle, String category, String characterName, String styleName,
                                SessionStatus status, int userTurnCount, int maxTurns, Integer overallScore,
                                JobStatus feedbackStatus, Instant createdAt) {
}