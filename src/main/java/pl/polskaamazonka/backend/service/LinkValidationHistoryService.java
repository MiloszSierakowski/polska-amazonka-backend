package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.LinkValidationRun;
import pl.polskaamazonka.backend.model.LinkValidationRunItem;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.LinkValidationRunItemRepository;
import pl.polskaamazonka.backend.repository.LinkValidationRunRepository;
import pl.polskaamazonka.backend.security.UserPrincipal;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LinkValidationHistoryService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final LinkValidationRunRepository runRepository;
    private final LinkValidationRunItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startRun(ProductLinkVerificationSource source) {
        LinkValidationRun run = new LinkValidationRun();
        run.setSource(source);
        run.setStatus(LinkValidationRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setTriggeredBy(source == ProductLinkVerificationSource.MANUAL ? resolveTriggeredBy() : null);
        return runRepository.save(run).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordItem(
            Long runId,
            Link link,
            Product product,
            ProductLinkVerificationResult result
    ) {
        LinkValidationRunItem item = new LinkValidationRunItem();
        item.setRun(runRepository.getReferenceById(runId));
        item.setLinkId(link != null ? link.getId() : null);
        item.setProductId(product != null ? product.getId() : null);
        item.setProductNameSnapshot(product != null ? truncate(product.getName(), 500) : null);
        item.setOriginalUrl(result.originalUrl());
        item.setNormalizedUrl(result.checkedUrl());
        item.setFinalUrl(result.finalUrl());
        item.setVerificationStatus(result.status());
        item.setReason(truncate(result.reason(), MAX_ERROR_LENGTH));
        item.setHttpStatus(result.httpStatus());
        item.setDurationMs(result.durationMs());
        item.setCheckedAt(result.checkedAt());
        item.setTechnicalError(result.technicalError());
        item.setPreviousIsBroken(result.previousIsBroken());
        item.setNewIsBroken(result.isBroken());
        item.setPreviousNeedsReview(result.previousNeedsReview());
        item.setNewNeedsReview(result.needsReview());
        itemRepository.save(item);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRun(
            Long runId,
            LinkValidationRunStatus status,
            LinkValidationRunSummary summary,
            String lastError
    ) {
        LinkValidationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Link validation run not found: " + runId));
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        run.setSelectedCount(summary.selected());
        run.setCheckedCount(summary.checked());
        run.setWorkingCount(summary.working());
        run.setBrokenCount(summary.broken());
        run.setUncertainCount(summary.uncertain());
        run.setBlockedCount(summary.blocked());
        run.setTechnicalErrorCount(summary.technicalErrors());
        run.setLastError(truncate(lastError, MAX_ERROR_LENGTH));
        runRepository.save(run);
    }

    private String resolveTriggeredBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return truncate(principal.getUsername(), 255);
        }
        String name = authentication.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name)
                ? null
                : truncate(name, 255);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
