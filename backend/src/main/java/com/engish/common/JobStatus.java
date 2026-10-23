package com.engish.common;

public enum JobStatus {
    PENDING, RUNNING, DONE, FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}