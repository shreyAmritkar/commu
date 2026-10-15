package com.engish.session;

import java.time.Instant;
import java.util.UUID;

public record SessionSummaryDto(UUID id, String scenarioTitle, String category, String characterName, String styleName,
                                SessionStatus status, int userTurnCount, int maxTurns, Integer overallScore, Instant createdAt) {
}
