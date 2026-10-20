package com.engish.auth;

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

/** Fixed one-minute windows. Login/register are limited per IP; the LLM-backed POSTs are limited per user, per endpoint. */
public class RateLimitFilter extends OncePerRequestFilter {
    private record Bucket(String name, int limit) {}

    private static final long WINDOW_MS = 60_000;

    private final Map<String, long[]> windows = new ConcurrentHashMap<>();   // key -> {windowStart, count}
    private final ObjectMapper mapper;

    public RateLimitFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static Bucket bucketFor(String path) {
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) return new Bucket("auth", 10);
        if (path.equals("/api/sessions")) return new Bucket("create", 6);
        if (path.startsWith("/api/sessions/")) {
            if (path.endsWith("/turns")) return new Bucket("turn", 20);
            if (path.endsWith("/complete")) return new Bucket("complete", 6);
            if (path.endsWith("/alternative-replies")) return new Bucket("alt", 10);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = "POST".equals(req.getMethod()) ? bucketFor(req.getRequestURI()) : null;
        if (bucket != null) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            String who = !bucket.name().equals("auth") && a != null && a.getPrincipal() instanceof AuthUser u
                    ? u.id().toString()
                    : req.getRemoteAddr();   // behind a proxy, enable server.forward-headers-strategy so this is the client IP
            long retryAfter = retryAfterSeconds(bucket.name() + ":" + who, bucket.limit());
            if (retryAfter > 0) {
                res.setHeader("Retry-After", String.valueOf(retryAfter));
                ErrorWriter.write(mapper, res, 429, "RATE_LIMITED", "Too many requests. Please wait a moment and try again.");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /** Returns 0 when allowed, otherwise the number of seconds until the window resets. */
    private long retryAfterSeconds(String key, int limit) {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) windows.values().removeIf(w -> now - w[0] >= WINDOW_MS);   // keep memory bounded
        long[] result = {0};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= WINDOW_MS) return new long[]{now, 1};
            if (w[1] >= limit) {
                result[0] = Math.max(1, (WINDOW_MS - (now - w[0]) + 999) / 1000);
                return w;
            }
            w[1]++;
            return w;
        });
        return result[0];
    }
}