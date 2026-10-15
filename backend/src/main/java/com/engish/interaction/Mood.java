package com.engish.interaction;

/** Ordered from coldest to warmest; the UI renders this as a temperature meter. */
public enum Mood {
    HOSTILE, DEFENSIVE, TENSE, NEUTRAL, RECEPTIVE, WARM;

    public static Mood parse(String v) {
        if (v == null) throw new IllegalArgumentException("mood is missing");
        return valueOf(v.strip().toUpperCase().replace(' ', '_'));
    }
}
