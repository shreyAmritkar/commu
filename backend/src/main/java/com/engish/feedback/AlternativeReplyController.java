package com.engish.feedback;

import com.engish.common.Guest;
import jakarta.validation.Valid;
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
    public AlternativeReplyDto create(@PathVariable UUID id,
                                      @RequestHeader(value = Guest.HEADER, required = false) String guestId,
                                      @Valid @RequestBody AlternativeReplyRequest request) {
        return service.generate(id, Guest.require(guestId), request);
    }
}
