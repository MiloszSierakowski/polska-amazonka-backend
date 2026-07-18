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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServiceAttachProductTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long LINK_ID = 100L;
    private static final Long PRODUCT_ID = 10L;
    private static final String ALIEXPRESS_SHORT_URL = "https://s.click.aliexpress.com/e/_c2x0Gp5j";
    private static final String ALIEXPRESS_PRODUCT_URL = "https://pl.aliexpress.com/item/1005001234567890.html";
    private static final String TEMU_SHORT_URL = "https://share.temu.com/abc123";
    private static final String TEMU_PRODUCT_URL = "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html";

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

    private final FakeShortLinkRedirectClient shortLinkRedirectClient = new FakeShortLinkRedirectClient();

    @Spy
    private QuickProductLinkValidator quickProductLinkValidator = new QuickProductLinkValidator(
            new AllegroUrlNormalizer(),
            new AliExpressUrlNormalizer(),
            new TemuUrlNormalizer(),
            new AmazonUrlNormalizer(),
            new AffiliateShortLinkResolver(
                    shortLinkRedirectClient,
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

    private Video video;

    @BeforeEach
    void setUp() {
        video = new Video();
        video.setId(VIDEO_ID);
        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link link = invocation.getArgument(0);
            link.setId(LINK_ID);
            return link;
        });
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(PRODUCT_ID);
            return product;
        });
        when(productPageScraperService.scrape(anyString())).thenReturn(new ProductPageData("Scraped name", null));
        when(productImageStorageService.ensureDefaultImage()).thenReturn("/images/default-product.png");
    }

    @Test
    void addProduct_fullUrlWithQueryParamsIsStoredIdentically() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        String submittedUrl = "https://allegro.pl/oferta/nazwa-produktu-123456789?utm_source=abc&ref=xyz";
        ProductDTO dto = productDto(submittedUrl);

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        Link savedLink = linkCaptor.getValue();
        assertEquals(submittedUrl, savedLink.getUrl());
        verify(productPageScraperService).scrape("https://allegro.pl/oferta/nazwa-produktu-123456789");
    }

    @Test
    void addProduct_validAllegroUrl_marksNeedsReview() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto(
                "https://allegro.pl/oferta/nazwa-produktu-123456789?utm_source=abc"
        );

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        Link savedLink = linkCaptor.getValue();
        assertEquals("https://allegro.pl/oferta/nazwa-produktu-123456789?utm_source=abc", savedLink.getUrl());
        assertFalse(savedLink.getIsBroken());
        assertTrue(savedLink.getNeedsReview());
        verify(productPageScraperService).scrape("https://allegro.pl/oferta/nazwa-produktu-123456789");
    }

    @Test
    void addProduct_validAliExpressUrl_storesFullUrlAndUsesVerificationUrlForScraper() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        String submittedUrl = "https://www.aliexpress.com/item/1005001234567890.html?spm=a2g0o";
        ProductDTO dto = productDto(submittedUrl);

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertEquals(submittedUrl, linkCaptor.getValue().getUrl());
        verify(productPageScraperService).scrape("https://www.aliexpress.com/item/1005001234567890.html");
    }

    @Test
    void addProduct_aliExpressShortLinkIsStoredAsShortLink() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        shortLinkRedirectClient.expand(ALIEXPRESS_SHORT_URL, ALIEXPRESS_PRODUCT_URL);
        ProductDTO dto = productDto(ALIEXPRESS_SHORT_URL);

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertEquals(ALIEXPRESS_SHORT_URL, linkCaptor.getValue().getUrl());
        verify(productPageScraperService).scrape(ALIEXPRESS_PRODUCT_URL);
    }

    @Test
    void addProduct_temuShortLinkIsStoredAsShortLink() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        shortLinkRedirectClient.expand(TEMU_SHORT_URL, TEMU_PRODUCT_URL);
        ProductDTO dto = productDto(TEMU_SHORT_URL);

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertEquals(TEMU_SHORT_URL, linkCaptor.getValue().getUrl());
        verify(productPageScraperService).scrape(TEMU_PRODUCT_URL);
    }

    @Test
    void addProduct_allegroListingIsRejected() {
        ProductDTO dto = productDto("https://allegro.pl/listing?string=telefon");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.addProduct(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(linkRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void addProduct_amazonHomepageIsRejected() {
        ProductDTO dto = productDto("https://www.amazon.pl");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.addProduct(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void addProduct_unsupportedPlatformIsRejected() {
        ProductDTO dto = productDto("https://www.ebay.com/itm/123");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.addProduct(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "Nieobsługiwana platforma. Obsługiwane platformy: Allegro, AliExpress, Temu, Amazon.",
                exception.getReason()
        );
        verify(linkRepository, never()).save(any());
    }

    @Test
    void addProduct_blankUrlIsRejected() {
        ProductDTO dto = productDto("   ");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.addProduct(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Adres URL produktu jest wymagany.", exception.getReason());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void addProduct_unparseableUrlIsRejected() {
        ProductDTO dto = productDto("not-a-url");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.addProduct(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void addProduct_savesNormalizedTagsOnNewGlobalProduct() {
        ProductDTO dto = productDto(ALIEXPRESS_PRODUCT_URL);
        dto.setTags(java.util.List.of("  Gąbka   do naczyń ", "gąbka do naczyń", "kuchnia"));

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals(
                java.util.List.of("Gąbka do naczyń", "kuchnia"),
                productCaptor.getValue().getTags().stream().map(tag -> tag.getValue()).toList()
        );
    }

    private ProductDTO productDto(String url) {
        ProductDTO dto = new ProductDTO();
        LinkDTO productLink = new LinkDTO();
        productLink.setUrl(url);
        productLink.setType("product");
        dto.setProductLink(productLink);
        return dto;
    }

    private static class FakeShortLinkRedirectClient implements ShortLinkRedirectClient {
        private final Map<String, String> expansions = new HashMap<>();

        void expand(String originalUrl, String expandedUrl) {
            expansions.put(originalUrl, expandedUrl);
        }

        @Override
        public String expand(String url) {
            return expansions.get(url);
        }
    }
}
