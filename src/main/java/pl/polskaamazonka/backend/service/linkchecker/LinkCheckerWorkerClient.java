package pl.polskaamazonka.backend.service.linkchecker;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class LinkCheckerWorkerClient {

    private final RestClient linkCheckerWorkerRestClient;

    public boolean isReachable() {
        try {
            return linkCheckerWorkerRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (RestClientException exception) {
            return false;
        }
    }

    public LinkCheckerWorkerCheckResponse check(String url) {
        try {
            LinkCheckerWorkerCheckResponse response = linkCheckerWorkerRestClient.post()
                    .uri("/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LinkCheckerWorkerCheckRequest(url))
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (request, clientResponse) -> {
                        throw new LinkCheckerWorkerTechnicalException(
                                "Link checker worker returned HTTP " + clientResponse.getStatusCode().value()
                        );
                    })
                    .body(LinkCheckerWorkerCheckResponse.class);
            if (response == null || response.status() == null) {
                throw new LinkCheckerWorkerTechnicalException("Link checker worker returned an empty response");
            }
            return response;
        } catch (LinkCheckerWorkerTechnicalException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new LinkCheckerWorkerTechnicalException("Link checker worker is unavailable", exception);
        } catch (RestClientException exception) {
            throw new LinkCheckerWorkerTechnicalException("Link checker worker call failed", exception);
        }
    }
}
