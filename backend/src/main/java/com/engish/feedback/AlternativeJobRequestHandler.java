package com.engish.feedback;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

@Component
public class AlternativeJobRequestHandler implements JobRequestHandler<AlternativeJobRequest> {
    private final AlternativeReplyService service;

    public AlternativeJobRequestHandler(AlternativeReplyService service) {
        this.service = service;
    }

    @Override
    @Job(name = "Generate alternative reply", retries = 5)
    public void run(AlternativeJobRequest request) {
        service.runJob(request.getAlternativeId());
    }
}