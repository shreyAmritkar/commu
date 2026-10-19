package com.engish.interaction;

import com.engish.auth.AuthUser;
import com.engish.session.SessionDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{id}/turns")
public class TurnController {
    private final ConversationService conversation;

    public TurnController(ConversationService conversation) {
        this.conversation = conversation;
    }

    @PostMapping
    public SessionDto submit(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody TurnRequest request) {
        return conversation.takeTurn(id, user.id(), request.message());
    }
}