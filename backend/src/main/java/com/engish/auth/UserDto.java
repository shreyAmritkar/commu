package com.engish.auth;

import java.util.UUID;

public record UserDto(UUID id, String email, String displayName, Role role) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole());
    }
}