package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.LinkValidationRun;
import pl.polskaamazonka.backend.model.LinkValidationRunItem;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.repository.LinkValidationRunItemRepository;
import pl.polskaamazonka.backend.repository.LinkValidationRunRepository;
import pl.polskaamazonka.backend.security.UserPrincipal;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkValidationHistoryServiceTest {

    @Mock private LinkValidationRunRepository runRepository;
    @Mock private LinkValidationRunItemRepository itemRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startManualRunStoresAuthenticatedUser() {
        User user = new User();
        user.setId(7L);
        user.setLogin("administrator");
        user.setPasswordHash("hash");
        user.setRole(UserRole.ADMIN);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        when(runRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            LinkValidationRun run = invocation.getArgument(0);
            run.setId(10L);
            return run;
        });
        LinkValidationHistoryService service = service();

        Long id = service.startRun(ProductLinkVerificationSource.MANUAL);

        ArgumentCaptor<LinkValidationRun> captor = ArgumentCaptor.forClass(LinkValidationRun.class);
        verify(runRepository).save(captor.capture());
        assertEquals(10L, id);
        assertEquals("administrator", captor.getValue().getTriggeredBy());
        assertEquals(LinkValidationRunStatus.RUNNING, captor.getValue().getStatus());
    }

    @Test
    void startManualRunAllowsMissingUser() {
        when(runRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            LinkValidationRun run = invocation.getArgument(0);
            run.setId(10L);
            return run;
        });

        service().startRun(ProductLinkVerificationSource.MANUAL);

        ArgumentCaptor<LinkValidationRun> captor = ArgumentCaptor.forClass(LinkValidationRun.class);
        verify(runRepository).save(captor.capture());
        assertNull(captor.getValue().getTriggeredBy());
    }

    @Test
    void recordItemStoresWorkerDataAndFlagSnapshots() {
        Link link = new Link();
        link.setId(20L);
        Product product = new Product();
        product.setId(30L);
        product.setName("Produkt testowy");
        ProductLinkVerificationResult result = result(ProductLinkVerificationStatus.BLOCKED, false, true);

        service().recordItem(10L, link, product, result);

        ArgumentCaptor<LinkValidationRunItem> captor = ArgumentCaptor.forClass(LinkValidationRunItem.class);
        verify(itemRepository).save(captor.capture());
        LinkValidationRunItem item = captor.getValue();
        assertEquals(20L, item.getLinkId());
        assertEquals(30L, item.getProductId());
        assertEquals("Produkt testowy", item.getProductNameSnapshot());
        assertEquals(ProductLinkVerificationStatus.BLOCKED, item.getVerificationStatus());
        assertEquals("reason", item.getReason());
        assertEquals(403, item.getHttpStatus());
        assertEquals(125L, item.getDurationMs());
        assertEquals(Boolean.TRUE, item.getPreviousIsBroken());
        assertEquals(Boolean.FALSE, item.getNewIsBroken());
        assertEquals(Boolean.FALSE, item.getPreviousNeedsReview());
        assertEquals(Boolean.TRUE, item.getNewNeedsReview());
        assertFalse(item.isTechnicalError());
    }

    @Test
    void recordItemMarksTechnicalError() {
        ProductLinkVerificationResult result = result(ProductLinkVerificationStatus.TECHNICAL_ERROR, true, true);

        service().recordItem(10L, new Link(), null, result);

        ArgumentCaptor<LinkValidationRunItem> captor = ArgumentCaptor.forClass(LinkValidationRunItem.class);
        verify(itemRepository).save(captor.capture());
        assertTrue(captor.getValue().isTechnicalError());
    }

    @Test
    void recordItemPersistsEveryVerificationStatus() {
        LinkValidationHistoryService service = service();
        for (ProductLinkVerificationStatus status : ProductLinkVerificationStatus.values()) {
            service.recordItem(10L, new Link(), null, result(status, false, status != ProductLinkVerificationStatus.WORKING));
        }

        ArgumentCaptor<LinkValidationRunItem> captor = ArgumentCaptor.forClass(LinkValidationRunItem.class);
        verify(itemRepository, times(ProductLinkVerificationStatus.values().length)).save(captor.capture());
        assertEquals(
                java.util.Arrays.asList(ProductLinkVerificationStatus.values()),
                captor.getAllValues().stream().map(LinkValidationRunItem::getVerificationStatus).toList()
        );
    }

    @Test
    void finishRunStoresAllCountersForCompletedWithErrors() {
        LinkValidationRun run = new LinkValidationRun();
        run.setId(10L);
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        LinkValidationRunSummary summary = new LinkValidationRunSummary(5, 5, 1, 1, 1, 1, 1);

        service().finishRun(10L, LinkValidationRunStatus.COMPLETED_WITH_ERRORS, summary, "safe error");

        assertEquals(LinkValidationRunStatus.COMPLETED_WITH_ERRORS, run.getStatus());
        assertEquals(5, run.getSelectedCount());
        assertEquals(1, run.getWorkingCount());
        assertEquals(1, run.getBrokenCount());
        assertEquals(1, run.getUncertainCount());
        assertEquals(1, run.getBlockedCount());
        assertEquals(1, run.getTechnicalErrorCount());
        assertEquals("safe error", run.getLastError());
        assertTrue(run.getFinishedAt() != null);
    }

    private LinkValidationHistoryService service() {
        return new LinkValidationHistoryService(runRepository, itemRepository);
    }

    private static ProductLinkVerificationResult result(
            ProductLinkVerificationStatus status,
            boolean isBroken,
            boolean needsReview
    ) {
        return new ProductLinkVerificationResult(
                status,
                ProductLinkVerificationSource.MANUAL,
                isBroken,
                needsReview,
                Instant.parse("2026-07-18T10:00:00Z"),
                125L,
                "https://example.com/original",
                "https://example.com/normalized",
                "reason",
                "https://example.com/final",
                403,
                status == ProductLinkVerificationStatus.TECHNICAL_ERROR,
                true,
                false,
                null
        );
    }
}
