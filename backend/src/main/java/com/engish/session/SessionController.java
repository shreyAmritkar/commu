package com.engish.session;

import com.engish.common.Guest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public SessionDto create(@RequestHeader(value = Guest.HEADER, required = false) String guestId,
                             @Valid @RequestBody CreateSessionRequest request) {
        return service.create(request, Guest.require(guestId));
    }

    @GetMapping("/history")
    public List<SessionSummaryDto> history(@RequestHeader(value = Guest.HEADER, required = false) String guestId) {
        return service.history(Guest.require(guestId));
    }

    @GetMapping("/{id}")
    public SessionDto get(@PathVariable UUID id,
                          @RequestHeader(value = Guest.HEADER, required = false) String guestId) {
        return service.get(id, Guest.require(guestId));
    }
}
