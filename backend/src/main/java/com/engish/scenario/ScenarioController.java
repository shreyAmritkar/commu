package com.engish.scenario;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final ScenarioService service;

    public ScenarioController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScenarioDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ScenarioDto get(@PathVariable Long id) {
        return service.get(id);
    }
}
