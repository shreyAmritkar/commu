package com.engish.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String email;
    private String passwordHash;
    private String displayName;
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
}