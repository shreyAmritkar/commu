package com.engish.evaluation;

import org.jobrunr.jobs.lambdas.JobRequest;

/** Only an id is stored in the job table, never transcript text. */
public class EvaluationJobRequest implements JobRequest {
    private String sessionId;

    public EvaluationJobRequest() {}   // required for JSON deserialisation

    public EvaluationJobRequest(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Class<EvaluationJobRequestHandler> getJobRequestHandler() {
        return EvaluationJobRequestHandler.class;
    }
}