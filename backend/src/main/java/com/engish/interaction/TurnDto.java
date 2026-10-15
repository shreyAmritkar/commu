package com.engish.interaction;

import java.time.Instant;

public record TurnDto(Long id, int seq, Speaker speaker, String message, String mood, Instant createdAt) {
    public static TurnDto from(SessionTurn t) {
        return new TurnDto(t.getId(), t.getSeq(), t.getSpeaker(), t.getMessage(),
                t.getMood() == null ? null : t.getMood().name(), t.getCreatedAt());
    }
}
