package com.engish.feedback;

import com.engish.common.JobStatus;

import java.time.Instant;

public record AlternativeReplyDto(Long id, Long turnId, Long styleId, String styleName, String inspiredBy, JobStatus status,
                                  String originalMessage, String alternativeMessage, Comparison comparison,
                                  String errorMessage, Instant createdAt) {
}