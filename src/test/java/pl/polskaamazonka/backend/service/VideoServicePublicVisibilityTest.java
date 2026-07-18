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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServicePublicVisibilityTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final String PRODUCT_URL = "https://allegro.pl/oferta/product-123456789";
    private static final String TIKTOK_URL = "https://www.tiktok.com/@test/video/1234567890";

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
        video = activeVideo(VIDEO_ID, null);
        relation = workingProductRelation(video, PRODUCT_ID);

        when(videoRepository.findWithProductsById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation));
        when(videoThumbnailStorageService.isReadableStoredUrl(anyString())).thenReturn(true);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"thumbnail_url\":\"https://thumb.example/video.jpg\"}"));
    }

    @Test
    void activeVideoWithoutPublicCodeIsExcludedFromRegularPublicList() {
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void promotedVideoWithoutPublicCodeIsExcludedFromPromotedPublicList() {
        Video promoted = activeVideo(2L, null);
        promoted.setPromotionStartAt(Instant.now().minusSeconds(60));
        promoted.setPromotionEndAt(Instant.now().plusSeconds(3600));
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(promoted));
        when(videoProductRepository.findByVideo_Id(2L)).thenReturn(List.of(workingProductRelation(promoted, 20L)));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertTrue(result.isEmpty());
    }

    @Test
    void videoWithoutPublicCodeIsExcludedFromCategoryFilteredPublicList() {
        when(videoRepository.findAllByCategoryId(7L)).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPublic(7L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getByIdPublicDoesNotReturnVideoWithoutPublicCode() {
        PublicVideoDTO result = videoService.getByIdPublic(VIDEO_ID);

        assertNull(result);
    }

    @Test
    void videoWithValidPublicCodeIsReturnedPublicly() {
        video.setPublicCode("A110");

        PublicVideoDTO result = videoService.getByIdPublic(VIDEO_ID);

        assertNotNull(result);
        assertEquals(VIDEO_ID, result.getId());
        assertEquals("A110", result.getPublicCode());
    }

    @Test
    void adminGetByIdStillReturnsVideoWithoutPublicCode() {
        VideoDTO result = videoService.getById(VIDEO_ID);

        assertNotNull(result);
        assertEquals(VIDEO_ID, result.getId());
    }

    @Test
    void whitespaceOnlyPublicCodeIsTreatedAsMissingForPublicApi() {
        video.setPublicCode("   ");

        assertNull(videoService.getByIdPublic(VIDEO_ID));
        assertTrue(videoService.getAllPublic(null).isEmpty());
    }

    @Test
    void inactiveVideoWithPublicCodeRemainsHiddenFromPublicList() {
        video.setPublicCode("A110");
        video.setIsActive(false);
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void publicListWithPublicCodeStillRequiresWorkingProduct() {
        video.setPublicCode("A110");
        Product brokenProduct = product(PRODUCT_ID, "Produkt", PRODUCT_URL);
        brokenProduct.getProductLink().setIsBroken(true);
        relation.setProduct(brokenProduct);
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void publicListReturnsVideoWithPublicCodeAndWorkingProduct() {
        video.setPublicCode("A110");
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(video));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertEquals(List.of(VIDEO_ID), result.stream().map(PublicVideoDTO::getId).toList());
        assertEquals("A110", result.get(0).getPublicCode());
        assertFalse(result.get(0).getProducts().isEmpty());
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
