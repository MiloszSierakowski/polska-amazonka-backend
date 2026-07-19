package pl.polskaamazonka.backend.service.linkchecker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LinkCheckerWorkerClientTest {

    private static final String BASE_URL = "http://localhost:3001";
    private static final String TOKEN = "test-worker-token";

    private MockRestServiceServer server;
    private LinkCheckerWorkerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new LinkCheckerWorkerClient(restClientBuilder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void check_returnsWorkerResponse() {
        server.expect(requestTo(BASE_URL + "/check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().json("{\"url\":\"https://example.com/product\"}"))
                .andRespond(withSuccess("""
                        {
                          "status": "WORKING",
                          "reason": "Page loaded successfully",
                          "finalUrl": "https://example.com/product",
                          "httpStatus": 200,
                          "checkedAt": "2026-06-28T10:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        LinkCheckerWorkerCheckResponse response = client.check("https://example.com/product");

        assertEquals(LinkCheckerWorkerCheckStatus.WORKING, response.status());
        assertEquals("Page loaded successfully", response.reason());
        assertEquals("https://example.com/product", response.finalUrl());
        assertEquals(200, response.httpStatus());
        assertEquals(Instant.parse("2026-06-28T10:00:00Z"), response.checkedAt());
    }

    @Test
    void check_http503ThrowsTechnicalException() {
        server.expect(requestTo(BASE_URL + "/check"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        LinkCheckerWorkerTechnicalException exception = assertThrows(
                LinkCheckerWorkerTechnicalException.class,
                () -> client.check("https://example.com/product")
        );

        assertEquals("Link checker worker returned HTTP 503", exception.getMessage());
    }

    @Test
    void check_unauthorizedErrorDoesNotExposeToken() {
        server.expect(requestTo(BASE_URL + "/check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        LinkCheckerWorkerTechnicalException exception = assertThrows(
                LinkCheckerWorkerTechnicalException.class,
                () -> client.check("https://example.com/product")
        );

        assertFalse(exception.getMessage().contains(TOKEN));
    }

    @Test
    void isReachable_returnsTrueForSuccessfulHealthResponse() {
        server.expect(requestTo(BASE_URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess());

        assertTrue(client.isReachable());
    }

    @Test
    void isReachable_returnsFalseWithoutExposingWorkerError() {
        server.expect(requestTo(BASE_URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertFalse(client.isReachable());
    }
}
