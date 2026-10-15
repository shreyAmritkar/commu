package com.engish.interaction;

public enum GoalProgress {
    NONE, PARTIAL, ACHIEVED;

    public static GoalProgress parse(String v) {
        if (v == null) throw new IllegalArgumentException("goalProgress is missing");
        return valueOf(v.strip().toUpperCase().replace(' ', '_'));
    }
}
