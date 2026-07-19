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
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pl.polskaamazonka.backend.config.LinkValidationProperties;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkValidatorServiceTest {

    private static final String PRODUCT_URL = "https://allegro.pl/oferta/example-123";

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private ProductLinkVerificationService productLinkVerificationService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LinkValidationHistoryService linkValidationHistoryService;

    @Mock
    private LinkValidationProperties linkValidationProperties;

    private LinkValidatorService linkValidatorService;

    @BeforeEach
    void setUp() {
        when(linkValidationProperties.getMaxLinksPerRun()).thenReturn(25);
        when(linkValidationProperties.getDelayMsBetweenChecks()).thenReturn(0L);
        when(linkValidationProperties.getMinHoursBetweenChecks()).thenReturn(24);
        when(linkValidationHistoryService.startRun(ProductLinkVerificationSource.SCHEDULED)).thenReturn(100L);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        linkValidatorService = new LinkValidatorService(
                linkRepository,
                productLinkVerificationService,
                productRepository,
                linkValidationHistoryService,
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
        assertTrue(!cutoff.isBefore(before.minus(24, ChronoUnit.HOURS))
                && !cutoff.isAfter(after.minus(24, ChronoUnit.HOURS)));
    }

    @Test
    void scheduledSelectionQueryIncludesOnlyNonBrokenProductLinks() throws Exception {
        Method method = LinkRepository.class.getMethod(
                "findLinksForScheduledValidation",
                Instant.class,
                Pageable.class
        );
        String jpql = method.getAnnotation(Query.class).value();

        assertTrue(jpql.contains("l.type = 'product'"));
        assertTrue(jpql.contains("l.isBroken IS NULL OR l.isBroken = false"));
        assertFalse(jpql.contains("l.isBroken = true"));
        assertTrue(jpql.contains("l.needsReview = true"));
        assertFalse(jpql.contains("l.needsReview = false"));
    }

    @Test
    void validateAllLinks_usesSharedVerificationServiceForProductLink() {
        Link link = productLink(1L);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(link));
        when(productLinkVerificationService.verify(link, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.WORKING));

        linkValidatorService.validateAllLinks();

        verify(productLinkVerificationService).verify(link, ProductLinkVerificationSource.SCHEDULED);
        verify(linkValidationHistoryService).startRun(ProductLinkVerificationSource.SCHEDULED);
        verify(linkValidationHistoryService).recordItem(eq(100L), eq(link), any(), any());
        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.COMPLETED),
                any(),
                eq(null)
        );
    }

    @Test
    void executeScheduledLinkValidation_collectsWorkerResultStatistics() {
        Link working = productLink(1L);
        Link broken = productLink(2L);
        Link uncertain = productLink(3L);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(working, broken, uncertain));
        when(productLinkVerificationService.verify(working, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.WORKING));
        when(productLinkVerificationService.verify(broken, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.BROKEN));
        when(productLinkVerificationService.verify(uncertain, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.BLOCKED));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(3, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getBroken());
        assertEquals(1, stats.getBlocked());
        assertEquals(0, stats.getTechnicalErrors());
    }

    @Test
    void executeScheduledLinkValidation_countsControlledTechnicalFailure() {
        Link link = productLink(1L);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(link));
        when(productLinkVerificationService.verify(link, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.TECHNICAL_ERROR));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(1, stats.getChecked());
        assertEquals(1, stats.getTechnicalErrors());
        assertEquals(0, stats.getBroken());
        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.COMPLETED_WITH_ERRORS),
                any(),
                eq(ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE)
        );
    }

    @Test
    void executeScheduledLinkValidation_continuesWhenSingleLinkThrowsUnexpectedException() {
        Link failing = productLink(1L);
        Link succeeding = productLink(2L);
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new IllegalStateException("database failure"))
                .when(productLinkVerificationService)
                .verify(failing, ProductLinkVerificationSource.SCHEDULED);
        when(productLinkVerificationService.verify(succeeding, ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(result(ProductLinkVerificationStatus.WORKING));

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        verify(productLinkVerificationService).verify(succeeding, ProductLinkVerificationSource.SCHEDULED);
        assertEquals(2, stats.getChecked());
        assertEquals(1, stats.getWorking());
        assertEquals(1, stats.getTechnicalErrors());
    }

    @Test
    void executeScheduledLinkValidation_emptyBatchCompletesWithZeroCounts() {
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        ScheduledLinkValidationStats stats = linkValidatorService.executeScheduledLinkValidation();

        assertEquals(0, stats.getChecked());
        assertNotNull(stats.getFinishedAt());
        assertTrue(stats.getDurationMs() >= 0);
    }

    @Test
    void executeScheduledLinkValidation_marksRunFailedWhenSelectionFails() {
        when(linkRepository.findLinksForScheduledValidation(any(Instant.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("selection failed"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> linkValidatorService.executeScheduledLinkValidation()
        );

        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.FAILED),
                any(),
                eq("Nie udało się wykonać zaplanowanej partii weryfikacji.")
        );
    }

    private static ProductLinkVerificationResult result(ProductLinkVerificationStatus status) {
        boolean broken = status == ProductLinkVerificationStatus.BROKEN;
        boolean review = status == ProductLinkVerificationStatus.UNCERTAIN
                || status == ProductLinkVerificationStatus.BLOCKED
                || status == ProductLinkVerificationStatus.TECHNICAL_ERROR;
        return new ProductLinkVerificationResult(
                status,
                ProductLinkVerificationSource.SCHEDULED,
                broken,
                review,
                Instant.now(),
                25L,
                PRODUCT_URL,
                PRODUCT_URL,
                null,
                null,
                null,
                status == ProductLinkVerificationStatus.TECHNICAL_ERROR,
                false,
                false,
                null
        );
    }

    private static Link productLink(Long id) {
        Link link = new Link();
        link.setId(id);
        link.setType("product");
        link.setUrl(PRODUCT_URL);
        link.setIsBroken(false);
        link.setNeedsReview(false);
        return link;
    }
}
