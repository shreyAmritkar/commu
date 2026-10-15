package com.engish.scenario;

import com.engish.character.AiCharacter;
import com.engish.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScenarioService {
    private final ScenarioRepository repo;

    public ScenarioService(ScenarioRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ScenarioDto> list() {
        return repo.findAllByOrderByIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ScenarioDto get(Long id) {
        return repo.findById(id).map(this::toDto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND", "Scenario not found."));
    }

    private ScenarioDto toDto(Scenario s) {
        AiCharacter c = s.getCharacters().isEmpty() ? null : s.getCharacters().get(0);
        return ScenarioDto.from(s, c);
    }
}
