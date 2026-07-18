package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Video;
import pl.polskaamazonka.backend.model.VideoProduct;
import pl.polskaamazonka.backend.repository.LinkRepository;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.VideoCategoryRepository;
import pl.polskaamazonka.backend.repository.VideoProductRepository;
import pl.polskaamazonka.backend.repository.VideoRepository;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServicePublicCodeLookupTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final String PRODUCT_URL = "https://allegro.pl/oferta/product-123456789";
    private static final String TIKTOK_URL = "https://www.tiktok.com/@test/video/1234567890";
    private static final String NOT_FOUND_MESSAGE = "Film nie istnieje lub link jest nieaktualny.";

    @Mock
    private VideoRepository videoRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private VideoProductRepository videoProductRepository;
    @Mock
    private VideoCategoryRepository videoCategoryRepository;
    @Mock
    private LinkRepository linkRepository;
    @Mock
    private RestTemplate restTemplate;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ProductPageScraperService productPageScraperService;
    @Mock
    private VideoThumbnailStorageService videoThumbnailStorageService;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private ProductNameCleaner productNameCleaner;
    @Mock
    private ActivityLogService activityLogService;
    @Spy
    private QuickProductLinkValidator quickProductLinkValidator = new QuickProductLinkValidator(
            new AllegroUrlNormalizer(),
            new AliExpressUrlNormalizer(),
            new TemuUrlNormalizer(),
            new AmazonUrlNormalizer(),
            new AffiliateShortLinkResolver(
                    url -> {
                        throw new AssertionError("Short link resolver should not be called.");
                    },
                    new AliExpressUrlNormalizer(),
                    new TemuUrlNormalizer()
            )
    );
    @Spy
    private ProductLinkUrlSupport productLinkUrlSupport = new ProductLinkUrlSupport(
            quickProductLinkValidator,
            new AllegroUrlNormalizer(),
            new TemuUrlNormalizer(),
            new AmazonUrlNormalizer()
    );
    @Spy
    private VideoPublicCodeSupport videoPublicCodeSupport = new VideoPublicCodeSupport();

    @InjectMocks
    private VideoService videoService;

    private Video video;
    private VideoProduct relation;

    @BeforeEach
    void setUp() {
        video = activeVideo(VIDEO_ID, "A110");
        relation = workingProductRelation(video, PRODUCT_ID);
        when(videoRepository.findWithProductsByPublicCode("A110")).thenReturn(Optional.of(video));
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation));
        when(videoThumbnailStorageService.isReadableStoredUrl(anyString())).thenReturn(true);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"thumbnail_url\":\"https://thumb.example/video.jpg\"}"));
    }

    @Test
    void getByPublicCodePublicReturnsVideoForExistingCode() {
        PublicVideoDTO result = videoService.getByPublicCodePublic("A110");

        assertNotNull(result);
        assertEquals(VIDEO_ID, result.getId());
        assertEquals("A110", result.getPublicCode());
        assertEquals(1, result.getProducts().size());
    }

    @Test
    void getByPublicCodePublicNormalizesLowercaseCode() {
        PublicVideoDTO result = videoService.getByPublicCodePublic("a110");

        assertEquals("A110", result.getPublicCode());
        verify(videoRepository).findWithProductsByPublicCode("A110");
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForMissingCode() {
        when(videoRepository.findWithProductsByPublicCode("B220")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("B220")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForInvalidFormat() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("110A")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
        verify(videoRepository, never()).findWithProductsByPublicCode(anyString());
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForInactiveVideo() {
        video.setIsActive(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("A110")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundWhenVideoHasNoPublicProducts() {
        Product brokenProduct = product(PRODUCT_ID, "Produkt", PRODUCT_URL);
        brokenProduct.getProductLink().setIsBroken(true);
        relation.setProduct(brokenProduct);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("A110")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForProductNeedingReview() {
        assertBlockedProductReturnsNotFound(link -> link.setNeedsReview(true));
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForInactiveProductLink() {
        assertBlockedProductReturnsNotFound(link -> link.setIsActive(false));
    }

    @Test
    void getByPublicCodePublicReturnsNotFoundForBrokenProductLink() {
        assertBlockedProductReturnsNotFound(link -> link.setIsBroken(true));
    }

    @Test
    void changedPublicCodeDoesNotResolveOldCode() {
        video.setPublicCode("B220");
        when(videoRepository.findWithProductsByPublicCode("A110")).thenReturn(Optional.empty());
        when(videoRepository.findWithProductsByPublicCode("B220")).thenReturn(Optional.of(video));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("A110")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("B220", videoService.getByPublicCodePublic("B220").getPublicCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
    }

    @Test
    void getByPublicCodePublicDoesNotWriteActivityLog() {
        videoService.getByPublicCodePublic("A110");

        verify(activityLogService, never()).logAction(anyString(), anyString());
    }

    @Test
    void promotedPublicListIncludesPublicCode() {
        video.setPromotionStartAt(java.time.Instant.now().minusSeconds(60));
        video.setPromotionEndAt(java.time.Instant.now().plusSeconds(3600));
        when(videoRepository.findAllActivePromoted(any(java.time.Instant.class))).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertEquals(1, result.size());
        assertEquals("A110", result.get(0).getPublicCode());
    }

    @Test
    void adminGetByIdRemainsUnchangedWithoutPublicVisibilityFilter() {
        video.setPublicCode(null);
        video.setIsActive(false);
        when(videoRepository.findWithProductsById(VIDEO_ID)).thenReturn(Optional.of(video));

        VideoDTO result = videoService.getById(VIDEO_ID);

        assertNotNull(result);
        assertEquals(VIDEO_ID, result.getId());
    }

    private void assertBlockedProductReturnsNotFound(java.util.function.Consumer<Link> blocker) {
        blocker.accept(relation.getProduct().getProductLink());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.getByPublicCodePublic("A110")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(NOT_FOUND_MESSAGE, exception.getReason());
    }

    private Video activeVideo(Long id, String publicCode) {
        Video result = new Video();
        result.setId(id);
        result.setTitle("Film " + id);
        result.setTiktokUrl(TIKTOK_URL);
        result.setPreviewImageUrl("/uploads/videos/" + id + ".jpg");
        result.setIsActive(true);
        result.setPublicCode(publicCode);
        return result;
    }

    private VideoProduct workingProductRelation(Video owner, Long productId) {
        VideoProduct videoProduct = new VideoProduct();
        videoProduct.setVideo(owner);
        videoProduct.setProduct(product(productId, "Produkt", PRODUCT_URL));
        return videoProduct;
    }

    private Product product(Long id, String name, String url) {
        Link productLink = new Link();
        productLink.setId(id + 1000);
        productLink.setUrl(url);
        productLink.setType("product");
        productLink.setIsBroken(false);

        Product result = new Product();
        result.setId(id);
        result.setName(name);
        result.setImageUrl("/uploads/products/" + id + ".jpg");
        result.setProductLink(productLink);
        return result;
    }
}
