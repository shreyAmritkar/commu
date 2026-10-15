package com.engish.common;

import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/** Guest sessions: the browser generates an id and sends it in the X-Guest-Id header. */
public final class Guest {
    public static final String HEADER = "X-Guest-Id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private Guest() {}

    public static String require(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GUEST_ID", "Missing or invalid " + HEADER + " header.");
        }
        return value;
    }
}
