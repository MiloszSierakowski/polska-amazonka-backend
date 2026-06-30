package pl.polskaamazonka.backend.service.linkchecker;

import java.time.Instant;

public record LinkCheckerWorkerCheckResponse(
        LinkCheckerWorkerCheckStatus status,
        String reason,
        String finalUrl,
        Integer httpStatus,
        Instant checkedAt
) {
}
