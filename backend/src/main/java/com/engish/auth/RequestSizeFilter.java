package com.engish.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects oversized API bodies early. A 1000-character message is well under 8 KB. */
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final long MAX_BYTES = 8 * 1024;
    private final ObjectMapper mapper;

    public RequestSizeFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(req.getMethod()) && req.getRequestURI().startsWith("/api/")) {
            boolean chunked = req.getHeader("Transfer-Encoding") != null;   // unknown length: refuse, browsers send Content-Length
            if (chunked || req.getContentLengthLong() > MAX_BYTES) {
                ErrorWriter.write(mapper, res, 413, "PAYLOAD_TOO_LARGE", "That request is too large.");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}