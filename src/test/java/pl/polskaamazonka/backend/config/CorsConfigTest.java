package pl.polskaamazonka.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void configuredOriginsAreExactAndCredentialsAreAllowed() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(
                config,
                "allowedOrigins",
                "http://localhost:4200, https://admin.example"
        );
        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/profile");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(List.of("http://localhost:4200", "https://admin.example"), configuration.getAllowedOrigins());
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedMethods().contains("PATCH"));
        assertTrue(configuration.getAllowedHeaders().contains("X-XSRF-TOKEN"));
    }
}
