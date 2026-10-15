package com.engish.evaluation;

import java.util.List;
import java.util.Map;

/** Structure the LLM must return for an evaluation; stored as JSON in evaluations.result_json. */
public record EvaluationResult(int overallScore, String outcome, String summary, Map<String, Dimension> dimensions,
                               List<String> strengths, List<String> improvements, List<String> suggestions) {
    public record Dimension(int score, String comment) {}
}
