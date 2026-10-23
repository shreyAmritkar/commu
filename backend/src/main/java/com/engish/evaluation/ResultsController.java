package com.engish.evaluation;

import com.engish.auth.AuthUser;
import com.engish.common.JobStatus;
import org.springframework.http.ResponseEntity;
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

    /** 200 when the feedback is ready, 202 while it is still being prepared. */
    @PostMapping("/complete")
    public ResponseEntity<ResultsDto> complete(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        ResultsDto result = service.complete(id, user.id());
        return ResponseEntity.status(result.evaluation().status() == JobStatus.DONE ? 200 : 202).body(result);
    }

    @GetMapping("/results")
    public ResultsDto results(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.results(id, user.id());
    }
}