package com.engish.character;

public record CharacterDto(Long id, String name, String roleTitle, String personality, String speakingStyle, String initialMood) {
    public static CharacterDto from(AiCharacter c) {
        return new CharacterDto(c.getId(), c.getName(), c.getRoleTitle(), c.getPersonality(), c.getSpeakingStyle(), c.getInitialMood());
    }
}
