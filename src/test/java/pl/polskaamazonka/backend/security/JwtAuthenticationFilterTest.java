package pl.polskaamazonka.backend.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                new JwtCookieService(86_400_000L, false)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUsingCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtCookieService.COOKIE_NAME, "cookie-jwt"));
        UserDetails user = allowedUser("administrator");
        when(jwtService.extractUsername("cookie-jwt")).thenReturn("administrator");
        when(userDetailsService.loadUserByUsername("administrator")).thenReturn(user);
        when(jwtService.isTokenValid("cookie-jwt", user)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("administrator", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(jwtService).extractUsername("cookie-jwt");
    }

    @Test
    void bearerDoesNotAuthenticateWhenCookieIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer administrator-jwt");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void blockedUserIsNotAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtCookieService.COOKIE_NAME, "cookie-jwt"));
        UserDetails user = allowedUser("blocked");
        when(user.isAccountNonLocked()).thenReturn(false);
        when(jwtService.extractUsername("cookie-jwt")).thenReturn("blocked");
        when(userDetailsService.loadUserByUsername("blocked")).thenReturn(user);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private UserDetails allowedUser(String username) {
        UserDetails user = mock(UserDetails.class);
        when(user.getUsername()).thenReturn(username);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isCredentialsNonExpired()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        return user;
    }
}
