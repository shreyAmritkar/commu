package com.engish.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 254) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
        @NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be 80 characters or fewer") String displayName) {
}