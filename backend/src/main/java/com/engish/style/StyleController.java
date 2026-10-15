package com.engish.style;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/styles")
public class StyleController {
    private final StyleRepository repo;

    public StyleController(StyleRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<StyleDto> list() {
        return repo.findAllByOrderByIdAsc().stream().map(StyleDto::from).toList();
    }
}
