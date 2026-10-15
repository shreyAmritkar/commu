package com.engish.interaction;

import com.engish.common.Guest;
import com.engish.session.SessionDto;
import jakarta.validation.Valid;
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
    public SessionDto submit(@PathVariable UUID id,
                             @RequestHeader(value = Guest.HEADER, required = false) String guestId,
                             @Valid @RequestBody TurnRequest request) {
        return conversation.takeTurn(id, Guest.require(guestId), request.message());
    }
}
