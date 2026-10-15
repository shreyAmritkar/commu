package com.engish.session;

import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull(message = "scenarioId is required") Long scenarioId,
        @NotNull(message = "styleId is required") Long styleId) {
}
