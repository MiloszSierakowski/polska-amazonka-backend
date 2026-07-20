package pl.polskaamazonka.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;
import pl.polskaamazonka.backend.dto.LoginRequest;
import pl.polskaamazonka.backend.dto.LoginResponse;
import pl.polskaamazonka.backend.security.JwtCookieService;
import pl.polskaamazonka.backend.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return Map.of("status", "CSRF_TOKEN_INITIALIZED");
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult loginResult = authService.login(request);
        jwtCookieService.setTokenCookie(response, loginResult.jwt());
        return loginResult.response();
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        try {
            authService.logout();
        } finally {
            jwtCookieService.clearTokenCookie(response);
        }
    }
}
