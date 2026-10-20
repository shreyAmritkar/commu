package com.engish.auth;

import com.engish.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Writes the standard JSON error from inside servlet filters, where controller advice does not run. */
final class ErrorWriter {
    private ErrorWriter() {}

    static void write(ObjectMapper mapper, HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(mapper.writeValueAsString(ErrorResponse.of(code, message)));
    }
}