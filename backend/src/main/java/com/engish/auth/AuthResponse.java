package com.engish.auth;

public record AuthResponse(String token, UserDto user) {
}