package pl.polskaamazonka.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCookieServiceTest {

    private static final long DAY_MILLIS = 86_400_000L;

    @Test
    void localCookieHasRequiredAttributesAndTwentyFourHourLifetime() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtCookieService(DAY_MILLIS, false).setTokenCookie(response, "jwt-value");

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(header.contains("PA_ADMIN_TOKEN=jwt-value"));
        assertTrue(header.contains("Max-Age=86400"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
        assertFalse(header.contains("Secure"));
    }

    @Test
    void productionCookieIsSecure() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtCookieService(DAY_MILLIS, true).setTokenCookie(response, "jwt-value");

        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Secure"));
    }

    @Test
    void clearingCookieExpiresItImmediately() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtCookieService(DAY_MILLIS, true).clearTokenCookie(response);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(header.contains("PA_ADMIN_TOKEN="));
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("Path=/"));
    }
}
