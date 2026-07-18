package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckResponse;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckStatus;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerTechnicalException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLinkVerificationServiceTest {

    private static final String STORED_URL = " https://allegro.pl/oferta/example-123 ";
    private static final String NORMALIZED_URL = "https://allegro.pl/oferta/example-123?verified=1";

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private ProductLinkUrlSupport productLinkUrlSupport;

    @Mock
    private LinkCheckerWorkerClient linkCheckerWorkerClient;

    private ProductLinkVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ProductLinkVerificationService(linkRepository, productLinkUrlSupport, linkCheckerWorkerClient);
        when(productLinkUrlSupport.verificationUrlForStored(STORED_URL.trim())).thenReturn(NORMALIZED_URL);
    }

    @Test
    void workingSetsFalseFalseAndUsesNormalizedUrl() {
        Link link = link(true);
        when(linkCheckerWorkerClient.check(NORMALIZED_URL)).thenReturn(response(LinkCheckerWorkerCheckStatus.WORKING));

        ProductLinkVerificationResult result = service.verify(link, ProductLinkVerificationSource.MANUAL);

        assertEquals(ProductLinkVerificationStatus.WORKING, result.status());
        assertEquals(Boolean.TRUE, result.previousIsBroken());
        assertEquals(Boolean.FALSE, result.previousNeedsReview());
        assertTrue(result.durationMs() >= 0L);
        assertFalse(result.isBroken());
        assertFalse(result.needsReview());
        verify(linkCheckerWorkerClient).check(NORMALIZED_URL);
        verifyPersisted(link, false, false);
    }

    @Test
    void brokenSetsTrueFalse() {
        Link link = link(false);
        when(linkCheckerWorkerClient.check(NORMALIZED_URL)).thenReturn(response(LinkCheckerWorkerCheckStatus.BROKEN));

        ProductLinkVerificationResult result = service.verify(link, ProductLinkVerificationSource.SCHEDULED);

        assertEquals(ProductLinkVerificationStatus.BROKEN, result.status());
        verifyPersisted(link, true, false);
    }

    @Test
    void uncertainPreservesBrokenAndRequiresReview() {
        Link link = link(true);
        when(linkCheckerWorkerClient.check(NORMALIZED_URL)).thenReturn(response(LinkCheckerWorkerCheckStatus.UNCERTAIN));

        ProductLinkVerificationResult result = service.verify(link, ProductLinkVerificationSource.MANUAL);

        assertEquals(ProductLinkVerificationStatus.UNCERTAIN, result.status());
        verifyPersisted(link, true, true);
    }

    @Test
    void blockedPreservesWorkingStateAndRequiresReview() {
        Link link = link(false);
        when(linkCheckerWorkerClient.check(NORMALIZED_URL)).thenReturn(response(LinkCheckerWorkerCheckStatus.BLOCKED));

        ProductLinkVerificationResult result = service.verify(link, ProductLinkVerificationSource.MANUAL);

        assertEquals(ProductLinkVerificationStatus.BLOCKED, result.status());
        verifyPersisted(link, false, true);
    }

    @Test
    void technicalFailurePreservesBrokenAndReturnsControlledResult() {
        Link link = link(true);
        when(linkCheckerWorkerClient.check(NORMALIZED_URL))
                .thenThrow(new LinkCheckerWorkerTechnicalException("worker offline"));

        ProductLinkVerificationResult result = service.verify(link, ProductLinkVerificationSource.MANUAL);

        assertEquals(ProductLinkVerificationStatus.TECHNICAL_ERROR, result.status());
        assertEquals(ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE, result.message());
        assertTrue(result.technicalError());
        assertTrue(result.isUncertain());
        verifyPersisted(link, true, true);
    }

    private void verifyPersisted(Link link, boolean isBroken, boolean needsReview) {
        ArgumentCaptor<Instant> checkedAt = ArgumentCaptor.forClass(Instant.class);
        verify(linkRepository).updateReviewFlags(eq(link.getId()), eq(isBroken), eq(needsReview), checkedAt.capture());
        assertNotNull(checkedAt.getValue());
        assertEquals(checkedAt.getValue(), link.getLastCheckedAt());
        assertEquals(isBroken, link.getIsBroken());
        assertEquals(needsReview, link.getNeedsReview());
    }

    private static LinkCheckerWorkerCheckResponse response(LinkCheckerWorkerCheckStatus status) {
        return new LinkCheckerWorkerCheckResponse(
                status,
                "reason",
                "https://example.com/final",
                200,
                Instant.parse("2026-07-18T10:00:00Z")
        );
    }

    private static Link link(boolean isBroken) {
        Link link = new Link();
        link.setId(10L);
        link.setUrl(STORED_URL);
        link.setType("product");
        link.setIsBroken(isBroken);
        link.setNeedsReview(false);
        return link;
    }
}
