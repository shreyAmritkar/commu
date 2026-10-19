package com.engish.evaluation;

import com.engish.auth.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{id}")
public class ResultsController {
    private final EvaluationService service;

    public ResultsController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping("/complete")
    public ResultsDto complete(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.complete(id, user.id());
    }

    @GetMapping("/results")
    public ResultsDto results(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.results(id, user.id());
    }
}