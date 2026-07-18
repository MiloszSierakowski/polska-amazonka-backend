package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.ProductLinkVerifyResultDTO;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Video;
import pl.polskaamazonka.backend.model.VideoProduct;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.VideoCategoryRepository;
import pl.polskaamazonka.backend.repository.VideoProductRepository;
import pl.polskaamazonka.backend.repository.VideoRepository;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceVerifyProductLinkTest {

    @Mock private VideoRepository videoRepository;
    @Mock private ProductRepository productRepository;
    @Mock private VideoProductRepository videoProductRepository;
    @Mock private VideoCategoryRepository videoCategoryRepository;
    @Mock private LinkRepository linkRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ProductPageScraperService productPageScraperService;
    @Mock private VideoThumbnailStorageService videoThumbnailStorageService;
    @Mock private ProductImageStorageService productImageStorageService;
    @Mock private ProductNameCleaner productNameCleaner;
    @Mock private ActivityLogService activityLogService;
    @Mock private ProductLinkUrlSupport productLinkUrlSupport;
    @Mock private ProductLinkVerificationService productLinkVerificationService;
    @Mock private LinkValidationHistoryService linkValidationHistoryService;
    @Mock private VideoPublicCodeSupport videoPublicCodeSupport;

    @InjectMocks
    private VideoService videoService;

    @org.junit.jupiter.api.BeforeEach
    void setUpHistory() {
        when(linkValidationHistoryService.startRun(ProductLinkVerificationSource.MANUAL)).thenReturn(100L);
    }

    @Test
    void manualVerificationUsesSharedService() {
        Fixture fixture = fixture();
        ProductLinkVerificationResult verification = result(ProductLinkVerificationStatus.WORKING, false, false, null);
        when(productLinkVerificationService.verify(fixture.link(), ProductLinkVerificationSource.MANUAL))
                .thenReturn(verification);

        ProductLinkVerifyResultDTO dto = videoService.verifyProductLink(1L, 2L);

        verify(productLinkVerificationService).verify(fixture.link(), ProductLinkVerificationSource.MANUAL);
        verify(linkValidationHistoryService).startRun(ProductLinkVerificationSource.MANUAL);
        verify(linkValidationHistoryService).recordItem(eq(100L), eq(fixture.link()), any(Product.class), eq(verification));
        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.COMPLETED),
                any(),
                eq(null)
        );
        assertTrue(dto.getLinkWorking());
        assertEquals("WORKING", dto.getVerificationStatus());
    }

    @Test
    void technicalWorkerFailureReturnsControlledResult() {
        Fixture fixture = fixture();
        when(productLinkVerificationService.verify(fixture.link(), ProductLinkVerificationSource.MANUAL))
                .thenReturn(result(
                        ProductLinkVerificationStatus.TECHNICAL_ERROR,
                        false,
                        true,
                        ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE
                ));

        ProductLinkVerifyResultDTO dto = videoService.verifyProductLink(1L, 2L);

        assertFalse(dto.getLinkWorking());
        assertTrue(dto.getVerificationUncertain());
        assertEquals("TECHNICAL_ERROR", dto.getVerificationStatus());
        assertEquals(ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE, dto.getVerificationMessage());
        verify(productPageScraperService, never()).scrape(org.mockito.ArgumentMatchers.anyString());
        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.COMPLETED_WITH_ERRORS),
                any(),
                eq(ProductLinkVerificationService.TECHNICAL_ERROR_MESSAGE)
        );
    }

    @Test
    void missingVideoStillReturnsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.verifyProductLink(1L, 2L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(linkValidationHistoryService).finishRun(
                eq(100L),
                eq(LinkValidationRunStatus.FAILED),
                any(),
                eq("Nie udało się wykonać ręcznej weryfikacji.")
        );
    }

    @Test
    void missingProductRelationStillReturnsNotFound() {
        when(videoRepository.findById(1L)).thenReturn(Optional.of(new Video()));
        when(videoProductRepository.findByVideo_IdAndProduct_IdWithLink(1L, 2L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.verifyProductLink(1L, 2L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void missingLinkKeepsBadRequestBehavior() {
        VideoProduct relation = new VideoProduct();
        relation.setProduct(new Product());
        when(videoRepository.findById(1L)).thenReturn(Optional.of(new Video()));
        when(videoProductRepository.findByVideo_IdAndProduct_IdWithLink(1L, 2L))
                .thenReturn(Optional.of(relation));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.verifyProductLink(1L, 2L)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private Fixture fixture() {
        Link link = new Link();
        link.setId(10L);
        link.setUrl("https://example.com/product");
        Product product = new Product();
        product.setId(2L);
        product.setName("Product");
        product.setProductLink(link);
        VideoProduct relation = new VideoProduct();
        relation.setProduct(product);
        when(videoRepository.findById(1L)).thenReturn(Optional.of(new Video()));
        when(videoProductRepository.findByVideo_IdAndProduct_IdWithLink(1L, 2L))
                .thenReturn(Optional.of(relation));
        return new Fixture(link);
    }

    private static ProductLinkVerificationResult result(
            ProductLinkVerificationStatus status,
            boolean isBroken,
            boolean needsReview,
            String message
    ) {
        return new ProductLinkVerificationResult(
                status,
                ProductLinkVerificationSource.MANUAL,
                isBroken,
                needsReview,
                Instant.now(),
                25L,
                "https://example.com/product",
                null,
                null,
                null,
                null,
                status == ProductLinkVerificationStatus.TECHNICAL_ERROR,
                false,
                false,
                message
        );
    }

    private record Fixture(Link link) {
    }
}
