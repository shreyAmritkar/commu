package com.engish.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Returns the caller's IP. X-Forwarded-For is honoured only when the direct peer is a configured trusted proxy,
 * and it is read from the right, skipping trusted hops, so a client cannot spoof its address by sending its own header.
 */
@Component
public class ClientIpResolver {
    // Literals only: IpAddressMatcher would otherwise try to resolve hostnames.
    private static final Pattern IP_LITERAL = Pattern.compile(
            "^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*)$");

    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(HttpProperties props) {
        this.trusted = props.trustedProxies().stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();   // invalid entries fail at startup
    }

    public String resolve(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        if (trusted.isEmpty() || !isTrusted(remote)) return remote;

        String forwarded = String.join(",", Collections.list(req.getHeaders("X-Forwarded-For")));
        if (forwarded.isBlank()) return remote;

        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!IP_LITERAL.matcher(hop).matches()) return remote;
            if (!isTrusted(hop)) return hop;
        }
        return remote;
    }

    private boolean isTrusted(String ip) {
        try {
            return trusted.stream().anyMatch(m -> m.matches(ip));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}