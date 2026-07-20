package pl.polskaamazonka.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.polskaamazonka.backend.dto.LoginRequest;
import pl.polskaamazonka.backend.dto.LoginResponse;
import pl.polskaamazonka.backend.dto.UpdateUserProfileRequest;
import pl.polskaamazonka.backend.dto.UserProfileDTO;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.security.JwtCookieService;
import pl.polskaamazonka.backend.service.AuthService;
import pl.polskaamazonka.backend.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCookieControllerTest {

    private JwtCookieService cookieService;

    @BeforeEach
    void setUp() {
        cookieService = new JwtCookieService(86_400_000L, false);
    }

    @Test
    void loginSetsCookieWithoutReturningTokenInResponse() {
        AuthService authService = mock(AuthService.class);
        LoginRequest request = new LoginRequest();
        LoginResponse expected = new LoginResponse(
                1L, "admin", UserRole.ADMIN, null, null, null
        );
        when(authService.login(request)).thenReturn(new AuthService.LoginResult(expected, "login-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        LoginResponse result = new AuthController(authService, cookieService).login(request, response);

        assertEquals("admin", result.getLogin());
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("PA_ADMIN_TOKEN=login-jwt"));
    }

    @Test
    void logoutPreservesActivityCallAndExpiresCookie() {
        AuthService authService = mock(AuthService.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthController(authService, cookieService).logout(response);

        verify(authService).logout();
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    void profileCredentialChangeRefreshesCookie() {
        UserService userService = mock(UserService.class);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        UserProfileDTO profile = new UserProfileDTO();
        when(userService.updateCurrentProfile(request))
                .thenReturn(new UserService.ProfileUpdateResult(profile, "refreshed-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserProfileDTO result = new UserProfileController(userService, cookieService)
                .updateProfile(request, response);

        assertEquals(profile, result);
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("PA_ADMIN_TOKEN=refreshed-jwt"));
    }

    @Test
    void profileUpdateWithoutNewTokenDoesNotSetCookie() {
        UserService userService = mock(UserService.class);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        UserProfileDTO profile = new UserProfileDTO();
        when(userService.updateCurrentProfile(request))
                .thenReturn(new UserService.ProfileUpdateResult(profile, null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserProfileDTO result = new UserProfileController(userService, cookieService)
                .updateProfile(request, response);

        assertNotNull(result);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }
}
