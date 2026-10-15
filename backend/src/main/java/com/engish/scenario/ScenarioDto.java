package com.engish.scenario;

import com.engish.character.AiCharacter;
import com.engish.character.CharacterDto;

import java.util.Arrays;
import java.util.List;

public record ScenarioDto(Long id, String slug, String title, String description, String category,
                          String userRole, String aiRole, String emotionalContext, String objective,
                          List<String> successCriteria, String difficulty, int maxTurns, CharacterDto character) {

    public static ScenarioDto from(Scenario s, AiCharacter c) {
        List<String> criteria = Arrays.stream(s.getSuccessCriteria().split("\n")).map(String::strip).filter(x -> !x.isEmpty()).toList();
        return new ScenarioDto(s.getId(), s.getSlug(), s.getTitle(), s.getDescription(), s.getCategory(), s.getUserRole(),
                s.getAiRole(), s.getEmotionalContext(), s.getObjective(), criteria, s.getDifficulty(), s.getMaxTurns(),
                c == null ? null : CharacterDto.from(c));
    }
}
