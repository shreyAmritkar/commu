package com.engish.auth;

import java.util.UUID;

public record AuthUser(UUID id, Role role) {
}