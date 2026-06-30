package pl.polskaamazonka.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.polskaamazonka.backend.config.LinkValidationProperties;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckResponse;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckStatus;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class LinkValidatorService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BODY_BYTES = 262_144;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final List<String> BROKEN_CONTENT_MARKERS = List.of(
            "page_not_found_notice",
            "page_not_found_rcmd_title",
            "page_not_found",
            "item-not-found",
            "product-not-found",
            "detail-not-found",
            "przedmiot chwilowo niedostępny",
            "nie znaleziono pożądanej strony",
            "chwilowo niedostępny",
            "page not found",
            "page under maintenance",
            "item is unavailable",
            "item is not available",
            "this item is unavailable",
            "this item is no longer available",
            "product is unavailable",
            "product not found",
            "goods not found",
            "the page you requested cannot be found",
            "sorry, the page you requested cannot be found",
            "ojej, nie możemy znaleźć tej strony",
            "nie możemy znaleźć tej strony",
            "404 not found",
            "404 error",
            "error 404",
            "404 page",
            "http 404",
            "product-not-found",
            "item-not-found",
            "page-not-found",
            "not-found-page",
            "goods-not-found",
            "page-404",
            "item-unavailable",
            "product-unavailable",
            "notfound\":true",
            "notfound\": true",
            "\"notfound\":true",
            "\"status\":404",
            "this item is invalid",
            "item does not exist",
            "product does not exist"
    );

    private final LinkRepository linkRepository;
    private final LinkCheckerWorkerClient linkCheckerWorkerClient;
    private final ProductLinkUrlSupport productLinkUrlSupport;
    private final LinkValidationProperties linkValidationProperties;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    LinkValidatorService(
            LinkRepository linkRepository,
            LinkCheckerWorkerClient linkCheckerWorkerClient,
            ProductLinkUrlSupport productLinkUrlSupport,
            LinkValidationProperties linkValidationProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.linkRepository = linkRepository;
        this.linkCheckerWorkerClient = linkCheckerWorkerClient;
        this.productLinkUrlSupport = productLinkUrlSupport;
        this.linkValidationProperties = linkValidationProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void validateAllLinks() {
        logScheduledValidationStart();
        ScheduledLinkValidationStats stats = executeScheduledLinkValidation();
        logScheduledValidationFinish(stats);
    }

    ScheduledLinkValidationStats executeScheduledLinkValidation() {
        Instant startedAt = Instant.now();
        ScheduledLinkValidationStats stats = new ScheduledLinkValidationStats(
                startedAt,
                linkValidationProperties.getMaxLinksPerRun(),
                linkValidationProperties.getDelayMsBetweenChecks(),
                linkValidationProperties.getMinHoursBetweenChecks()
        );

        List<Link> links = selectLinksForScheduledValidation();
        stats.setSelected(links.size());

        for (int index = 0; index < links.size(); index++) {
            if (index > 0) {
                pauseBetweenChecks();
            }
            Link link = links.get(index);
            try {
                ScheduledLinkValidationOutcome outcome = transactionTemplate.execute(
                        status -> validateSingleLinkForSchedule(link)
                );
                stats.recordOutcome(outcome);
            } catch (Exception exception) {
                log.warn(
                        "Scheduled validation failed for link id={}: {}",
                        link.getId(),
                        exception.getMessage()
                );
                transactionTemplate.executeWithoutResult(
                        status -> markLinkValidationUncertain(link, Instant.now())
                );
                stats.recordTechnicalError();
            }
        }

        stats.finish();
        return stats;
    }

    private void logScheduledValidationStart() {
        log.info(
                "Starting scheduled link validation: maxLinksPerRun={}, delayMs={}, minHoursBetweenChecks={}",
                linkValidationProperties.getMaxLinksPerRun(),
                linkValidationProperties.getDelayMsBetweenChecks(),
                linkValidationProperties.getMinHoursBetweenChecks()
        );
    }

    private void logScheduledValidationFinish(ScheduledLinkValidationStats stats) {
        log.info(
                "Scheduled link validation finished: selected={}, checked={}, working={}, broken={}, uncertain={}, technicalErrors={}, durationMs={}",
                stats.getSelected(),
                stats.getChecked(),
                stats.getWorking(),
                stats.getBroken(),
                stats.getUncertain(),
                stats.getTechnicalErrors(),
                stats.getDurationMs()
        );
    }

    List<Link> selectLinksForScheduledValidation() {
        Instant cutoff = Instant.now().minus(
                linkValidationProperties.getMinHoursBetweenChecks(),
                ChronoUnit.HOURS
        );
        int maxLinks = Math.max(linkValidationProperties.getMaxLinksPerRun(), 0);
        if (maxLinks == 0) {
            return List.of();
        }
        return linkRepository.findLinksForScheduledValidation(
                cutoff,
                PageRequest.of(0, maxLinks)
        );
    }

    ScheduledLinkValidationOutcome validateSingleLinkForSchedule(Link link) {
        Instant checkedAt = Instant.now();
        String url = link.getUrl();
        if (url == null || url.isBlank()) {
            linkRepository.updateReviewFlags(link.getId(), true, false, checkedAt);
            return ScheduledLinkValidationOutcome.BROKEN;
        }
        if ("product".equals(link.getType())) {
            String verificationUrl = productLinkUrlSupport.verificationUrlForStored(url.trim());
            LinkCheckerWorkerCheckResponse workerResponse = linkCheckerWorkerClient.check(verificationUrl);
            applyWorkerCheckResult(link, workerResponse, checkedAt);
            return toWorkerOutcome(workerResponse.status());
        }
        boolean broken = isBroken(url.trim());
        link.setIsBroken(broken);
        link.setLastCheckedAt(checkedAt);
        linkRepository.save(link);
        return broken
                ? ScheduledLinkValidationOutcome.BROKEN
                : ScheduledLinkValidationOutcome.WORKING;
    }

    private ScheduledLinkValidationOutcome toWorkerOutcome(LinkCheckerWorkerCheckStatus status) {
        return switch (status) {
            case WORKING -> ScheduledLinkValidationOutcome.WORKING;
            case BROKEN -> ScheduledLinkValidationOutcome.BROKEN;
            case UNCERTAIN, BLOCKED -> ScheduledLinkValidationOutcome.UNCERTAIN;
        };
    }

    public boolean isBroken(String url) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8");
            applyPlatformHeaders(requestBuilder, url);
            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode <= 0 || statusCode >= 400) {
                discardBody(response.body());
                return true;
            }
            try (InputStream body = response.body()) {
                String html = readLimitedUtf8(body, MAX_BODY_BYTES);
                return containsBrokenContent(html);
            }
        } catch (IllegalArgumentException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }

    private void applyWorkerCheckResult(Link link, LinkCheckerWorkerCheckResponse response, Instant checkedAt) {
        LinkCheckerWorkerCheckStatus status = response.status();
        if (status == LinkCheckerWorkerCheckStatus.UNCERTAIN || status == LinkCheckerWorkerCheckStatus.BLOCKED) {
            markLinkValidationUncertain(link, checkedAt);
            return;
        }
        linkRepository.updateReviewFlags(
                link.getId(),
                status == LinkCheckerWorkerCheckStatus.BROKEN,
                false,
                checkedAt
        );
    }

    private void markLinkValidationUncertain(Link link, Instant checkedAt) {
        linkRepository.updateReviewFlags(
                link.getId(),
                Boolean.TRUE.equals(link.getIsBroken()),
                true,
                checkedAt
        );
    }

    private void pauseBetweenChecks() {
        long delayMs = linkValidationProperties.getDelayMsBetweenChecks();
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Scheduled link validation interrupted during delay");
        }
    }

    private boolean containsBrokenContent(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String normalized = html.toLowerCase(Locale.ROOT);
        for (String marker : BROKEN_CONTENT_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        if (normalized.contains("404")) {
            if (normalized.contains("not found")
                    || normalized.contains("unavailable")
                    || normalized.contains("nie możemy znaleźć")
                    || normalized.contains("<title>404")
                    || normalized.contains("error")) {
                return true;
            }
        }
        return false;
    }

    private void applyPlatformHeaders(HttpRequest.Builder requestBuilder, String url) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("aliexpress.")) {
            requestBuilder.header("Referer", "https://www.aliexpress.com/");
            return;
        }
        if (lowerUrl.contains("temu.com")) {
            requestBuilder.header("Referer", "https://www.temu.com/");
            return;
        }
        if (lowerUrl.contains("amazon.")) {
            requestBuilder.header("Referer", "https://www.amazon.pl/");
        }
    }

    private String readLimitedUtf8(InputStream inputStream, int maxBytes) throws IOException {
        byte[] buffer = new byte[maxBytes];
        int totalRead = 0;
        int read;
        while (totalRead < maxBytes && (read = inputStream.read(buffer, totalRead, maxBytes - totalRead)) != -1) {
            totalRead += read;
        }
        discardBody(inputStream);
        if (totalRead == 0) {
            return "";
        }
        return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
    }

    private void discardBody(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            byte[] skipBuffer = new byte[8192];
            while (inputStream.read(skipBuffer) != -1) {
            }
        } catch (IOException ignored) {
        }
    }
}
