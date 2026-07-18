package pl.polskaamazonka.backend.dto;

import pl.polskaamazonka.backend.service.ProductLinkVerificationStatus;

import java.time.Instant;

public record LinkValidationRunItemDTO(
        Long id,
        Long linkId,
        Long productId,
        String productNameSnapshot,
        String originalUrl,
        String normalizedUrl,
        String finalUrl,
        ProductLinkVerificationStatus verificationStatus,
        String reason,
        Integer httpStatus,
        long durationMs,
        Instant checkedAt,
        boolean technicalError,
        Boolean previousIsBroken,
        Boolean newIsBroken,
        Boolean previousNeedsReview,
        Boolean newNeedsReview
) {
}
