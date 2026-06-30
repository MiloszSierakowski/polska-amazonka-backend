package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pl.polskaamazonka.backend.config.LinkValidationProperties;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckResponse;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerCheckStatus;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerTechnicalException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkValidatorServiceTest {

    private static final String PRODUCT_URL = "https://allegro.pl/oferta/example-123";
    private static final String VERIFICATION_URL = "https://allegro.pl/oferta/example-123?verified=1";

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private LinkCheckerWorkerClient linkCheckerWorkerClient;

    @Mock
    private ProductLinkUrlSupport productLinkUrlSupport;

    @Mock
    private LinkValidationProperties linkValidationProperties;

    private LinkValidatorService linkValidatorService;

    @BeforeEach
    void setUp() {
        when(linkValidationProperties.getMaxLinksPerRun()).thenReturn(25);
        when(linkValidationProperties.getDelayMsBetweenChecks()).thenReturn(0L);
        when(linkValidationProperties.getMinHoursBetweenChecks()).thenReturn(24);
        when(productLinkUrlSupport.verificationUrlForStored(PRODUCT_URL)).thenReturn(VERIFICATION_URL);
        when(productLinkUrlSupport.verificationUrlForStored("https://allegro.pl/oferta/example-456"))
                .thenReturn("https://allegro.pl/oferta/example-456?verified=1");

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        linkValidatorService = new LinkValidatorService(
                linkRepository,
                linkCheckerWorkerClient,
                productLinkUrlSupport,
                linkValidationProperties,
                transactionManager
        );
    }

    @Test
    void selectLinksForScheduledValidation_usesConfiguredMaxLinksPerRun() {
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        linkValidatorService.selectLinksForScheduledValidation();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(linkRepository).findLinksForScheduledValidation(any(Instant.class), pageableCaptor.capture());
        assertEquals(25, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void selectLinksForScheduledValidation_skipsRecentlyCheckedLinksViaCutoff() {
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        Instant before = Instant.now();
        linkValidatorService.selectLinksForScheduledValidation();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(linkRepository).findLinksForScheduledValidation(cutoffCaptor.capture(), any(Pageable.class));

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedEarliest = before.minus(24, ChronoUnit.HOURS);
        Instant expectedLatest = after.minus(24, ChronoUnit.HOURS);
        assertTrue(!cutoff.isBefore(expectedEarliest) && !cutoff.isAfter(expectedLatest));
    }

    @Test
    void validateAllLinks_processesNeverCheckedProductLink() {
        Link link = productLink(null, false, false);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(link));
        when(linkCheckerWorkerClient.check(VERIFICATION_URL))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.WORKING));

        linkValidatorService.validateAllLinks();

        verify(productLinkUrlSupport).verificationUrlForStored(PRODUCT_URL);
        verify(linkCheckerWorkerClient, times(1)).check(VERIFICATION_URL);
        verify(linkRepository).updateReviewFlags(
                eq(link.getId()),
                eq(false),
                eq(false),
                any(Instant.class)
        );
    }

    @Test
    void validateAllLinks_uncertainResultDoesNotRetry() {
        Link link = productLink(Instant.now().minus(48, ChronoUnit.HOURS), false, true);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(link));
        when(linkCheckerWorkerClient.check(VERIFICATION_URL))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.UNCERTAIN));

        linkValidatorService.validateAllLinks();

        verify(linkCheckerWorkerClient, times(1)).check(VERIFICATION_URL);
        verify(linkRepository).updateReviewFlags(
                eq(link.getId()),
                eq(false),
                eq(true),
                any(Instant.class)
        );
    }

    @Test
    void validateAllLinks_continuesBatchWhenSingleLinkFails() {
        Link failingLink = productLink(null, false, false);
        failingLink.setId(1L);
        Link succeedingLink = productLink(null, false, false);
        succeedingLink.setId(2L);
        succeedingLink.setUrl("https://allegro.pl/oferta/example-456");

        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(failingLink, succeedingLink));
        doThrow(new LinkCheckerWorkerTechnicalException("timeout"))
                .when(linkCheckerWorkerClient)
                .check(VERIFICATION_URL);
        when(linkCheckerWorkerClient.check("https://allegro.pl/oferta/example-456?verified=1"))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.WORKING));

        linkValidatorService.validateAllLinks();

        verify(linkCheckerWorkerClient).check(VERIFICATION_URL);
        verify(linkCheckerWorkerClient).check("https://allegro.pl/oferta/example-456?verified=1");
        verify(linkRepository).updateReviewFlags(eq(failingLink.getId()), eq(false), eq(true), any(Instant.class));
        verify(linkRepository).updateReviewFlags(eq(succeedingLink.getId()), eq(false), eq(false), any(Instant.class));
    }

    @Test
    void validateAllLinks_respectsLowerMaxLinksPerRunFromConfiguration() {
        when(linkValidationProperties.getMaxLinksPerRun()).thenReturn(3);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        linkValidatorService.validateAllLinks();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(linkRepository).findLinksForScheduledValidation(any(Instant.class), pageableCaptor.capture());
        assertEquals(3, pageableCaptor.getValue().getPageSize());
        verifyNoMoreInteractions(linkCheckerWorkerClient);
    }

    @Test
    void executeScheduledLinkValidation_emptyBatchCompletesWithZeroCounts() {
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(0, stats.getSelected());
        assertEquals(0, stats.getChecked());
        assertEquals(0, stats.getWorking());
        assertEquals(0, stats.getBroken());
        assertEquals(0, stats.getUncertain());
        assertEquals(0, stats.getTechnicalErrors());
        assertNotNull(stats.getFinishedAt());
        assertTrue(stats.getDurationMs() >= 0);
    }

    @Test
    void executeScheduledLinkValidation_collectsWorkingBrokenAndUncertainCounts() {
        Link workingLink = productLink(null, false, false);
        workingLink.setId(1L);

        Link brokenLink = productLink(null, false, false);
        brokenLink.setId(2L);
        brokenLink.setUrl("https://allegro.pl/oferta/broken-123");

        Link uncertainLink = productLink(null, false, false);
        uncertainLink.setId(3L);
        uncertainLink.setUrl("https://allegro.pl/oferta/uncertain-123");

        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(workingLink, brokenLink, uncertainLink));
        when(productLinkUrlSupport.verificationUrlForStored(workingLink.getUrl())).thenReturn(workingLink.getUrl());
        when(productLinkUrlSupport.verificationUrlForStored(brokenLink.getUrl())).thenReturn(brokenLink.getUrl());
        when(productLinkUrlSupport.verificationUrlForStored(uncertainLink.getUrl())).thenReturn(uncertainLink.getUrl());
        when(linkCheckerWorkerClient.check(workingLink.getUrl()))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.WORKING));
        when(linkCheckerWorkerClient.check(brokenLink.getUrl()))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.BROKEN));
        when(linkCheckerWorkerClient.check(uncertainLink.getUrl()))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.UNCERTAIN));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(3, stats.getSelected());
        assertEquals(3, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getBroken());
        assertEquals(1, stats.getUncertain());
        assertEquals(0, stats.getTechnicalErrors());
    }

    @Test
    void executeScheduledLinkValidation_mapsBlockedStatusToUncertainStats() {
        Link blockedLink = productLink(null, false, false);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(blockedLink));
        when(linkCheckerWorkerClient.check(VERIFICATION_URL))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.BLOCKED));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(1, stats.getUncertain());
        verify(linkRepository).updateReviewFlags(
                eq(blockedLink.getId()),
                eq(false),
                eq(true),
                any(Instant.class)
        );
    }

    @Test
    void executeScheduledLinkValidation_countsTechnicalErrorWithoutStoppingBatch() {
        Link failingLink = productLink(null, false, false);
        failingLink.setId(1L);
        Link succeedingLink = productLink(null, false, false);
        succeedingLink.setId(2L);
        succeedingLink.setUrl("https://allegro.pl/oferta/example-456");

        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(failingLink, succeedingLink));
        doThrow(new LinkCheckerWorkerTechnicalException("timeout"))
                .when(linkCheckerWorkerClient)
                .check(VERIFICATION_URL);
        when(linkCheckerWorkerClient.check("https://allegro.pl/oferta/example-456?verified=1"))
                .thenReturn(workerResponse(LinkCheckerWorkerCheckStatus.WORKING));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(2, stats.getSelected());
        assertEquals(2, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getTechnicalErrors());
        assertEquals(0, stats.getBroken());
        assertEquals(0, stats.getUncertain());
    }

    @Test
    void executeScheduledLinkValidation_technicalErrorDoesNotMarkLinkAsBroken() {
        Link link = productLink(null, false, false);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(link));
        doThrow(new LinkCheckerWorkerTechnicalException("HTTP 503"))
                .when(linkCheckerWorkerClient)
                .check(VERIFICATION_URL);

        linkValidatorService.executeScheduledLinkValidation();

        verify(linkRepository).updateReviewFlags(
                eq(link.getId()),
                eq(false),
                eq(true),
                any(Instant.class)
        );
    }

    private static LinkCheckerWorkerCheckResponse workerResponse(LinkCheckerWorkerCheckStatus status) {
        return new LinkCheckerWorkerCheckResponse(
                status,
                "test reason",
                "https://example.com/product",
                200,
                Instant.parse("2026-06-28T10:00:00Z")
        );
    }

    private static Link productLink(Instant lastCheckedAt, boolean isBroken, boolean needsReview) {
        Link link = new Link();
        link.setId(10L);
        link.setType("product");
        link.setUrl(PRODUCT_URL);
        link.setIsBroken(isBroken);
        link.setNeedsReview(needsReview);
        link.setLastCheckedAt(lastCheckedAt);
        return link;
    }
}
