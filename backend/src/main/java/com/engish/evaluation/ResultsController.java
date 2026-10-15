package com.engish.evaluation;

import com.engish.common.Guest;
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
    public ResultsDto complete(@PathVariable UUID id, @RequestHeader(value = Guest.HEADER, required = false) String guestId) {
        return service.complete(id, Guest.require(guestId));
    }

    @GetMapping("/results")
    public ResultsDto results(@PathVariable UUID id, @RequestHeader(value = Guest.HEADER, required = false) String guestId) {
        return service.results(id, Guest.require(guestId));
    }
}
