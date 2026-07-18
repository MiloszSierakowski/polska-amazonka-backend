package pl.polskaamazonka.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.polskaamazonka.backend.config.LinkValidationProperties;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;

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
    private final ProductLinkVerificationService productLinkVerificationService;
    private final ProductRepository productRepository;
    private final LinkValidationHistoryService linkValidationHistoryService;
    private final LinkValidationProperties linkValidationProperties;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    LinkValidatorService(
            LinkRepository linkRepository,
            ProductLinkVerificationService productLinkVerificationService,
            ProductRepository productRepository,
            LinkValidationHistoryService linkValidationHistoryService,
            LinkValidationProperties linkValidationProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.linkRepository = linkRepository;
        this.productLinkVerificationService = productLinkVerificationService;
        this.productRepository = productRepository;
        this.linkValidationHistoryService = linkValidationHistoryService;
        this.linkValidationProperties = linkValidationProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void validateAllLinks() {
        logScheduledValidationStart();
        ScheduledLinkValidationStats stats = executeScheduledLinkValidation();
        logScheduledValidationFinish(stats);
    }

    ScheduledLinkValidationStats executeScheduledLinkValidation() {
        Long runId = linkValidationHistoryService.startRun(ProductLinkVerificationSource.SCHEDULED);
        Instant startedAt = Instant.now();
        ScheduledLinkValidationStats stats = new ScheduledLinkValidationStats(
                startedAt,
                linkValidationProperties.getMaxLinksPerRun(),
                linkValidationProperties.getDelayMsBetweenChecks(),
                linkValidationProperties.getMinHoursBetweenChecks()
        );

        try {
            List<Link> links = selectLinksForScheduledValidation();
            stats.setSelected(links.size());

            for (int index = 0; index < links.size(); index++) {
                if (index > 0) {
                    pauseBetweenChecks();
                }
                Link link = links.get(index);
                try {
                    ScheduledLinkValidationOutcome outcome = transactionTemplate.execute(
                            status -> validateSingleLinkForSchedule(link, runId)
                    );
                    stats.recordOutcome(outcome);
                } catch (Exception exception) {
                    log.warn(
                            "Scheduled validation failed for link id={}: {}",
                            link.getId(),
                            exception.getMessage()
                    );
                    recordUnexpectedScheduledFailure(runId, link);
                    stats.recordTechnicalError();
                }
            }

            stats.finish();
            LinkValidationRunStatus runStatus = stats.getTechnicalErrors() > 0
                    ? LinkValidationRunStatus.COMPLETED_WITH_ERRORS
                    : LinkValidationRunStatus.COMPLETED;
            finishScheduledRunSafely(runId, runStatus, stats, stats.getLastError());
            return stats;
        } catch (RuntimeException exception) {
            stats.finish();
            finishScheduledRunSafely(
                    runId,
                    LinkValidationRunStatus.FAILED,
                    stats,
                    "Nie udało się wykonać zaplanowanej partii weryfikacji."
            );
            throw exception;
        }
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
                "Scheduled link validation finished: selected={}, checked={}, working={}, broken={}, uncertain={}, blocked={}, technicalErrors={}, durationMs={}",
                stats.getSelected(),
                stats.getChecked(),
                stats.getWorking(),
                stats.getBroken(),
                stats.getUncertain(),
                stats.getBlocked(),
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

    ScheduledLinkValidationOutcome validateSingleLinkForSchedule(Link link, Long runId) {
        String url = link.getUrl();
        if ("product".equals(link.getType())) {
            ProductLinkVerificationResult result = productLinkVerificationService.verify(
                    link,
                    ProductLinkVerificationSource.SCHEDULED
            );
            Product product = productRepository.findFirstByProductLink_Id(link.getId()).orElse(null);
            try {
                linkValidationHistoryService.recordItem(runId, link, product, result);
            } catch (RuntimeException exception) {
                log.warn("Could not save validation history item for link id={}", link.getId());
                return ScheduledLinkValidationOutcome.TECHNICAL_ERROR;
            }
            return switch (result.status()) {
                case WORKING -> ScheduledLinkValidationOutcome.WORKING;
                case BROKEN -> ScheduledLinkValidationOutcome.BROKEN;
                case UNCERTAIN -> ScheduledLinkValidationOutcome.UNCERTAIN;
                case BLOCKED -> ScheduledLinkValidationOutcome.BLOCKED;
                case TECHNICAL_ERROR -> ScheduledLinkValidationOutcome.TECHNICAL_ERROR;
            };
        }
        Instant checkedAt = Instant.now();
        if (url == null || url.isBlank()) {
            linkRepository.updateReviewFlags(link.getId(), true, false, checkedAt);
            return ScheduledLinkValidationOutcome.BROKEN;
        }
        boolean broken = isBroken(url.trim());
        link.setIsBroken(broken);
        link.setLastCheckedAt(checkedAt);
        linkRepository.save(link);
        return broken
                ? ScheduledLinkValidationOutcome.BROKEN
                : ScheduledLinkValidationOutcome.WORKING;
    }

    private void recordUnexpectedScheduledFailure(Long runId, Link link) {
        try {
            Product product = productRepository.findFirstByProductLink_Id(link.getId()).orElse(null);
            ProductLinkVerificationResult result = new ProductLinkVerificationResult(
                    ProductLinkVerificationStatus.TECHNICAL_ERROR,
                    ProductLinkVerificationSource.SCHEDULED,
                    Boolean.TRUE.equals(link.getIsBroken()),
                    true,
                    Instant.now(),
                    0L,
                    link.getUrl(),
                    null,
                    "Nie udało się wykonać kontroli linku.",
                    null,
                    null,
                    true,
                    link.getIsBroken(),
                    link.getNeedsReview(),
                    ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE
            );
            linkValidationHistoryService.recordItem(runId, link, product, result);
        } catch (RuntimeException historyException) {
            log.warn("Could not save failed validation history item for link id={}", link.getId());
        }
    }

    private void finishScheduledRunSafely(
            Long runId,
            LinkValidationRunStatus status,
            ScheduledLinkValidationStats stats,
            String lastError
    ) {
        try {
            linkValidationHistoryService.finishRun(runId, status, stats.toRunSummary(), lastError);
        } catch (RuntimeException exception) {
            log.error("Could not finish link validation history run id={}", runId);
        }
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
