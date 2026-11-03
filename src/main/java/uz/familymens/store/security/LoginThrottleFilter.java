package uz.familymens.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Bir IP'dan 15 daqiqa ichida 5 ta noto'g'ri parol kiritilsa, login vaqtincha bloklanadi.
 * Xato urinishlar {@link #onFailure}, muvaffaqiyat {@link #onSuccess} orqali hisoblanadi.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimiter limiter;
    private final String loginUrl;

    public LoginThrottleFilter(RateLimiter limiter, String loginUrl) {
        this.limiter = limiter;
        this.loginUrl = loginUrl;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && loginUrl.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (isBlocked(req)) {
            res.sendRedirect(req.getContextPath() + loginUrl + "?blocked");
            return;
        }
        chain.doFilter(req, res);
    }

    public boolean isBlocked(HttpServletRequest req) {
        return limiter.count(key(req), WINDOW) >= MAX_FAILURES;
    }

    public void onFailure(HttpServletRequest req) {
        limiter.record(key(req));
    }

    public void onSuccess(HttpServletRequest req) {
        limiter.reset(key(req));
    }

    private static String key(HttpServletRequest req) {
        return "login:" + req.getRemoteAddr();
    }
}
