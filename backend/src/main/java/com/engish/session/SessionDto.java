package com.engish.session;

import com.engish.character.CharacterDto;
import com.engish.interaction.TurnDto;
import com.engish.scenario.ScenarioDto;
import com.engish.style.StyleDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDto(UUID id, SessionStatus status, ScenarioDto scenario, CharacterDto character, StyleDto style,
                         int userTurnCount, int maxTurns, String mood, String sceneState, String goalProgress,
                         Instant createdAt, Instant completedAt, List<TurnDto> turns) {
}
