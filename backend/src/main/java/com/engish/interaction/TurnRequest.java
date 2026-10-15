package com.engish.interaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TurnRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message must be 1000 characters or fewer")
        String message) {
}
