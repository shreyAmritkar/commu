package com.engish.auth;

import com.engish.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window limits: login/register per IP, and the expensive LLM-backed POSTs per user. */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int AUTH_LIMIT = 10;        // per IP per minute
    private static final int AI_LIMIT = 30;          // per user per minute
    private static final long WINDOW_MS = 60_000;

    private final Map<String, long[]> windows = new ConcurrentHashMap<>();   // key -> {windowStart, count}
    private final ObjectMapper mapper;

    public RateLimitFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(req.getMethod())) {
            String path = req.getRequestURI();
            String key = null;
            int limit = 0;
            if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
                key = "auth:" + req.getRemoteAddr();   // behind a proxy, use the forwarded address instead
                limit = AUTH_LIMIT;
            } else if (path.startsWith("/api/sessions")) {
                Authentication a = SecurityContextHolder.getContext().getAuthentication();
                key = "ai:" + (a != null && a.getPrincipal() instanceof AuthUser u ? u.id().toString() : req.getRemoteAddr());
                limit = AI_LIMIT;
            }
            if (key != null && !allow(key, limit)) {
                res.setStatus(429);
                res.setHeader("Retry-After", "60");
                res.setContentType("application/json");
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write(mapper.writeValueAsString(
                        ErrorResponse.of("RATE_LIMITED", "Too many requests. Please wait a minute and try again.")));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) windows.values().removeIf(w -> now - w[0] >= WINDOW_MS);   // keep memory bounded
        boolean[] allowed = {true};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= WINDOW_MS) return new long[]{now, 1};
            if (w[1] >= limit) { allowed[0] = false; return w; }
            w[1]++;
            return w;
        });
        return allowed[0];
    }
}