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

/**
 * Applies the Redis-backed limits. Not a @Component: SecurityConfig creates it so it is registered once, inside the security chain,
 * after JwtAuthenticationFilter so the authenticated user is known. Unauthenticated calls to non-auth endpoints are skipped here
 * because they are rejected with 401 before any expensive work happens.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final RateLimitService limits;
    private final ClientIpResolver ips;

    public RateLimitFilter(ObjectMapper mapper, RateLimitService limits, ClientIpResolver ips) {
        this.mapper = mapper;
        this.limits = limits;
        this.ips = ips;
    }

    private static RateLimitService.Kind kindFor(String path) {
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) return RateLimitService.Kind.AUTH;
        if (path.equals("/api/sessions")) return RateLimitService.Kind.CREATE;
        if (path.startsWith("/api/sessions/")) {
            if (path.endsWith("/turns")) return RateLimitService.Kind.TURN;
            if (path.endsWith("/complete")) return RateLimitService.Kind.COMPLETE;
            if (path.endsWith("/alternative-replies")) return RateLimitService.Kind.ALTERNATIVE;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        RateLimitService.Kind kind = "POST".equals(req.getMethod()) ? kindFor(req.getRequestURI()) : null;
        if (kind != null) {
            String subject = null;
            if (kind == RateLimitService.Kind.AUTH) {
                subject = ips.resolve(req);
            } else {
                Authentication a = SecurityContextHolder.getContext().getAuthentication();
                if (a != null && a.getPrincipal() instanceof AuthUser u) subject = u.id().toString();
            }
            if (subject != null) {
                RateLimitService.Decision d = limits.check(kind, subject);
                if (d.unavailable()) {
                    res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
                    ErrorWriter.write(mapper, res, 503, "RATE_LIMIT_UNAVAILABLE", "The service is temporarily unavailable. Please try again shortly.");
                    return;
                }
                if (!d.allowed()) {
                    res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
                    ErrorWriter.write(mapper, res, 429, "RATE_LIMITED", "Too many requests. Please wait a moment and try again.");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }
}