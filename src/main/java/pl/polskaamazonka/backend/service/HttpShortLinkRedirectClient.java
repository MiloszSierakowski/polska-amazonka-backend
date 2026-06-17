package pl.polskaamazonka.backend.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class HttpShortLinkRedirectClient implements ShortLinkRedirectClient {

    static final String EXPANSION_FAILED_MESSAGE = "Nie udało się rozwinąć skróconego linku";
    static final String TOO_MANY_REDIRECTS_MESSAGE = "Zbyt wiele przekierowań podczas rozwijania skróconego linku.";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_REDIRECTS = 3;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final HostSafetyValidator hostSafetyValidator;

    public HttpShortLinkRedirectClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), HttpShortLinkRedirectClient::validateSafeHost);
    }

    HttpShortLinkRedirectClient(HttpClient httpClient, HostSafetyValidator hostSafetyValidator) {
        this.httpClient = httpClient;
        this.hostSafetyValidator = hostSafetyValidator;
    }

    @Override
    public String expand(String url) {
        try {
            URI current = URI.create(url.trim());
            validateHttpScheme(current);
            hostSafetyValidator.validate(current.getHost());

            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                HttpResponse<Void> response = sendHead(current);
                int statusCode = response.statusCode();

                if (isRedirect(statusCode)) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw new ShortLinkExpansionException(TOO_MANY_REDIRECTS_MESSAGE);
                    }
                    current = resolveRedirect(current, response);
                    continue;
                }

                if (statusCode == 405 || statusCode == 501) {
                    response = sendGet(current);
                    statusCode = response.statusCode();
                    if (isRedirect(statusCode)) {
                        if (redirectCount == MAX_REDIRECTS) {
                            throw new ShortLinkExpansionException(TOO_MANY_REDIRECTS_MESSAGE);
                        }
                        current = resolveRedirect(current, response);
                        continue;
                    }
                }

                if (statusCode >= 200 && statusCode < 300) {
                    return current.toString();
                }
                throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
            }
        } catch (ShortLinkExpansionException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
        throw new ShortLinkExpansionException(TOO_MANY_REDIRECTS_MESSAGE);
    }

    private HttpResponse<Void> sendHead(URI uri) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<Void> sendGet(URI uri) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(uri)
                .header("Range", "bytes=0-0")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8");
    }

    private URI resolveRedirect(URI current, HttpResponse<?> response) {
        String location = response.headers().firstValue("Location").orElse(null);
        if (location == null || location.isBlank()) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
        URI resolved = current.resolve(location.trim());
        validateHttpScheme(resolved);
        hostSafetyValidator.validate(resolved.getHost());
        return resolved;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private void validateHttpScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
    }

    static void validateSafeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
                }
            }
        } catch (ShortLinkExpansionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ShortLinkExpansionException(EXPANSION_FAILED_MESSAGE);
        }
    }

    static String normalizeHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.")) {
            return lower.substring(4);
        }
        return lower;
    }

    @FunctionalInterface
    interface HostSafetyValidator {
        void validate(String host);
    }
}
