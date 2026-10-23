package com.engish.notification;

import com.engish.auth.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationHub hub;

    public NotificationController(NotificationService service, NotificationHub hub) {
        this.service = service;
        this.hub = hub;
    }

    @GetMapping
    public NotificationsDto list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.id());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        service.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser user) {
        service.markAllRead(user.id());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");               // tells Nginx not to buffer the stream
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return hub.open(user.id());
    }
}