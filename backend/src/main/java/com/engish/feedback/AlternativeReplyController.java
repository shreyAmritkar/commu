package com.engish.feedback;

import com.engish.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{id}/alternative-replies")
public class AlternativeReplyController {
    private final AlternativeReplyService service;

    public AlternativeReplyController(AlternativeReplyService service) {
        this.service = service;
    }

    @PostMapping
    public AlternativeReplyDto create(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody AlternativeReplyRequest request) {
        return service.generate(id, user.id(), request);
    }
}