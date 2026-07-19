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
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.dto.ProductDTO;
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
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoServicePromotionTest {

    private static final Long VIDEO_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long SECOND_PRODUCT_ID = 11L;
    private static final Long LINK_ID = 100L;
    private static final Instant PROMOTION_START = Instant.parse("2026-06-28T12:00:00Z");
    private static final Instant PROMOTION_END = Instant.parse("2026-06-29T12:00:00Z");
    private static final String PRODUCT_URL = "https://allegro.pl/oferta/product-123456789";

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

    @Spy
    private VideoPublicCodeSupport videoPublicCodeSupport = new VideoPublicCodeSupport();

    @InjectMocks
    @Spy
    private VideoService videoService;

    private Video video;
    private Link link;
    private Product product;
    private VideoProduct relation;

    @BeforeEach
    void setUp() {
        video = new Video();
        video.setId(VIDEO_ID);
        video.setTitle("Film");
        video.setTiktokUrl("https://www.tiktok.com/@test/video/1234567890");
        video.setIsActive(true);
        video.setPreviewImageUrl("/uploads/videos/thumb.jpg");

        link = new Link();
        link.setId(LINK_ID);
        link.setUrl(PRODUCT_URL);
        link.setType("product");
        link.setIsBroken(false);
        link.setNeedsReview(false);

        product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Produkt");
        product.setImageUrl("/uploads/products/product.jpg");
        product.setProductLink(link);

        relation = new VideoProduct();
        relation.setId(1000L);
        relation.setVideo(video);
        relation.setProduct(product);

        when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoRepository.findWithProductsById(VIDEO_ID)).thenReturn(Optional.of(video));
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video saved = invocation.getArgument(0);
            saved.setId(VIDEO_ID);
            return saved;
        });
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation));
        when(videoProductRepository.findByVideo_IdAndProduct_Id(VIDEO_ID, PRODUCT_ID)).thenReturn(Optional.of(relation));
        when(videoProductRepository.findAllByProduct_Id(PRODUCT_ID)).thenReturn(List.of(relation));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link saved = invocation.getArgument(0);
            saved.setId(LINK_ID);
            return saved;
        });
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(PRODUCT_ID);
            return saved;
        });
        when(videoProductRepository.save(any(VideoProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productPageScraperService.scrape(anyString())).thenReturn(new ProductPageData("Scraped product", null));
        when(productImageStorageService.ensureDefaultImage()).thenReturn("/uploads/products/default.png");
        when(videoThumbnailStorageService.isReadableStoredUrl(anyString())).thenReturn(true);
        when(videoThumbnailStorageService.storeFromRemoteUrl(any())).thenReturn("/uploads/videos/thumb.jpg");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"thumbnail_url\":\"https://thumb.example/video.jpg\"}"));
        when(videoRepository.existsByPublicCode(anyString())).thenReturn(false);
        when(videoRepository.existsByPublicCodeAndIdNot(anyString(), any())).thenReturn(false);
    }

    @Test
    void createWithoutPromotionLeavesDatesNull() {
        VideoDTO dto = videoDto(null, null);

        videoService.create(dto);

        Video saved = lastSavedVideo();
        assertNull(saved.getPromotionStartAt());
        assertNull(saved.getPromotionEndAt());
    }

    @Test
    void createWithValidPromotionDatesPersistsDates() {
        VideoDTO dto = videoDto(PROMOTION_START, PROMOTION_END);
        dto.setProducts(List.of(productDto(PRODUCT_URL, "PROMO1")));

        videoService.create(dto);

        Video saved = lastSavedVideo();
        assertEquals(PROMOTION_START, saved.getPromotionStartAt());
        assertEquals(PROMOTION_END, saved.getPromotionEndAt());
        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertFalse(linkCaptor.getValue().getIsBroken());
        assertFalse(linkCaptor.getValue().getNeedsReview());
        assertNull(linkCaptor.getValue().getLastCheckedAt());
    }

    @Test
    void createWithProductPromoCodeStoresCodeOnVideoProductRelation() {
        VideoDTO dto = videoDto(null, null);
        dto.setProducts(List.of(productDto(PRODUCT_URL, "CREATE10")));

        videoService.create(dto);

        ArgumentCaptor<VideoProduct> relationCaptor = ArgumentCaptor.forClass(VideoProduct.class);
        verify(videoProductRepository).save(relationCaptor.capture());
        assertEquals("CREATE10", relationCaptor.getValue().getPromoCode());
    }

    @Test
    void updateSetsPromotionDates() {
        VideoDTO dto = videoDto(PROMOTION_START, PROMOTION_END);
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertEquals(PROMOTION_START, video.getPromotionStartAt());
        assertEquals(PROMOTION_END, video.getPromotionEndAt());
    }

    @Test
    void updateClearsPromotionWhenBothDatesAreNull() {
        video.setPromotionStartAt(PROMOTION_START);
        video.setPromotionEndAt(PROMOTION_END);
        VideoDTO dto = videoDto(null, null);
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertNull(video.getPromotionStartAt());
        assertNull(video.getPromotionEndAt());
    }

    @Test
    void updateWithOnlyPromotionStartIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, videoDto(PROMOTION_START, null))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Data rozpoczęcia i zakończenia promocji muszą być ustawione razem.", exception.getReason());
    }

    @Test
    void updateWithOnlyPromotionEndIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, videoDto(null, PROMOTION_END))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Data rozpoczęcia i zakończenia promocji muszą być ustawione razem.", exception.getReason());
    }

    @Test
    void updateWithEqualPromotionDatesIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, videoDto(PROMOTION_START, PROMOTION_START))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Data zakończenia promocji musi być późniejsza niż data rozpoczęcia.", exception.getReason());
    }

    @Test
    void updateWithPromotionStartAfterEndIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, videoDto(PROMOTION_END, PROMOTION_START))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Data zakończenia promocji musi być późniejsza niż data rozpoczęcia.", exception.getReason());
    }

    @Test
    void promotedVideoWithoutWorkingProductsIsRejected() {
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> videoService.update(VIDEO_ID, videoDto(PROMOTION_START, PROMOTION_END))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Film promowany musi mieć co najmniej jeden działający produkt.", exception.getReason());
    }

    @Test
    void markingOnlyProductOfPromotedVideoAsBrokenIsAllowed() {
        video.setPromotionStartAt(PROMOTION_START);
        video.setPromotionEndAt(PROMOTION_END);

        videoService.setProductLinkFlag(VIDEO_ID, PRODUCT_ID, true, false);

        assertTrue(link.getIsBroken());
        assertFalse(link.getNeedsReview());
        assertEquals(product, relation.getProduct());
        verify(linkRepository).updateReviewFlags(eq(LINK_ID), eq(true), eq(false), any(Instant.class));
    }

    @Test
    void markingOnlyProductOfPromotedVideoAsNeedingReviewIsAllowed() {
        video.setPromotionStartAt(PROMOTION_START);
        video.setPromotionEndAt(PROMOTION_END);

        videoService.setProductLinkFlag(VIDEO_ID, PRODUCT_ID, false, true);

        assertFalse(link.getIsBroken());
        assertTrue(link.getNeedsReview());
        assertEquals(product, relation.getProduct());
        verify(linkRepository).updateReviewFlags(eq(LINK_ID), eq(false), eq(true), any(Instant.class));
    }

    @Test
    void updateProductSavesPromoCodeOnVideoProductRelation() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        ProductDTO dto = productDto(PRODUCT_URL, "  SAVE10  ");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertEquals("SAVE10", relation.getPromoCode());
    }

    @Test
    void getByIdReturnsDifferentPromoCodesForDifferentVideoProducts() {
        Product secondProduct = product(SECOND_PRODUCT_ID, "Drugi produkt", "https://allegro.pl/oferta/second-987654321");
        VideoProduct secondRelation = new VideoProduct();
        secondRelation.setId(1001L);
        secondRelation.setVideo(video);
        secondRelation.setProduct(secondProduct);
        relation.setPromoCode("FIRST");
        secondRelation.setPromoCode("SECOND");
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation, secondRelation));

        VideoDTO dto = videoService.getById(VIDEO_ID);

        assertEquals("FIRST", dto.getProducts().get(0).getPromoCode());
        assertEquals("SECOND", dto.getProducts().get(1).getPromoCode());
    }

    @Test
    void blankPromoCodeIsStoredAsNull() {
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);
        ProductDTO dto = productDto(PRODUCT_URL, "   ");

        videoService.updateProduct(VIDEO_ID, PRODUCT_ID, dto);

        assertNull(relation.getPromoCode());
    }

    @Test
    void promoCodeOfOneProductDoesNotLeakToAnotherVideoProduct() {
        Product secondProduct = product(SECOND_PRODUCT_ID, "Drugi produkt", "https://allegro.pl/oferta/second-987654321");
        VideoProduct secondRelation = new VideoProduct();
        secondRelation.setId(1001L);
        secondRelation.setVideo(video);
        secondRelation.setProduct(secondProduct);
        relation.setPromoCode("ONLY_FIRST");
        when(videoProductRepository.findByVideo_Id(VIDEO_ID)).thenReturn(List.of(relation, secondRelation));

        VideoDTO dto = videoService.getById(VIDEO_ID);

        assertEquals("ONLY_FIRST", dto.getProducts().get(0).getPromoCode());
        assertNull(dto.getProducts().get(1).getPromoCode());
    }

    @Test
    void publicVideoDtoDoesNotExposePromotionDatesOrPromoCodeAtThisStage() {
        video.setPromotionStartAt(PROMOTION_START);
        video.setPromotionEndAt(PROMOTION_END);
        video.setPublicCode("A110");
        relation.setPromoCode("SECRET");

        PublicVideoDTO dto = videoService.getByIdPublic(VIDEO_ID);

        assertNull(dto.getPromotionStartAt());
        assertNull(dto.getPromotionEndAt());
        assertEquals("A110", dto.getPublicCode());
        assertNull(dto.getProducts().get(0).getPromoCode());
    }

    @Test
    void promotedPublicListReturnsActivePromotionWithPromoCode() {
        Video promoted = publicVideo(20L, "Aktywna", activeStart(), activeEnd(), true);
        VideoProduct promotedRelation = relation(promoted, product(20L, "Produkt promowany", PRODUCT_URL), "ACTIVE10");
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(promoted));
        when(videoProductRepository.findByVideo_Id(20L)).thenReturn(List.of(promotedRelation));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getId());
        assertEquals("ACTIVE10", result.get(0).getProducts().get(0).getPromoCode());
    }

    @Test
    void regularPublicListExcludesOnlyActivePromotionAndKeepsFutureExpiredAndNormalVideosWithoutPromoCodes() {
        Video active = publicVideo(21L, "Aktywna", activeStart(), activeEnd(), true);
        Video future = publicVideo(22L, "Zaplanowana", futureStart(), futureEnd(), true);
        Video expired = publicVideo(23L, "Wygasła", expiredStart(), expiredEnd(), true);
        Video normal = publicVideo(24L, "Zwykła", null, null, true);
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active, future, expired, normal));
        when(videoProductRepository.findByVideo_Id(21L)).thenReturn(List.of(relation(active, product(21L, "Aktywna", PRODUCT_URL), "ACTIVE")));
        when(videoProductRepository.findByVideo_Id(22L)).thenReturn(List.of(relation(future, product(22L, "Zaplanowana", PRODUCT_URL), "FUTURE")));
        when(videoProductRepository.findByVideo_Id(23L)).thenReturn(List.of(relation(expired, product(23L, "Wygasła", PRODUCT_URL), "EXPIRED")));
        when(videoProductRepository.findByVideo_Id(24L)).thenReturn(List.of(relation(normal, product(24L, "Zwykła", PRODUCT_URL), "NORMAL")));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertEquals(List.of(22L, 23L, 24L), result.stream().map(PublicVideoDTO::getId).toList());
        assertTrue(result.stream().flatMap(video -> video.getProducts().stream()).allMatch(product -> product.getPromoCode() == null));
    }

    @Test
    void promotedPublicListDoesNotReturnFutureExpiredOrNormalVideos() {
        Video future = publicVideo(25L, "Zaplanowana", futureStart(), futureEnd(), true);
        Video expired = publicVideo(26L, "Wygasła", expiredStart(), expiredEnd(), true);
        Video normal = publicVideo(27L, "Zwykła", null, null, true);
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(future, expired, normal));
        when(videoProductRepository.findByVideo_Id(25L)).thenReturn(List.of(relation(future, product(25L, "Zaplanowana", PRODUCT_URL), "FUTURE")));
        when(videoProductRepository.findByVideo_Id(26L)).thenReturn(List.of(relation(expired, product(26L, "Wygasła", PRODUCT_URL), "EXPIRED")));
        when(videoProductRepository.findByVideo_Id(27L)).thenReturn(List.of(relation(normal, product(27L, "Zwykła", PRODUCT_URL), "NORMAL")));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertTrue(result.isEmpty());
    }

    @Test
    void promotedPublicListPreservesPromotionStartDescendingOrderFromRepository() {
        Video newer = publicVideo(31L, "Nowsza", activeStart().plusSeconds(60), activeEnd(), true);
        Video older = publicVideo(32L, "Starsza", activeStart().minusSeconds(60), activeEnd(), true);
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(newer, older));
        when(videoProductRepository.findByVideo_Id(31L)).thenReturn(List.of(relation(newer, product(31L, "Nowsza", PRODUCT_URL), null)));
        when(videoProductRepository.findByVideo_Id(32L)).thenReturn(List.of(relation(older, product(32L, "Starsza", PRODUCT_URL), null)));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertEquals(List.of(31L, 32L), result.stream().map(PublicVideoDTO::getId).toList());
    }

    @Test
    void promotedPublicListSkipsInactiveAndVideosWithoutWorkingProductsAndFiltersBrokenProducts() {
        Video inactive = publicVideo(41L, "Nieaktywny", activeStart(), activeEnd(), false);
        Video noProducts = publicVideo(42L, "Bez produktów", activeStart(), activeEnd(), true);
        Video mixedProducts = publicVideo(43L, "Mieszany", activeStart(), activeEnd(), true);
        Product brokenProduct = product(431L, "Zepsuty", PRODUCT_URL);
        brokenProduct.getProductLink().setIsBroken(true);
        Product workingProduct = product(432L, "Działający", PRODUCT_URL);
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(inactive, noProducts, mixedProducts));
        when(videoProductRepository.findByVideo_Id(42L)).thenReturn(List.of());
        when(videoProductRepository.findByVideo_Id(43L)).thenReturn(List.of(
                relation(mixedProducts, brokenProduct, "BROKEN"),
                relation(mixedProducts, workingProduct, "WORKING")
        ));

        List<PublicVideoDTO> result = videoService.getAllPromotedPublic();

        assertEquals(List.of(43L), result.stream().map(PublicVideoDTO::getId).toList());
        assertEquals(1, result.get(0).getProducts().size());
        assertEquals(432L, result.get(0).getProducts().get(0).getId());
        assertEquals("WORKING", result.get(0).getProducts().get(0).getPromoCode());
    }

    @Test
    void regularPublicListKeepsCreatedAtRepositoryOrder() {
        Video first = publicVideo(51L, "Pierwszy", null, null, true);
        Video second = publicVideo(52L, "Drugi", null, null, true);
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(first, second));
        when(videoProductRepository.findByVideo_Id(51L)).thenReturn(List.of(relation(first, product(51L, "Pierwszy", PRODUCT_URL), null)));
        when(videoProductRepository.findByVideo_Id(52L)).thenReturn(List.of(relation(second, product(52L, "Drugi", PRODUCT_URL), null)));

        List<PublicVideoDTO> result = videoService.getAllPublic(null);

        assertEquals(List.of(51L, 52L), result.stream().map(PublicVideoDTO::getId).toList());
    }

    @Test
    void categoryFilteredRegularPublicListStillUsesCategoryRepositoryAndExcludesActivePromotion() {
        Video active = publicVideo(61L, "Aktywna", activeStart(), activeEnd(), true);
        Video future = publicVideo(62L, "Zaplanowana", futureStart(), futureEnd(), true);
        when(videoRepository.findAllByCategoryId(7L)).thenReturn(List.of(active, future));
        when(videoProductRepository.findByVideo_Id(61L)).thenReturn(List.of(relation(active, product(61L, "Aktywna", PRODUCT_URL), null)));
        when(videoProductRepository.findByVideo_Id(62L)).thenReturn(List.of(relation(future, product(62L, "Zaplanowana", PRODUCT_URL), null)));

        List<PublicVideoDTO> result = videoService.getAllPublic(7L);

        assertEquals(List.of(62L), result.stream().map(PublicVideoDTO::getId).toList());
    }

    @Test
    void activePromotionDoesNotAppearInBothPublicLists() {
        Video active = publicVideo(71L, "Aktywna", activeStart(), activeEnd(), true);
        when(videoRepository.findAllActivePromoted(any(Instant.class))).thenReturn(List.of(active));
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active));
        when(videoProductRepository.findByVideo_Id(71L)).thenReturn(List.of(relation(active, product(71L, "Aktywna", PRODUCT_URL), "ACTIVE")));

        List<PublicVideoDTO> promoted = videoService.getAllPromotedPublic();
        List<PublicVideoDTO> regular = videoService.getAllPublic(null);

        assertEquals(List.of(71L), promoted.stream().map(PublicVideoDTO::getId).toList());
        assertFalse(regular.stream().anyMatch(video -> video.getId().equals(71L)));
    }

    @Test
    void existingUpdateWithoutPromotionStillWorks() {
        VideoDTO dto = videoDto(null, null);
        doReturn(new VideoDTO()).when(videoService).getById(VIDEO_ID);

        videoService.update(VIDEO_ID, dto);

        assertEquals("Film", video.getTitle());
        assertNull(video.getPromotionStartAt());
        assertNull(video.getPromotionEndAt());
    }

    private VideoDTO videoDto(Instant promotionStartAt, Instant promotionEndAt) {
        VideoDTO dto = new VideoDTO();
        dto.setTitle("Film");
        dto.setTiktokUrl("https://www.tiktok.com/@test/video/1234567890");
        dto.setIsActive(true);
        dto.setPromotionStartAt(promotionStartAt);
        dto.setPromotionEndAt(promotionEndAt);
        dto.setPublicCode("A110");
        return dto;
    }

    private ProductDTO productDto(String url, String promoCode) {
        ProductDTO dto = new ProductDTO();
        LinkDTO productLink = new LinkDTO();
        productLink.setUrl(url);
        productLink.setType("product");
        dto.setProductLink(productLink);
        dto.setPromoCode(promoCode);
        return dto;
    }

    private Video lastSavedVideo() {
        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository, atLeastOnce()).save(videoCaptor.capture());
        List<Video> savedVideos = videoCaptor.getAllValues();
        return savedVideos.get(savedVideos.size() - 1);
    }

    private Instant activeStart() {
        return Instant.now().minusSeconds(60);
    }

    private Instant activeEnd() {
        return Instant.now().plusSeconds(3600);
    }

    private Instant futureStart() {
        return Instant.now().plusSeconds(3600);
    }

    private Instant futureEnd() {
        return Instant.now().plusSeconds(7200);
    }

    private Instant expiredStart() {
        return Instant.now().minusSeconds(7200);
    }

    private Instant expiredEnd() {
        return Instant.now().minusSeconds(3600);
    }

    private Video publicVideo(Long id, String title, Instant promotionStartAt, Instant promotionEndAt, boolean active) {
        Video result = new Video();
        result.setId(id);
        result.setTitle(title);
        result.setTiktokUrl("https://www.tiktok.com/@test/video/" + id);
        result.setPreviewImageUrl("/uploads/videos/" + id + ".jpg");
        result.setIsActive(active);
        result.setCreatedAt(Instant.now().minusSeconds(id));
        result.setPromotionStartAt(promotionStartAt);
        result.setPromotionEndAt(promotionEndAt);
        result.setPublicCode("V" + id);
        return result;
    }

    private VideoProduct relation(Video owner, Product relationProduct, String promoCode) {
        VideoProduct result = new VideoProduct();
        result.setVideo(owner);
        result.setProduct(relationProduct);
        result.setPromoCode(promoCode);
        return result;
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
