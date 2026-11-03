package uz.familymens.store.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import uz.familymens.store.security.LoginThrottleFilter;
import uz.familymens.store.security.RateLimiter;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String DEFAULT_PASSWORD = "admin123";
    private static final String LOGIN_URL = "/admin/login";

    /**
     * Content-Security-Policy: skriptlar faqat o'z saytimizdan; rasmlar https dan
     * (Unsplash, Telegram CDN); shriftlar Google Fonts'dan. Inline skriptlar taqiqlangan.
     */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' https: data: blob:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'");

    @Bean
    LoginThrottleFilter loginThrottleFilter(RateLimiter limiter) {
        return new LoginThrottleFilter(limiter, LOGIN_URL);
    }

    /** Filtr faqat Security zanjirida ishlasin, servlet filtri sifatida ikkinchi marta emas. */
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<LoginThrottleFilter> loginThrottleRegistration(
            LoginThrottleFilter filter) {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LoginThrottleFilter throttle) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_URL).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((req, res, auth) -> {
                            throttle.onSuccess(req);
                            res.sendRedirect(req.getContextPath() + "/admin");
                        })
                        .failureHandler((req, res, ex) -> {
                            throttle.onFailure(req);
                            res.sendRedirect(req.getContextPath() + LOGIN_URL
                                    + (throttle.isBlocked(req) ? "?blocked" : "?error"));
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl(LOGIN_URL + "?logout"))
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(StoreProperties props, PasswordEncoder encoder) {
        if (DEFAULT_PASSWORD.equals(props.adminPassword())) {
            log.warn("⚠ Admin paroli standart ('{}')! ADMIN_PASSWORD muhit o'zgaruvchisi orqali o'zgartiring.",
                    DEFAULT_PASSWORD);
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(props.adminUsername())
                        .password(encoder.encode(props.adminPassword()))
                        .roles("ADMIN")
                        .build());
    }
}
