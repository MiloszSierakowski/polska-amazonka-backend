package pl.polskaamazonka.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Service
public class JwtCookieService {

    public static final String COOKIE_NAME = "PA_ADMIN_TOKEN";

    private final Duration tokenLifetime;
    private final boolean secure;

    public JwtCookieService(
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${app.auth.cookie.secure:false}") boolean secure
    ) {
        this.tokenLifetime = Duration.ofMillis(expirationMs);
        this.secure = secure;
    }

    public void setTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = baseCookie(token)
                .maxAge(tokenLifetime)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }
}
