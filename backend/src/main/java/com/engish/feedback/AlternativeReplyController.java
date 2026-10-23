package com.engish.feedback;

import com.engish.auth.AuthUser;
import com.engish.common.JobStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    /** 200 if the alternative is ready, 202 if it is being generated in the background. */
    @PostMapping
    public ResponseEntity<AlternativeReplyDto> create(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user,
                                                      @Valid @RequestBody AlternativeReplyRequest request) {
        AlternativeReplyDto dto = service.request(id, user.id(), request);
        return ResponseEntity.status(dto.status() == JobStatus.DONE ? 200 : 202).body(dto);
    }
}