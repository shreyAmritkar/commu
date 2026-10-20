package com.engish.auth;

import com.engish.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;   // compared against when the email is unknown, so timing does not reveal it
    
    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.email().strip().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with that email already exists.");
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(req.displayName().strip());
        u.setRole(Role.USER);   // registration can never create an admin
        User saved = users.save(u);
        return new AuthResponse(jwt.generate(saved), UserDto.from(saved));
    }

    public AuthResponse login(LoginRequest req) {
        Optional<User> found = users.findByEmailIgnoreCase(req.email().strip());
        boolean passwordOk = encoder.matches(req.password(), found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk || !found.get().isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
        }
        return new AuthResponse(jwt.generate(found.get()), UserDto.from(found.get()));
    }

    public UserDto me(UUID userId) {
        return users.findById(userId).map(UserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in to continue."));
    }
}