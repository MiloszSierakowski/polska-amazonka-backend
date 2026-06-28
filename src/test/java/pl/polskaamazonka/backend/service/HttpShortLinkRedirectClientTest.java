package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

class HttpShortLinkRedirectClientTest {

    private HttpClient httpClient;
    private HttpShortLinkRedirectClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        client = new HttpShortLinkRedirectClient(httpClient, host -> {
        });
    }

    @Test
    void followsHeadRedirect() throws Exception {
        HttpResponse<Void> redirect = response(302, "https://www.temu.com/pl/nazwa-g-601099999999999.html");
        HttpResponse<Void> ok = response(200, null);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(redirect)
                .thenReturn(ok);

        String expandedUrl = client.expand("https://share.temu.com/abc123");

        assertEquals("https://www.temu.com/pl/nazwa-g-601099999999999.html", expandedUrl);
    }

    @Test
    void fallsBackToGetWhenHeadIsNotAllowed() throws Exception {
        HttpResponse<Void> methodNotAllowed = response(405, null);
        HttpResponse<Void> redirect = response(302, "https://pl.aliexpress.com/item/1005001234567890.html");
        HttpResponse<Void> ok = response(200, null);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(methodNotAllowed)
                .thenReturn(redirect)
                .thenReturn(ok);

        String expandedUrl = client.expand("https://s.click.aliexpress.com/e/_c2x0Gp5j");

        assertEquals("https://pl.aliexpress.com/item/1005001234567890.html", expandedUrl);
    }

    @Test
    void tooManyRedirectsFails() throws Exception {
        HttpResponse<Void> first = response(302, "https://share.temu.com/1");
        HttpResponse<Void> second = response(302, "https://share.temu.com/2");
        HttpResponse<Void> third = response(302, "https://share.temu.com/3");
        HttpResponse<Void> fourth = response(302, "https://www.temu.com/pl/nazwa-g-601099999999999.html");
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(first)
                .thenReturn(second)
                .thenReturn(third)
                .thenReturn(fourth);

        ShortLinkExpansionException exception = assertThrows(
                ShortLinkExpansionException.class,
                () -> client.expand("https://share.temu.com/start")
        );

        assertEquals(HttpShortLinkRedirectClient.TOO_MANY_REDIRECTS_MESSAGE, exception.getMessage());
    }

    @Test
    void networkErrorFailsWithoutRetry() throws Exception {
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenThrow(new IOException("timeout"));

        ShortLinkExpansionException exception = assertThrows(
                ShortLinkExpansionException.class,
                () -> client.expand("https://share.temu.com/abc123")
        );

        assertEquals(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE, exception.getMessage());
    }

    @Test
    void localhostIsBlocked() {
        ShortLinkExpansionException exception = assertThrows(
                ShortLinkExpansionException.class,
                () -> HttpShortLinkRedirectClient.validateSafeHost("localhost")
        );

        assertEquals(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE, exception.getMessage());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Void> response(int statusCode, String location) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.headers()).thenReturn(headers);
        when(headers.firstValue("Location")).thenReturn(Optional.ofNullable(location));
        return response;
    }
}
