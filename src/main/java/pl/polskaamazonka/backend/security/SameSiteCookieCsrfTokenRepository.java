package pl.polskaamazonka.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.time.Duration;

public class SameSiteCookieCsrfTokenRepository implements CsrfTokenRepository {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private final CookieCsrfTokenRepository delegate;
    private final boolean secure;

    public SameSiteCookieCsrfTokenRepository(boolean secure) {
        this.secure = secure;
        this.delegate = CookieCsrfTokenRepository.withHttpOnlyFalse();
        this.delegate.setCookieName(COOKIE_NAME);
        this.delegate.setHeaderName(HEADER_NAME);
        this.delegate.setCookiePath("/");
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return delegate.generateToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie
                .from(COOKIE_NAME, token == null ? "" : token.getToken())
                .httpOnly(false)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
        if (token == null) {
            cookie.maxAge(Duration.ZERO);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }
}
