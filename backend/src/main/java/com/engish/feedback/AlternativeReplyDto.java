package com.engish.feedback;

import java.time.Instant;

public record AlternativeReplyDto(Long id, Long turnId, Long styleId, String styleName, String inspiredBy,
                                  String originalMessage, String alternativeMessage, Comparison comparison, Instant createdAt) {
}
