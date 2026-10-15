package com.engish.interaction;

/** The model only *suggests* an action. The backend decides whether the session actually ends. */
public enum NextAction {
    CONTINUE, WRAP_UP;

    public static NextAction parse(String v) {
        if (v == null) throw new IllegalArgumentException("nextAction is missing");
        return valueOf(v.strip().toUpperCase().replace(' ', '_'));
    }
}
