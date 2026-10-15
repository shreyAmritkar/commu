package com.engish.evaluation;

import com.engish.feedback.AlternativeReplyDto;
import com.engish.session.SessionDto;

import java.util.List;

public record ResultsDto(SessionDto session, EvaluationDto evaluation, List<AlternativeReplyDto> alternativeReplies) {
}
