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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
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
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServicePublicCodeTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long OTHER_VIDEO_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long LINK_ID = 100L;
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
    @Spy
    private VideoService videoService;

    private Video video;
    private VideoProduct relation;

    @BeforeEach
    void setUp() {
        video = new Video();
        video.setId(VIDEO_ID);
        video.setTitle("Film");
        video.setTiktokUrl(TIKTOK_URL);
        video.setIsActive(true);
        video.setPreviewImageUrl("/uploads/videos/thumb.jpg");

        Link link = new Link();
        link.setId(LINK_ID);
        link.setUrl("https://allegro.pl/oferta/product-123456789");
        link.setType("product");
        link.setIsBroken(false);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Produkt");
        product.setProductLink(link);

        relation = new VideoProduct();
        relation.setVideo(video);
        relation.setProduct(product);

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoRepository.findWithProductsById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(VIDEO_ID);
            }
            return saved;
        });
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation));
        when(videoThumbnailStorageService.isReadableStoredUrl(anyString())).thenReturn(true);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"thumbnail_url\":\"https://thumb.example/video.jpg\"}"));
        when(videoRepository.existsByPublicCode(anyString())).thenReturn(false);
        when(videoRepository.existsByPublicCodeAndIdNot(anyString(), eq(VIDEO_ID))).thenReturn(false);
    }

    @Test
    void createWithoutPublicCodeIsRejected() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.create(dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Kod filmu jest wymagany.", exception.getReason());
    }

    @Test
    void createWithLowercaseCodeStoresNormalizedCode() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("a110");
        doReturn(adminResponse("A110")).when(videoService).getById(VIDEO_ID);

        videoService.create(dto);

        Video saved = lastSavedVideo();
        assertEquals("A110", saved.getPublicCode());
    }

    @Test
    void createWithTakenCodeIsRejected() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("A110");
        when(videoRepository.existsByPublicCode("A110")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.create(dto)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Kod filmu A110 jest już używany.", exception.getReason());
    }

    @Test
    void updateAllowsAddingCodeToVideoWithoutCode() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("B220");
        doReturn(adminResponse("B220")).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertEquals("B220", video.getPublicCode());
        verify(videoRepository).existsByPublicCodeAndIdNot("B220", VIDEO_ID);
    }

    @Test
    void updateAllowsKeepingSameCodeForSameVideo() {
        video.setPublicCode("A110");
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("a110");
        doReturn(adminResponse("A110")).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertEquals("A110", video.getPublicCode());
        verify(videoRepository).existsByPublicCodeAndIdNot("A110", VIDEO_ID);
    }

    @Test
    void updateAllowsChangingToAvailableCode() {
        video.setPublicCode("A110");
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("C330");
        doReturn(adminResponse("C330")).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertEquals("C330", video.getPublicCode());
    }

    @Test
    void updateRejectsCodeAssignedToAnotherVideo() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("A110");
        when(videoRepository.existsByPublicCodeAndIdNot("A110", VIDEO_ID)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Kod filmu A110 jest już używany.", exception.getReason());
    }

    @Test
    void updateWithoutCodeAndNullRequestKeepsNull() {
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode(null);
        doReturn(adminResponse(null)).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertNull(video.getPublicCode());
    }

    @Test
    void updateWithExistingCodeAndNullRequestIsRejected() {
        video.setPublicCode("A110");
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Kod filmu nie może zostać usunięty.", exception.getReason());
        assertEquals("A110", video.getPublicCode());
    }

    @Test
    void updateWithExistingCodeAndBlankRequestIsRejected() {
        video.setPublicCode("A110");
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Kod filmu nie może zostać usunięty.", exception.getReason());
        assertEquals("A110", video.getPublicCode());
    }

    @Test
    void updateWithExistingCodeAndWhitespaceRequestIsRejected() {
        video.setPublicCode("A110");
        VideoDTO dto = baseVideoDto();
        dto.setPublicCode("   ");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, dto)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Kod filmu nie może zostać usunięty.", exception.getReason());
        assertEquals("A110", video.getPublicCode());
    }

    @Test
    void getByIdReturnsPublicCodeInAdminResponse() {
        video.setPublicCode("AB120");

        VideoDTO response = videoService.getById(VIDEO_ID);

        assertEquals("AB120", response.getPublicCode());
    }

    private VideoDTO adminResponse(String publicCode) {
        VideoDTO dto = new VideoDTO();
        dto.setId(VIDEO_ID);
        dto.setPublicCode(publicCode);
        return dto;
    }

    private VideoDTO baseVideoDto() {
        VideoDTO dto = new VideoDTO();
        dto.setTitle("Film");
        dto.setTiktokUrl(TIKTOK_URL);
        dto.setIsActive(true);
        return dto;
    }

    private Video lastSavedVideo() {
        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository, org.mockito.Mockito.atLeastOnce()).save(videoCaptor.capture());
        List<Video> savedVideos = videoCaptor.getAllValues();
        return savedVideos.get(savedVideos.size() - 1);
    }
}
