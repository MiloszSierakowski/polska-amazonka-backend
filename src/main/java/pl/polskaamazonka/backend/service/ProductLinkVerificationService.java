package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckResponse;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckStatus;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerTechnicalException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductLinkVerificationService {

    public static final String TECHNICAL_ERROR_MESSAGE =
            "Nie udało się wykonać automatycznej kontroli. Link wymaga ręcznej weryfikacji.";

    private final LinkRepository linkRepository;
    private final ProductLinkUrlSupport productLinkUrlSupport;
    private final LinkCheckerWorkerClient linkCheckerWorkerClient;

    public ProductLinkVerificationResult verify(Link link, ProductLinkVerificationSource source) {
        long startedAtNanos = System.nanoTime();
        Instant checkedAt = Instant.now();
        String originalUrl = link.getUrl();
        Boolean previousIsBroken = link.getIsBroken();
        Boolean previousNeedsReview = link.getNeedsReview();
        String verificationUrl = null;
        LinkCheckerWorkerCheckResponse response;
        try {
            String storedUrl = link.getUrl();
            if (storedUrl == null || storedUrl.isBlank()) {
                return persistTechnicalFailure(
                        link, source, checkedAt, startedAtNanos, originalUrl,
                        previousIsBroken, previousNeedsReview, null
                );
            }
            verificationUrl = productLinkUrlSupport.verificationUrlForStored(storedUrl.trim());
            response = linkCheckerWorkerClient.check(verificationUrl);
        } catch (LinkCheckerWorkerTechnicalException | IllegalArgumentException exception) {
            return persistTechnicalFailure(
                    link, source, checkedAt, startedAtNanos, originalUrl,
                    previousIsBroken, previousNeedsReview, verificationUrl
            );
        }

        boolean isBroken = switch (response.status()) {
            case WORKING -> false;
            case BROKEN -> true;
            case UNCERTAIN, BLOCKED -> Boolean.TRUE.equals(link.getIsBroken());
        };
        boolean needsReview = response.status() == LinkCheckerWorkerCheckStatus.UNCERTAIN
                || response.status() == LinkCheckerWorkerCheckStatus.BLOCKED;
        persist(link, isBroken, needsReview, checkedAt);

        return new ProductLinkVerificationResult(
                ProductLinkVerificationStatus.valueOf(response.status().name()),
                source,
                isBroken,
                needsReview,
                checkedAt,
                elapsedMillis(startedAtNanos),
                originalUrl,
                verificationUrl,
                response.reason(),
                response.finalUrl(),
                response.httpStatus(),
                false,
                previousIsBroken,
                previousNeedsReview,
                null
        );
    }

    private ProductLinkVerificationResult persistTechnicalFailure(
            Link link,
            ProductLinkVerificationSource source,
            Instant checkedAt,
            long startedAtNanos,
            String originalUrl,
            Boolean previousIsBroken,
            Boolean previousNeedsReview,
            String verificationUrl
    ) {
        boolean isBroken = Boolean.TRUE.equals(link.getIsBroken());
        persist(link, isBroken, true, checkedAt);
        return new ProductLinkVerificationResult(
                ProductLinkVerificationStatus.TECHNICAL_ERROR,
                source,
                isBroken,
                true,
                checkedAt,
                elapsedMillis(startedAtNanos),
                originalUrl,
                verificationUrl,
                TECHNICAL_ERROR_MESSAGE,
                null,
                null,
                true,
                previousIsBroken,
                previousNeedsReview,
                TECHNICAL_ERROR_MESSAGE
        );
    }

    private void persist(Link link, boolean isBroken, boolean needsReview, Instant checkedAt) {
        linkRepository.updateReviewFlags(link.getId(), isBroken, needsReview, checkedAt);
        link.setIsBroken(isBroken);
        link.setNeedsReview(needsReview);
        link.setLastCheckedAt(checkedAt);
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
