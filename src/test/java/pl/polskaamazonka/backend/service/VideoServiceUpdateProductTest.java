package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.VideoDTO;
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
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceUpdateProductTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long LINK_ID = 100L;
    private static final String OLD_URL = "https://allegro.pl/oferta/old-product-123456789";
    private static final String NEW_ALLEGRO_URL = "https://allegro.pl/oferta/new-product-987654321?utm_source=abc";
    private static final String NEW_ALIEXPRESS_URL = "https://www.aliexpress.com/item/1005001234567890.html?spm=a2g0o";

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
    @Mock
    private ObjectMapper objectMapper;
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
                        throw new AssertionError("Short link resolver should not be called for full product URLs.");
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

    @InjectMocks
    @Spy
    private VideoService videoService;

    private Link link;
    private Product product;
    private VideoProduct relation;

    @BeforeEach
    void setUp() {
        link = new Link();
        link.setId(LINK_ID);
        link.setUrl(OLD_URL);
        link.setType("product");
        link.setIsBroken(false);
        link.setNeedsReview(false);
        link.setLastCheckedAt(Instant.parse("2025-01-01T00:00:00Z"));

        product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Old name");
        product.setImageUrl("https://example.com/old-image.jpg");
        product.setProductLink(link);

        Video video = new Video();
        video.setId(VIDEO_ID);

        relation = new VideoProduct();
        relation.setVideo(video);
        relation.setProduct(product);

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoProductRepository.findByVideo_IdAndProduct_Id(VIDEO_ID, PRODUCT_ID))
                .thenReturn(Optional.of(relation));
    }

    @Test
    void updateProduct_whenUrlUnchanged_preservesMetadataAndReviewFlags() {
        link.setIsBroken(true);
        link.setNeedsReview(false);
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto(OLD_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("Old name", product.getName());
        assertEquals("https://example.com/old-image.jpg", product.getImageUrl());
        assertEquals(OLD_URL, link.getUrl());
        assertTrue(link.getIsBroken());
        assertFalse(link.getNeedsReview());
        verify(linkRepository, never()).updateReviewFlags(anyLong(), anyBoolean(), anyBoolean(), any());
        verify(productLinkUrlSupport, never()).validateProductUrl(anyString());
        verify(productPageScraperService, never()).scrape(anyString());
    }

    @Test
    void updateProduct_whenUrlUnchanged_allowsExplicitMetadataUpdate() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto(OLD_URL);
        dto.setName("Updated name");
        dto.setImageUrl("https://example.com/updated-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("Updated name", product.getName());
        assertEquals("https://example.com/updated-image.jpg", product.getImageUrl());
        assertEquals(OLD_URL, link.getUrl());
        verify(productLinkUrlSupport, never()).validateProductUrl(anyString());
        verify(productPageScraperService, never()).scrape(anyString());
    }

    @Test
    void updateProduct_whenPromoCodeChanges_urlRemainsUnchanged() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto(OLD_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");
        dto.setPromoCode("PROMO2025");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals(OLD_URL, link.getUrl());
        assertEquals("PROMO2025", relation.getPromoCode());
        verify(productLinkUrlSupport, never()).validateProductUrl(anyString());
        verify(productPageScraperService, never()).scrape(anyString());
    }

    @Test
    void updateProduct_whenUrlChangedToValidAllegro_storesSubmittedUrlAndUsesVerificationUrlForScraper() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(new ProductPageData("Scraped title", null));
        when(productNameCleaner.isWeakScrapedName("Scraped title", "https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(false);
        when(productNameCleaner.fallbackFromUrl("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn("allegro.pl");
        when(productImageStorageService.ensureDefaultImage()).thenReturn("/images/default-product.png");

        ProductDTO dto = productDto(NEW_ALLEGRO_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals(NEW_ALLEGRO_URL, link.getUrl());
        assertFalse(link.getIsBroken());
        assertTrue(link.getNeedsReview());
        verify(productLinkUrlSupport).validateProductUrl(NEW_ALLEGRO_URL.trim());
        verify(productPageScraperService).scrape("https://allegro.pl/oferta/new-product-987654321");
    }

    @Test
    void updateProduct_whenUrlChangedToValidAliExpress_storesFullUrlWithQueryParams() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape("https://www.aliexpress.com/item/1005001234567890.html"))
                .thenReturn(new ProductPageData("AliExpress title", "https://img.example/ae.jpg"));
        when(productNameCleaner.isWeakScrapedName("AliExpress title", "https://www.aliexpress.com/item/1005001234567890.html"))
                .thenReturn(false);
        when(productNameCleaner.fallbackFromUrl("https://www.aliexpress.com/item/1005001234567890.html"))
                .thenReturn("aliexpress.com");
        when(productImageStorageService.tryStoreFromRemoteUrl("https://img.example/ae.jpg", "https://www.aliexpress.com/item/1005001234567890.html"))
                .thenReturn("/stored/ae.jpg");

        ProductDTO dto = productDto(NEW_ALIEXPRESS_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals(NEW_ALIEXPRESS_URL, link.getUrl());
        assertTrue(link.getNeedsReview());
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_whenUrlChangedWithExplicitMetadata_usesDtoValuesAndMarksForReview() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.resolveProductName(
                eq("https://allegro.pl/oferta/new-product-987654321"),
                eq("New explicit name")
        )).thenReturn("New explicit name");
        when(productImageStorageService.isBrowserDisplayableRemoteUrl("https://example.com/new-image.jpg"))
                .thenReturn(true);

        ProductDTO dto = productDto(NEW_ALLEGRO_URL);
        dto.setName("New explicit name");
        dto.setImageUrl("https://example.com/new-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("New explicit name", product.getName());
        assertEquals("https://example.com/new-image.jpg", product.getImageUrl());
        assertEquals(NEW_ALLEGRO_URL, link.getUrl());
        assertTrue(link.getNeedsReview());
        verify(productPageScraperService, never()).scrape(anyString());
        verify(productPageScraperService).resolveProductName(
                "https://allegro.pl/oferta/new-product-987654321",
                "New explicit name"
        );
    }

    @Test
    void updateProduct_whenUrlChangedWithoutExplicitMetadata_usesScrapedMetadata() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(new ProductPageData("Scraped title", "https://img.example/scraped.jpg"));
        when(productNameCleaner.isWeakScrapedName("Scraped title", "https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(false);
        when(productNameCleaner.fallbackFromUrl("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn("allegro.pl");
        when(productImageStorageService.tryStoreFromRemoteUrl(
                "https://img.example/scraped.jpg",
                "https://allegro.pl/oferta/new-product-987654321"
        )).thenReturn("/stored/scraped.jpg");

        ProductDTO dto = productDto(NEW_ALLEGRO_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("Scraped title", product.getName());
        assertEquals("/stored/scraped.jpg", product.getImageUrl());
        assertEquals(NEW_ALLEGRO_URL, link.getUrl());
        assertTrue(link.getNeedsReview());
        verify(productPageScraperService).scrape("https://allegro.pl/oferta/new-product-987654321");
        verify(productPageScraperService, never()).evaluateProductLinkAvailability(anyString());
    }

    @Test
    void updateProduct_whenUrlChangedAndScrapeFails_clearsMisleadingMetadata() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(new ProductPageData("allegro.pl", null));
        when(productNameCleaner.isWeakScrapedName("allegro.pl", "https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(true);
        when(productNameCleaner.nameFromUrlSlug("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn("new product");
        when(productNameCleaner.clean("new product")).thenReturn("new product");
        when(productImageStorageService.ensureDefaultImage()).thenReturn("/images/default-product.png");

        ProductDTO dto = productDto(NEW_ALLEGRO_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("[Do weryfikacji] new product", product.getName());
        assertEquals("/images/default-product.png", product.getImageUrl());
        assertEquals(NEW_ALLEGRO_URL, link.getUrl());
        assertTrue(link.getNeedsReview());
        assertFalse(link.getIsBroken());
    }

    @Test
    void updateProduct_whenOnlyTrackingParamsChange_treatsAsUrlChangeAndStoresExactSubmittedUrl() {
        product.setName("Allegro product");
        product.setImageUrl("https://example.com/allegro-image.jpg");
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape(OLD_URL))
                .thenReturn(new ProductPageData("Allegro product", "https://example.com/allegro-image.jpg"));
        when(productNameCleaner.isWeakScrapedName("Allegro product", OLD_URL))
                .thenReturn(false);
        when(productNameCleaner.fallbackFromUrl(OLD_URL))
                .thenReturn("allegro.pl");
        when(productImageStorageService.tryStoreFromRemoteUrl(anyString(), eq(OLD_URL)))
                .thenReturn("https://example.com/allegro-image.jpg");

        String urlWithTracking = OLD_URL + "?utm_source=test&ref=abc";
        ProductDTO dto = productDto(urlWithTracking);
        dto.setName("Allegro product");
        dto.setImageUrl("https://example.com/allegro-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals(urlWithTracking, link.getUrl());
        assertTrue(link.getNeedsReview());
        verify(productLinkUrlSupport).validateProductUrl(urlWithTracking.trim());
        verify(productPageScraperService).scrape(OLD_URL);
    }

    @Test
    void updateProduct_whenUrlChangedToAllegroListingIsRejected() {
        link.setIsBroken(true);
        link.setNeedsReview(false);

        ProductDTO dto = productDto("https://allegro.pl/listing?string=telefon");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(OLD_URL, link.getUrl());
        assertTrue(link.getIsBroken());
        assertFalse(link.getNeedsReview());
        verify(linkRepository, never()).updateReviewFlags(anyLong(), anyBoolean(), anyBoolean(), any());
        verify(linkRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_whenUrlChangedToAmazonHomepageIsRejected() {
        ProductDTO dto = productDto("https://www.amazon.pl");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(OLD_URL, link.getUrl());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void updateProduct_whenUrlChangedToUnsupportedPlatformIsRejected() {
        ProductDTO dto = productDto("https://www.ebay.com/itm/123");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "Nieobsługiwana platforma. Obsługiwane platformy: Allegro, AliExpress, Temu, Amazon.",
                exception.getReason()
        );
        assertEquals(OLD_URL, link.getUrl());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void updateProduct_whenUrlChangesFromBroken_doesNotMarkAsFullyWorking() {
        link.setIsBroken(true);
        link.setNeedsReview(false);
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        when(productPageScraperService.scrape("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(new ProductPageData("Scraped title", "https://img.example/scraped.jpg"));
        when(productNameCleaner.isWeakScrapedName("Scraped title", "https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn(false);
        when(productNameCleaner.fallbackFromUrl("https://allegro.pl/oferta/new-product-987654321"))
                .thenReturn("allegro.pl");
        when(productImageStorageService.tryStoreFromRemoteUrl(anyString(), eq("https://allegro.pl/oferta/new-product-987654321")))
                .thenReturn("/stored/scraped.jpg");

        ProductDTO dto = productDto(NEW_ALLEGRO_URL);
        dto.setName("Old name");
        dto.setImageUrl("https://example.com/old-image.jpg");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertFalse(link.getIsBroken());
        assertTrue(link.getNeedsReview());
        verify(linkRepository).updateReviewFlags(eq(LINK_ID), eq(false), eq(true), any(Instant.class));
    }

    private ProductDTO productDto(String url) {
        ProductDTO dto = new ProductDTO();
        LinkDTO productLink = new LinkDTO();
        productLink.setUrl(url);
        productLink.setType("product");
        dto.setProductLink(productLink);
        return dto;
    }
}
