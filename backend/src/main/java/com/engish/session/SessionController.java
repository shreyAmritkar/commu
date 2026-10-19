package com.engish.session;

import com.engish.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateSessionRequest request) {
        return service.create(request, user.id());
    }

    @GetMapping("/history")
    public List<SessionSummaryDto> history(@AuthenticationPrincipal AuthUser user) {
        return service.history(user.id());
    }

    @GetMapping("/{id}")
    public SessionDto get(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.get(id, user.id());
    }
}