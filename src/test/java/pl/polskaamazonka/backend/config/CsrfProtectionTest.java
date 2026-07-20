package pl.polskaamazonka.backend.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.controller.AuthController;
import pl.polskaamazonka.backend.dto.LoginResponse;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.security.CustomUserDetailsService;
import pl.polskaamazonka.backend.security.JwtAuthenticationFilter;
import pl.polskaamazonka.backend.security.JwtCookieService;
import pl.polskaamazonka.backend.security.JwtService;
import pl.polskaamazonka.backend.service.AuthService;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        CsrfConfig.class,
        JwtCookieService.class,
        JwtAuthenticationFilter.class,
        CsrfProtectionTest.MutationController.class
})
@TestPropertySource(properties = {
        "app.auth.cookie.secure=false",
        "jwt.expiration-ms=86400000"
})
class CsrfProtectionTest {

    @Autowired
    private CsrfTokenRepository repository;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        LoginResponse response = new LoginResponse(
                1L, "admin", UserRole.ADMIN, null, null, null
        );
        when(authService.login(any())).thenReturn(new AuthService.LoginResult(response, "jwt"));
        when(jwtService.extractUsername("authenticated-jwt")).thenReturn("admin");
        when(jwtService.extractUsername("jwt")).thenReturn("admin");
        when(jwtService.extractUsername("worker-jwt")).thenReturn("worker");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(
                User.withUsername("admin").password("password").roles("ADMIN").build()
        );
        when(userDetailsService.loadUserByUsername("worker")).thenReturn(
                User.withUsername("worker").password("password").roles("WORKER").build()
        );
        when(jwtService.isTokenValid(any(), any())).thenReturn(true);
    }

    @Test
    void csrfEndpointCreatesReadableLocalCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = csrfSetCookie(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
        assertTrue(setCookie.contains("XSRF-TOKEN="));
        assertTrue(setCookie.contains("Path=/"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertFalse(setCookie.contains("HttpOnly"));
        assertFalse(setCookie.contains("Secure"));
    }

    @Test
    void productionCookieIsSecureAndSameSiteLax() {
        new ApplicationContextRunner()
                .withUserConfiguration(CsrfConfig.class)
                .withPropertyValues("app.auth.cookie.secure=true")
                .run(context -> {
                    CsrfTokenRepository productionRepository =
                            context.getBean(CsrfTokenRepository.class);
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    CsrfToken token = productionRepository.generateToken(request);

                    productionRepository.saveToken(token, request, response);

                    String setCookie = csrfSetCookie(response.getHeaders(HttpHeaders.SET_COOKIE));
                    assertTrue(setCookie.contains("Secure"));
                    assertTrue(setCookie.contains("SameSite=Lax"));
                    assertTrue(setCookie.contains("Path=/"));
                    assertFalse(setCookie.contains("HttpOnly"));
                });
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void invalidCsrfHasDedicatedErrorCodeAndCorrectPolishMessage() throws Exception {
        CsrfRequest csrf = csrfRequest();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("Token CSRF jest nieprawidłowy lub wygasł."));
    }

    @Test
    void ordinaryAccessDeniedDoesNotUseCsrfErrorCode() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .cookie(new Cookie(JwtCookieService.COOKIE_NAME, "worker-jwt")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.message").value("Brak uprawnień do wykonania tej operacji."));
    }

    @Test
    void loginWithCsrfSucceedsAndJwtRemainsHttpOnly() throws Exception {
        CsrfRequest csrf = csrfRequest();

        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"password\":\"password\"}"), csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        String jwtCookie = cookieNamed(
                result.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                JwtCookieService.COOKIE_NAME
        );
        assertTrue(jwtCookie.contains("HttpOnly"));
    }

    @Test
    void loginAllowsFirstMutationAndFirstLogoutWithSameCsrfToken() throws Exception {
        CsrfRequest csrf = csrfRequest();
        MockHttpServletRequestBuilder login = withCsrf(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"admin\",\"password\":\"password\"}"), csrf);
        mockMvc.perform(login).andExpect(status().isOk());

        mockMvc.perform(withCsrf(withAuthentication(post("/test/mutation"), "jwt"), csrf))
                .andExpect(status().isOk());
        mockMvc.perform(withCsrf(withAuthentication(post("/api/auth/logout"), "jwt"), csrf))
                .andExpect(status().isOk());
    }

    @Test
    void existingCsrfCookieIsNotRegeneratedByOrdinaryRequest() throws Exception {
        CsrfRequest csrf = csrfRequest();

        MvcResult result = mockMvc.perform(get("/api/auth/csrf").cookie(csrf.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(header -> header.startsWith("XSRF-TOKEN=")));
    }

    @Test
    void allMutationMethodsRequireCsrfAndAcceptValidToken() throws Exception {
        CsrfRequest csrf = csrfRequest();
        for (MockHttpServletRequestBuilder request : mutationRequests().toList()) {
            mockMvc.perform(withAuthentication(request)).andExpect(status().isForbidden());
        }
        for (MockHttpServletRequestBuilder request : mutationRequests().toList()) {
            mockMvc.perform(withCsrf(withAuthentication(request), csrf)).andExpect(status().isOk());
        }
    }

    @Test
    void publicGetDoesNotRequireCsrf() throws Exception {
        mockMvc.perform(get("/test/mutation"))
                .andExpect(status().isOk());
    }

    private CsrfRequest csrfRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        return new CsrfRequest(token.getToken(), new Cookie("XSRF-TOKEN", token.getToken()));
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request,
            CsrfRequest csrf
    ) {
        return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
    }

    private MockHttpServletRequestBuilder withAuthentication(MockHttpServletRequestBuilder request) {
        return withAuthentication(request, "authenticated-jwt");
    }

    private MockHttpServletRequestBuilder withAuthentication(
            MockHttpServletRequestBuilder request,
            String jwt
    ) {
        return request.cookie(new Cookie(JwtCookieService.COOKIE_NAME, jwt));
    }

    private Stream<MockHttpServletRequestBuilder> mutationRequests() {
        return Stream.of(
                post("/test/mutation"),
                put("/test/mutation"),
                patch("/test/mutation"),
                delete("/test/mutation")
        );
    }

    private static String csrfSetCookie(List<String> headers) {
        return cookieNamed(headers, "XSRF-TOKEN");
    }

    private static String cookieNamed(List<String> headers, String name) {
        return headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Set-Cookie for " + name));
    }

    private record CsrfRequest(String token, Cookie cookie) {}

    @RestController
    @RequestMapping("/test/mutation")
    public static class MutationController {
        @GetMapping public void get() {}
        @PostMapping public void post() {}
        @PutMapping public void put() {}
        @PatchMapping public void patch() {}
        @DeleteMapping public void delete() {}
    }
}
