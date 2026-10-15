package com.engish.feedback;

import jakarta.validation.constraints.NotNull;

/** styleId is optional; when omitted the session's selected style is used. */
public record AlternativeReplyRequest(@NotNull(message = "turnId is required") Long turnId, Long styleId) {
}
