package pl.polskaamazonka.backend.service;

import java.time.Instant;

public record ProductLinkVerificationResult(
        ProductLinkVerificationStatus status,
        ProductLinkVerificationSource source,
        boolean isBroken,
        boolean needsReview,
        Instant checkedAt,
        long durationMs,
        String originalUrl,
        String checkedUrl,
        String reason,
        String finalUrl,
        Integer httpStatus,
        boolean technicalError,
        Boolean previousIsBroken,
        Boolean previousNeedsReview,
        String message
) {
    public boolean isWorking() {
        return status == ProductLinkVerificationStatus.WORKING;
    }

    public boolean isUncertain() {
        return status == ProductLinkVerificationStatus.UNCERTAIN
                || status == ProductLinkVerificationStatus.BLOCKED
                || status == ProductLinkVerificationStatus.TECHNICAL_ERROR;
    }
}
