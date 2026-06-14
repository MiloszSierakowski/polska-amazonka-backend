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
import pl.polskaamazonka.backend.service.scraper.ProductLinkAvailability;

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

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private ProductPageScraperService productPageScraperService;

    @Mock
    private LinkValidationProperties linkValidationProperties;

    private LinkValidatorService linkValidatorService;

    @BeforeEach
    void setUp() {
        when(linkValidationProperties.getMaxLinksPerRun()).thenReturn(25);
        when(linkValidationProperties.getDelayMsBetweenChecks()).thenReturn(0L);
        when(linkValidationProperties.getMinHoursBetweenChecks()).thenReturn(24);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        linkValidatorService = new LinkValidatorService(
                linkRepository,
                productPageScraperService,
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
        when(productPageScraperService.evaluateProductLinkAvailability(PRODUCT_URL))
                .thenReturn(ProductLinkAvailability.WORKING);

        linkValidatorService.validateAllLinks();

        verify(productPageScraperService, times(1)).evaluateProductLinkAvailability(PRODUCT_URL);
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
        when(productPageScraperService.evaluateProductLinkAvailability(PRODUCT_URL))
                .thenReturn(ProductLinkAvailability.UNCERTAIN);

        linkValidatorService.validateAllLinks();

        verify(productPageScraperService, times(1)).evaluateProductLinkAvailability(PRODUCT_URL);
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
        doThrow(new RuntimeException("timeout"))
                .when(productPageScraperService)
                .evaluateProductLinkAvailability(failingLink.getUrl());
        when(productPageScraperService.evaluateProductLinkAvailability(succeedingLink.getUrl()))
                .thenReturn(ProductLinkAvailability.WORKING);

        linkValidatorService.validateAllLinks();

        verify(productPageScraperService).evaluateProductLinkAvailability(failingLink.getUrl());
        verify(productPageScraperService).evaluateProductLinkAvailability(succeedingLink.getUrl());
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
        verifyNoMoreInteractions(productPageScraperService);
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
        when(productPageScraperService.evaluateProductLinkAvailability(workingLink.getUrl()))
                .thenReturn(ProductLinkAvailability.WORKING);
        when(productPageScraperService.evaluateProductLinkAvailability(brokenLink.getUrl()))
                .thenReturn(ProductLinkAvailability.BROKEN);
        when(productPageScraperService.evaluateProductLinkAvailability(uncertainLink.getUrl()))
                .thenReturn(ProductLinkAvailability.UNCERTAIN);

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(3, stats.getSelected());
        assertEquals(3, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getBroken());
        assertEquals(1, stats.getUncertain());
        assertEquals(0, stats.getTechnicalErrors());
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
        doThrow(new RuntimeException("timeout"))
                .when(productPageScraperService)
                .evaluateProductLinkAvailability(failingLink.getUrl());
        when(productPageScraperService.evaluateProductLinkAvailability(succeedingLink.getUrl()))
                .thenReturn(ProductLinkAvailability.WORKING);

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(2, stats.getSelected());
        assertEquals(2, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getTechnicalErrors());
        assertEquals(0, stats.getBroken());
        assertEquals(0, stats.getUncertain());
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
