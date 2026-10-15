package com.engish.interaction;

/** Validated reaction produced from the LLM's structured JSON. */
public record CharacterReaction(String characterMessage, String sceneState, Mood mood,
                                GoalProgress goalProgress, NextAction nextAction) {
}
