package com.engish.feedback;

import org.jobrunr.jobs.lambdas.JobRequest;

public class AlternativeJobRequest implements JobRequest {
    private long alternativeId;

    public AlternativeJobRequest() {}

    public AlternativeJobRequest(long alternativeId) {
        this.alternativeId = alternativeId;
    }

    public long getAlternativeId() {
        return alternativeId;
    }

    @Override
    public Class<AlternativeJobRequestHandler> getJobRequestHandler() {
        return AlternativeJobRequestHandler.class;
    }
}