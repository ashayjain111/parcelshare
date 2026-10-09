package com.parcelshare.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tiny in-memory fixed-window limiter for the unauthenticated, abuse-prone endpoints.
 * Fine for a single instance; swap for a shared store (Redis/Bucket4j) when you scale out.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private final int limit;

    public RateLimitFilter(int limit) {
        this.limit = limit;
    }

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !(p.startsWith("/auth/") || p.startsWith("/public/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getRemoteAddr() + "|" + (req.getRequestURI().startsWith("/auth/") ? "auth" : "public");
        long now = System.currentTimeMillis();
        Window w = windows.merge(key, new Window(now, 1),
                (old, fresh) -> now - old.start > WINDOW_MS ? fresh : new Window(old.start, old.count + 1));
        if (w.count > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests, slow down\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > WINDOW_MS);
        }
        chain.doFilter(req, res);
    }
}
