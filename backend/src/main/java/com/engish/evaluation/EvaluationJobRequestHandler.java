package com.engish.evaluation;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EvaluationJobRequestHandler implements JobRequestHandler<EvaluationJobRequest> {
    private final EvaluationService service;

    public EvaluationJobRequestHandler(EvaluationService service) {
        this.service = service;
    }

    // The service stops at its own attempt limit; JobRunr's retry count must not be lower than that limit.
    @Override
    @Job(name = "Evaluate session", retries = 5)
    public void run(EvaluationJobRequest request) {
        service.runJob(UUID.fromString(request.getSessionId()));
    }
}