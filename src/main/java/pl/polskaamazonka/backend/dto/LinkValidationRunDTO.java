package pl.polskaamazonka.backend.dto;

import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.service.ProductLinkVerificationSource;

import java.time.Instant;

public record LinkValidationRunDTO(
        Long id,
        ProductLinkVerificationSource source,
        LinkValidationRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        int selectedCount,
        int checkedCount,
        int workingCount,
        int brokenCount,
        int uncertainCount,
        int blockedCount,
        int technicalErrorCount,
        String lastError,
        String triggeredBy
) {
}
