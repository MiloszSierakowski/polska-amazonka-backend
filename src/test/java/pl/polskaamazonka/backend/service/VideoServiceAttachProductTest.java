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
    private AllegroUrlNormalizer allegroUrlNormalizer;
    @Mock
    private TemuUrlNormalizer temuUrlNormalizer;
    @Mock
    private AmazonUrlNormalizer amazonUrlNormalizer;
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
    void addProduct_validAllegroUrl_savesNormalizedUrlAndMarksNeedsReview() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto(
                "https://allegro.pl/oferta/nazwa-produktu-123456789?utm_source=abc"
        );

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        Link savedLink = linkCaptor.getValue();
        assertEquals("https://allegro.pl/oferta/nazwa-produktu-123456789", savedLink.getUrl());
        assertFalse(savedLink.getIsBroken());
        assertTrue(savedLink.getNeedsReview());
        verify(productPageScraperService).scrape("https://allegro.pl/oferta/nazwa-produktu-123456789");
        verify(productPageScraperService, never()).evaluateProductLinkAvailability(anyString());
    }

    @Test
    void addProduct_validAliExpressUrl_savesProductWithNeedsReview() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        ProductDTO dto = productDto("https://www.aliexpress.com/item/1005001234567890.html?spm=a2g0o");

        videoService.addProduct(VIDEO_ID, dto);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertTrue(linkCaptor.getValue().getNeedsReview());
        assertFalse(linkCaptor.getValue().getIsBroken());
        verify(productRepository).save(any(Product.class));
        verify(videoProductRepository).save(any(VideoProduct.class));
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

    private ProductDTO productDto(String url) {
        ProductDTO dto = new ProductDTO();
        LinkDTO productLink = new LinkDTO();
        productLink.setUrl(url);
        productLink.setType("product");
        dto.setProductLink(productLink);
        return dto;
    }
}
