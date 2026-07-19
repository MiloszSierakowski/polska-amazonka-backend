package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.polskaamazonka.backend.config.UploadPaths;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.dto.ProductLinkVerifyResultDTO;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationResult;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.mapper.ProductMapper;
import pl.polskaamazonka.backend.mapper.VideoMapper;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {

    private static final Pattern TIKTOK_VIDEO_ID_PATTERN = Pattern.compile("/video/(\\d+)");

    private final VideoRepository videoRepository;
    private final ProductRepository productRepository;
    private final VideoProductRepository videoProductRepository;
    private final VideoCategoryRepository videoCategoryRepository;
    private final LinkRepository linkRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductPageScraperService productPageScraperService;
    private final VideoThumbnailStorageService videoThumbnailStorageService;
    private final ProductImageStorageService productImageStorageService;
    private final ProductNameCleaner productNameCleaner;
    private final ActivityLogService activityLogService;
    private final ProductLinkUrlSupport productLinkUrlSupport;
    private final ProductLinkVerificationService productLinkVerificationService;
    private final LinkValidationHistoryService linkValidationHistoryService;
    private final VideoPublicCodeSupport videoPublicCodeSupport;

    private static final String PUBLIC_CODE_REQUIRED_MESSAGE = "Kod filmu jest wymagany.";
    private static final String PUBLIC_CODE_INVALID_FORMAT_MESSAGE = "Kod filmu ma nieprawidłowy format.";
    private static final String PUBLIC_CODE_REMOVAL_NOT_ALLOWED_MESSAGE = "Kod filmu nie może zostać usunięty.";
    private static final String PUBLIC_VIDEO_NOT_FOUND_MESSAGE = "Film nie istnieje lub link jest nieaktualny.";

    @Transactional
    public List<VideoDTO> getAll(Long categoryId) {
        List<Video> videos = categoryId == null
                ? videoRepository.findAllByOrderByCreatedAtDesc()
                : videoRepository.findAllByCategoryId(categoryId);
        return videos.stream()
                .map(this::toDtoWithProducts)
                .toList();
    }

    @Transactional
    public List<PublicVideoDTO> getAllPublic(Long categoryId) {
        Instant now = Instant.now();
        List<Video> videos = categoryId == null
                ? videoRepository.findAllByOrderByCreatedAtDesc()
                : videoRepository.findAllByCategoryId(categoryId);
        return videos.stream()
                .filter(video -> !isPromotionActive(video, now))
                .map(video -> toPublicDtoWithProducts(video, false))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public List<PublicVideoDTO> getAllPromotedPublic() {
        Instant now = Instant.now();
        return videoRepository.findAllActivePromoted(now).stream()
                .filter(video -> isPromotionActive(video, now))
                .map(video -> toPublicDtoWithProducts(video, true))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public PublicVideoDTO getByIdPublic(Long id) {
        Video video = videoRepository.findWithProductsById(id)
                .orElse(null);
        if (video == null) {
            return null;
        }
        return toPublicDtoWithProducts(video, false);
    }

    @Transactional
    public PublicVideoDTO getByPublicCodePublic(String rawPublicCode) {
        String normalizedPublicCode;
        try {
            normalizedPublicCode = videoPublicCodeSupport.normalize(rawPublicCode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, PUBLIC_VIDEO_NOT_FOUND_MESSAGE);
        }
        Video video = videoRepository.findWithProductsByPublicCode(normalizedPublicCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PUBLIC_VIDEO_NOT_FOUND_MESSAGE));
        PublicVideoDTO dto = toPublicDtoWithProducts(video, isPromotionActive(video, Instant.now()));
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, PUBLIC_VIDEO_NOT_FOUND_MESSAGE);
        }
        return dto;
    }

    @Transactional
    public VideoDTO getById(Long id) {
        Video video = videoRepository.findWithProductsById(id)
                .orElse(null);
        if (video == null) {
            return null;
        }
        return toDtoWithProducts(video);
    }

    @Transactional
    public VideoDTO create(VideoDTO dto) {
        if (dto.getTiktokUrl() == null || dto.getTiktokUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        validatePromotionDates(dto.getPromotionStartAt(), dto.getPromotionEndAt());
        String publicCode = resolvePublicCodeForCreate(dto.getPublicCode());
        ensurePublicCodeAvailable(publicCode, null);
        Video video = new Video();
        video.setTiktokUrl(dto.getTiktokUrl());
        video.setLocalMp4Url(dto.getLocalMp4Url());
        video.setTitle(dto.getTitle());
        video.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        video.setPublicCode(publicCode);
        applyPromotionDates(video, dto);
        video.setCreatedAt(Instant.now());
        Video saved = videoRepository.save(video);
        saved.setPreviewImageUrl(resolveAndPersistPreview(saved));
        saved = videoRepository.save(saved);
        if (dto.getProducts() != null) {
            for (ProductDTO productDto : dto.getProducts()) {
                attachProduct(saved, productDto);
            }
        }
        validatePromotionProductPresence(saved);
        String title = saved.getTitle() != null && !saved.getTitle().isBlank() ? saved.getTitle() : "-";
        activityLogService.logAction("UTWORZENIE_FILMU", "Dodano film o tytule: " + title + " (ID: " + saved.getId() + ")");
        return getById(saved.getId());
    }

    @Transactional
    public VideoDTO update(Long id, VideoDTO dto) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (dto.getTiktokUrl() == null || dto.getTiktokUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        validatePromotionDates(dto.getPromotionStartAt(), dto.getPromotionEndAt());
        String publicCode = resolvePublicCodeForUpdate(dto.getPublicCode(), video.getPublicCode());
        ensurePublicCodeAvailable(publicCode, id);
        boolean tiktokChanged = !Objects.equals(video.getTiktokUrl(), dto.getTiktokUrl());
        String previousPreview = video.getPreviewImageUrl();
        video.setTitle(dto.getTitle());
        video.setTiktokUrl(dto.getTiktokUrl());
        video.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        video.setPublicCode(publicCode);
        applyPromotionDates(video, dto);
        if (dto.getLocalMp4Url() != null) {
            video.setLocalMp4Url(dto.getLocalMp4Url());
        }
        if (tiktokChanged) {
            video.setPreviewImageUrl(null);
        }
        validatePromotionProductPresence(video);
        videoRepository.save(video);
        if (tiktokChanged) {
            resolveAndPersistPreview(video);
            if (previousPreview != null
                    && !previousPreview.isBlank()
                    && !Objects.equals(previousPreview, video.getPreviewImageUrl())) {
                videoThumbnailStorageService.deleteByPublicUrl(previousPreview);
            }
        }
        String title = dto.getTitle() != null && !dto.getTitle().isBlank() ? dto.getTitle() : "-";
        activityLogService.logAction("EDYCJA_FILMU", "Zaktualizowano film o ID: " + id + " (Tytuł: " + title + ")");
        return getById(id);
    }

    @Transactional
    public VideoDTO addProduct(Long videoId, ProductDTO dto) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        attachProduct(video, dto);
        validatePromotionProductPresence(video);
        return getById(videoId);
    }

    @Transactional
    public VideoDTO attachExistingProduct(
            Long videoId,
            Long productId
    ) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Film nie istnieje."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nie istnieje."));
        if (videoProductRepository.existsByVideo_IdAndProduct_Id(videoId, productId)) {
            throw productAlreadyAssigned();
        }

        VideoProduct relation = new VideoProduct();
        relation.setVideo(video);
        relation.setProduct(product);
        relation.setPromoCode(resolveSharedPromoCode(productId));
        try {
            videoProductRepository.saveAndFlush(relation);
        } catch (DataIntegrityViolationException exception) {
            throw productAlreadyAssigned();
        }
        activityLogService.logAction(
                "PRZYPISANIE_PRODUKTU",
                "Przypisano istniejący produkt o ID: " + productId + " do filmu o ID: " + videoId
        );
        return getById(videoId);
    }

    private ResponseStatusException productAlreadyAssigned() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Produkt jest już przypisany do tego filmu."
        );
    }

    @Transactional
    public VideoDTO updateProduct(Long videoId, Long productId, ProductDTO dto) {
        videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VideoProduct relation = videoProductRepository.findByVideo_IdAndProduct_Id(videoId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Product product = relation.getProduct();
        if (product == null || product.getProductLink() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Link link = product.getProductLink();
        List<String> normalizedTags = ProductTagNormalizer.normalize(dto.getTags());
        String previousUrl = link.getUrl();
        String rawShopUrl = resolveShopUrl(dto);
        if (rawShopUrl == null || rawShopUrl.isBlank()) {
            rawShopUrl = previousUrl;
        }
        if (rawShopUrl == null || rawShopUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String trimmedRawUrl = rawShopUrl.trim();
        boolean urlChanged = previousUrl == null || !previousUrl.trim().equals(trimmedRawUrl);
        if (urlChanged) {
            QuickProductLinkValidationResult validation = productLinkUrlSupport.validateProductUrl(trimmedRawUrl);
            String storedUrl = productLinkUrlSupport.storedUrl(validation);
            String verificationUrl = productLinkUrlSupport.verificationUrl(validation);
            link.setIsBroken(false);
            link.setNeedsReview(false);
            link.setLastCheckedAt(null);
            link.setUrl(storedUrl);
            applyProductMetadataAfterUrlChange(product, dto, verificationUrl);
        } else {
            if (dto.getName() != null && !dto.getName().isBlank()) {
                product.setName(dto.getName().trim());
            }
            if (dto.getImageUrl() != null && !dto.getImageUrl().isBlank()) {
                product.setImageUrl(dto.getImageUrl().trim());
            }
        }
        updateSharedPromoCode(productId, dto.getPromoCode());
        product.replaceTags(normalizedTags);
        linkRepository.save(link);
        productRepository.save(product);
        videoProductRepository.save(relation);
        validatePromotionProductPresence(relation.getVideo());
        activityLogService.logAction(
                "EDYCJA_PRODUKTU",
                "Zaktualizowano produkt o ID: " + productId + " (Nowy URL: " + link.getUrl() + ")"
        );
        return getById(videoId);
    }

    @Transactional
    public VideoDTO detachProduct(Long videoId, Long productId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VideoProduct relation = videoProductRepository.findByVideo_IdAndProduct_Id(videoId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Product product = relation.getProduct();
        String productName = product != null && product.getName() != null ? product.getName() : "-";
        Long linkId = null;
        if (product != null && product.getProductLink() != null && product.getProductLink().getId() != null) {
            linkId = product.getProductLink().getId();
        }

        long usageCount = product != null && product.getId() != null
                ? videoProductRepository.countByProduct_Id(product.getId())
                : 0L;

        videoProductRepository.delete(relation);
        videoProductRepository.flush();
        validatePromotionProductPresence(video);

        if (usageCount <= 1L && product != null && product.getId() != null) {
            productRepository.delete(product);
            productRepository.flush();
            if (linkId != null && productRepository.countByProductLink_Id(linkId) == 0L) {
                linkRepository.findById(linkId)
                        .filter(link -> "product".equals(link.getType()))
                        .ifPresent(linkRepository::delete);
            }
        }

        activityLogService.logAction("USUNIĘCIE_PRODUKTU", "Usunięto produkt o ID: " + productId + " z filmu o ID: " + videoId + " (" + productName + ")");
        return getById(videoId);
    }

    @Transactional
    public VideoDTO resyncProduct(Long videoId, Long productId) {
        Video video = videoRepository.findWithProductsById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VideoProduct relation = videoProductRepository.findByVideo_IdAndProduct_Id(videoId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Product product = relation.getProduct();
        if (product == null || product.getProductLink() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String storedUrl = product.getProductLink().getUrl();
        if (storedUrl == null || storedUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String verificationUrl = productLinkUrlSupport.verificationUrlForStored(storedUrl);
        String previousImageUrl = product.getImageUrl();
        ProductPageData scraped = productPageScraperService.scrape(verificationUrl);
        String productName = scraped.getName();
        if (productNameCleaner.isWeakScrapedName(productName, verificationUrl)) {
            String slugName = productNameCleaner.nameFromUrlSlug(verificationUrl);
            if (slugName != null && !slugName.isBlank()) {
                productName = slugName;
            } else {
                productName = productNameCleaner.fallbackFromUrl(verificationUrl);
            }
        }
        String imageUrl = persistProductImage(scraped.getImageUrl(), verificationUrl);
        product.setName(productName);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
        if (previousImageUrl != null
                && !previousImageUrl.isBlank()
                && !previousImageUrl.equals(imageUrl)
                && UploadPaths.isStoredProductImageUrl(previousImageUrl)) {
            productImageStorageService.deleteByPublicUrl(previousImageUrl);
        }
        activityLogService.logAction("EDYCJA_PRODUKTU", "Odświeżono produkt o ID: " + productId + " w filmie o ID: " + videoId + " (Nazwa: " + productName + ")");
        return toDtoWithProducts(video);
    }

    @Transactional
    public ProductLinkVerifyResultDTO verifyProductLink(Long videoId, Long productId) {
        Long runId = linkValidationHistoryService.startRun(ProductLinkVerificationSource.MANUAL);
        try {
            Product product = loadProductForVideo(videoId, productId);
            Link link = requireProductLink(product);
            ProductLinkVerificationResult verification = productLinkVerificationService.verify(
                    link,
                    ProductLinkVerificationSource.MANUAL
            );
            boolean historyItemSaved = recordValidationItemSafely(runId, link, product, verification);

            ProductPageData scraped = verification.checkedUrl() == null
                    ? null
                    : scrapeSafely(verification.checkedUrl());
            ProductLinkVerifyResultDTO result = new ProductLinkVerifyResultDTO();
            result.setVideoId(videoId);
            result.setProductId(productId);
            result.setVerificationUncertain(verification.isUncertain());
            result.setNeedsReview(verification.needsReview());
            result.setIsBroken(verification.isBroken());
            result.setLinkWorking(verification.isWorking());
            result.setVerificationStatus(verification.status().name());
            result.setVerificationMessage(verification.message());
            result.setCurrentTitle(product.getName());
            result.setCurrentImageUrl(product.getImageUrl());
            result.setStoreTitle(scraped == null
                    ? null
                    : resolveScrapedProductName(scraped, verification.checkedUrl()));
            result.setStoreImageUrl(scraped != null ? scraped.getImageUrl() : null);
            String availabilityLabel = switch (verification.status()) {
                case WORKING -> "sprawny";
                case BROKEN -> "niesprawny";
                case UNCERTAIN -> "niepewny";
                case BLOCKED -> "zablokowany";
                case TECHNICAL_ERROR -> "błąd techniczny";
            };
            activityLogService.logAction(
                    "WERYFIKACJA_LINKU",
                    "Zweryfikowano link produktu o ID: " + productId + " w filmie o ID: " + videoId
                            + " (Wynik: " + availabilityLabel + ")"
            );
            LinkValidationRunStatus runStatus = verification.technicalError() || !historyItemSaved
                    ? LinkValidationRunStatus.COMPLETED_WITH_ERRORS
                    : LinkValidationRunStatus.COMPLETED;
            finishValidationRunSafely(
                    runId,
                    runStatus,
                    LinkValidationRunSummary.forSingle(verification),
                    historyItemSaved ? verification.message() : "Nie udało się zapisać wyniku weryfikacji."
            );
            return result;
        } catch (RuntimeException exception) {
            finishValidationRunSafely(
                    runId,
                    LinkValidationRunStatus.FAILED,
                    new LinkValidationRunSummary(1, 0, 0, 0, 0, 0, 1),
                    "Nie udało się wykonać ręcznej weryfikacji."
            );
            throw exception;
        }
    }

    private boolean recordValidationItemSafely(
            Long runId,
            Link link,
            Product product,
            ProductLinkVerificationResult verification
    ) {
        try {
            linkValidationHistoryService.recordItem(runId, link, product, verification);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Could not save manual validation history item for link id={}", link.getId());
            return false;
        }
    }

    private void finishValidationRunSafely(
            Long runId,
            LinkValidationRunStatus status,
            LinkValidationRunSummary summary,
            String lastError
    ) {
        try {
            linkValidationHistoryService.finishRun(runId, status, summary, lastError);
        } catch (RuntimeException exception) {
            log.error("Could not finish manual link validation history run id={}", runId);
        }
    }

    @Transactional
    public void setProductLinkFlag(Long videoId, Long productId, boolean isBroken, boolean needsReview) {
        videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        Instant checkedAt = Instant.now();
        linkRepository.updateReviewFlags(link.getId(), isBroken, needsReview, checkedAt);
        link.setIsBroken(isBroken);
        link.setNeedsReview(needsReview);
        link.setLastCheckedAt(checkedAt);
        String statusLabel = needsReview ? "wymaga sprawdzenia" : (isBroken ? "niesprawny" : "sprawny");
        activityLogService.logAction(
                "RECZNA_FLAGA_LINKU",
                "Ręcznie ustawiono flagę linku produktu o ID: " + productId + " w filmie o ID: " + videoId
                        + " (Wynik: " + statusLabel + ")"
        );
    }

    @Transactional
    public VideoDTO applyStoreTitleToProduct(Long videoId, Long productId) {
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        String verificationUrl = productLinkUrlSupport.verificationUrlForStored(link.getUrl().trim());
        ProductPageData scraped = productPageScraperService.scrape(verificationUrl);
        String productName = resolveScrapedProductName(scraped, verificationUrl);
        product.setName(productName);
        productRepository.save(product);
        activityLogService.logAction(
                "EDYCJA_PRODUKTU",
                "Nadpisano tytuł produktu o ID: " + productId + " z danych sklepu (Film ID: " + videoId + ")"
        );
        return getById(videoId);
    }

    @Transactional
    public VideoDTO applyStoreImageToProduct(Long videoId, Long productId) {
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        String verificationUrl = productLinkUrlSupport.verificationUrlForStored(link.getUrl().trim());
        ProductPageData scraped = productPageScraperService.scrape(verificationUrl);
        String previousImageUrl = product.getImageUrl();
        String imageUrl = persistProductImage(scraped != null ? scraped.getImageUrl() : null, verificationUrl);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
        if (previousImageUrl != null
                && !previousImageUrl.isBlank()
                && !previousImageUrl.equals(imageUrl)
                && UploadPaths.isStoredProductImageUrl(previousImageUrl)) {
            productImageStorageService.deleteByPublicUrl(previousImageUrl);
        }
        activityLogService.logAction(
                "EDYCJA_PRODUKTU",
                "Nadpisano obrazek produktu o ID: " + productId + " z danych sklepu (Film ID: " + videoId + ")"
        );
        return getById(videoId);
    }

    private Product loadProductForVideo(Long videoId, Long productId) {
        videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VideoProduct relation = videoProductRepository.findByVideo_IdAndProduct_IdWithLink(videoId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Product product = relation.getProduct();
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return product;
    }

    private Link requireProductLink(Product product) {
        Link link = product.getProductLink();
        if (link == null || link.getUrl() == null || link.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produkt nie ma prawidłowego linku do sklepu.");
        }
        return link;
    }

    private ProductPageData scrapeSafely(String shopUrl) {
        try {
            return productPageScraperService.scrape(shopUrl);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String resolveScrapedProductName(ProductPageData scraped, String shopUrl) {
        if (scraped == null || scraped.getName() == null || scraped.getName().isBlank()) {
            return productNameCleaner.fallbackFromUrl(shopUrl);
        }
        String productName = scraped.getName();
        if (productNameCleaner.isWeakScrapedName(productName, shopUrl)) {
            String slugName = productNameCleaner.nameFromUrlSlug(shopUrl);
            if (slugName != null && !slugName.isBlank()) {
                return slugName;
            }
            return productNameCleaner.fallbackFromUrl(shopUrl);
        }
        return productName;
    }

    @Transactional
    public void delete(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String title = video.getTitle() != null && !video.getTitle().isBlank() ? video.getTitle() : "-";
        activityLogService.logAction("USUNIĘCIE_FILMU", "Usunięto film o ID: " + id + " (Tytuł: " + title + ")");

        String previewToDelete = video.getPreviewImageUrl();
        List<VideoProduct> relations = new ArrayList<>(videoProductRepository.findByVideo_Id(id));
        Set<Long> linkIdsToCheck = new HashSet<>();

        for (VideoProduct relation : relations) {
            Product product = relation.getProduct();
            if (product == null || product.getId() == null) {
                videoProductRepository.delete(relation);
                continue;
            }
            Long productId = product.getId();
            long usageCount = videoProductRepository.countByProduct_Id(productId);

            if (usageCount <= 1L) {
                Link link = product.getProductLink();
                if (link != null && link.getId() != null && "product".equals(link.getType())) {
                    linkIdsToCheck.add(link.getId());
                }
                videoProductRepository.delete(relation);
                productRepository.delete(product);
            } else {
                videoProductRepository.delete(relation);
            }
        }

        videoCategoryRepository.deleteByVideo_Id(id);
        videoRepository.delete(video);
        videoRepository.flush();
        videoThumbnailStorageService.deleteByPublicUrl(previewToDelete);

        linkIdsToCheck.stream()
                .filter(linkId -> productRepository.countByProductLink_Id(linkId) == 0L)
                .forEach(linkId -> linkRepository.findById(linkId)
                        .filter(link -> "product".equals(link.getType()))
                        .ifPresent(linkRepository::delete));
    }

    private void attachProduct(Video video, ProductDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        List<String> normalizedTags = ProductTagNormalizer.normalize(dto.getTags());
        QuickProductLinkValidationResult validation = productLinkUrlSupport.validateProductUrl(resolveShopUrl(dto));
        String storedUrl = productLinkUrlSupport.storedUrl(validation);
        String verificationUrl = productLinkUrlSupport.verificationUrl(validation);

        Link link = new Link();
        link.setUrl(storedUrl);
        link.setType("product");
        link.setIsActive(Boolean.TRUE);
        link.setIsBroken(false);
        link.setNeedsReview(false);
        link.setLastCheckedAt(null);
        link = linkRepository.save(link);

        ProductPageData scraped = productPageScraperService.scrape(verificationUrl);
        String productName;
        if (dto.getName() != null && !dto.getName().isBlank()) {
            productName = productPageScraperService.resolveProductName(verificationUrl, dto.getName());
        } else {
            productName = scraped.getName();
        }

        String imageUrl = dto.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = scraped.getImageUrl();
        }
        imageUrl = persistProductImage(imageUrl, verificationUrl);

        Product product = new Product();
        product.setName(productName);
        product.setImageUrl(imageUrl);
        product.setProductLink(link);
        product.replaceTags(normalizedTags);
        product = productRepository.save(product);

        VideoProduct videoProduct = new VideoProduct();
        videoProduct.setVideo(video);
        videoProduct.setProduct(product);
        videoProduct.setPromoCode(normalizePromoCode(dto.getPromoCode()));
        videoProductRepository.save(videoProduct);
        activityLogService.logAction("UTWORZENIE_PRODUKTU", "Dodano produkt o ID: " + product.getId() + " do filmu o ID: " + video.getId() + " (Nazwa: " + productName + ", URL: " + storedUrl + ")");
    }

    private String resolveShopUrl(ProductDTO dto) {
        if (dto.getProductLink() != null && dto.getProductLink().getUrl() != null && !dto.getProductLink().getUrl().isBlank()) {
            return dto.getProductLink().getUrl();
        }
        return null;
    }

    private VideoDTO toDtoWithProducts(Video video) {
        VideoDTO dto = VideoMapper.toDTO(video);
        dto.setPreviewImageUrl(resolveAndPersistPreview(video));
        List<VideoProduct> relations = videoProductRepository.findByVideo_Id(video.getId());
        dto.setProducts(relations.stream()
                .map(ProductMapper::toDTO)
                .toList());
        dto.setBlockReasons(resolveBlockReasons(video, relations));
        return dto;
    }

    private PublicVideoDTO toPublicDtoWithProducts(Video video, boolean includePromoCodes) {
        if (!Boolean.TRUE.equals(video.getIsActive())) {
            return null;
        }
        if (!hasPublicCodeForPublicApi(video)) {
            return null;
        }
        PublicVideoDTO dto = new PublicVideoDTO();
        dto.setId(video.getId());
        dto.setTiktokUrl(video.getTiktokUrl());
        dto.setLocalMp4Url(video.getLocalMp4Url());
        dto.setPreviewImageUrl(video.getPreviewImageUrl());
        dto.setTitle(video.getTitle());
        dto.setIsActive(video.getIsActive());
        dto.setPublicCode(video.getPublicCode());
        dto.setPromotionStartAt(video.getPromotionStartAt());
        dto.setPromotionEndAt(video.getPromotionEndAt());
        if (!includePromoCodes) {
            dto.setPromotionStartAt(null);
            dto.setPromotionEndAt(null);
        }
        dto.setPreviewImageUrl(resolveAndPersistPreview(video));
        var products = videoProductRepository.findByVideo_Id(video.getId()).stream()
                .filter(videoProduct -> videoProduct.getProduct() != null)
                .filter(videoProduct -> hasPubliclyAvailableLink(videoProduct.getProduct()))
                .map(videoProduct -> ProductMapper.toPublicVideoDTO(videoProduct, includePromoCodes))
                .toList();
        if (products.isEmpty()) {
            return null;
        }
        dto.setProducts(products);
        return dto;
    }

    private boolean hasPubliclyAvailableLink(Product product) {
        return ProductLinkPublicVisibility.isPubliclyAvailable(product.getProductLink());
    }

    private boolean hasPublicCodeForPublicApi(Video video) {
        if (video == null) {
            return false;
        }
        String publicCode = video.getPublicCode();
        return publicCode != null && !publicCode.isBlank();
    }

    private boolean isPromotionActive(Video video, Instant now) {
        if (video == null
                || video.getPromotionStartAt() == null
                || video.getPromotionEndAt() == null
                || now == null) {
            return false;
        }
        return !video.getPromotionStartAt().isAfter(now)
                && video.getPromotionEndAt().isAfter(now);
    }

    private void validatePromotionDates(Instant promotionStartAt, Instant promotionEndAt) {
        if (promotionStartAt == null && promotionEndAt == null) {
            return;
        }
        if (promotionStartAt == null || promotionEndAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Data rozpoczęcia i zakończenia promocji muszą być ustawione razem."
            );
        }
        if (!promotionStartAt.isBefore(promotionEndAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Data zakończenia promocji musi być późniejsza niż data rozpoczęcia."
            );
        }
    }

    private void applyPromotionDates(Video video, VideoDTO dto) {
        video.setPromotionStartAt(dto.getPromotionStartAt());
        video.setPromotionEndAt(dto.getPromotionEndAt());
    }

    private void validatePromotionProductPresence(Video video) {
        if (!hasPromotionDates(video)) {
            return;
        }
        List<VideoProduct> relations = videoProductRepository.findByVideo_Id(video.getId());
        boolean hasWorkingProduct = relations.stream()
                .map(VideoProduct::getProduct)
                .filter(Objects::nonNull)
                .anyMatch(this::hasPubliclyAvailableLink);
        if (!hasWorkingProduct) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Film promowany musi mieć co najmniej jeden działający produkt."
            );
        }
    }

    private boolean hasPromotionDates(Video video) {
        return video != null
                && video.getPromotionStartAt() != null
                && video.getPromotionEndAt() != null;
    }

    private String normalizePromoCode(String promoCode) {
        if (promoCode == null) {
            return null;
        }
        String trimmed = promoCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveSharedPromoCode(Long productId) {
        Set<String> existingCodes = videoProductRepository.findAllByProduct_Id(productId).stream()
                .map(VideoProduct::getPromoCode)
                .map(this::normalizePromoCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (existingCodes.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Produkt ma niespójne kody promocyjne. Ujednolić kody przed kolejnym przypisaniem."
            );
        }
        return existingCodes.stream().findFirst().orElse(null);
    }

    private void updateSharedPromoCode(Long productId, String rawPromoCode) {
        String promoCode = normalizePromoCode(rawPromoCode);
        if (promoCode != null && promoCode.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kod promocyjny może mieć maksymalnie 255 znaków.");
        }
        List<VideoProduct> relations = videoProductRepository.findAllByProduct_Id(productId);
        relations.forEach(relation -> relation.setPromoCode(promoCode));
        videoProductRepository.saveAll(relations);
    }

    private String resolvePublicCodeForCreate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PUBLIC_CODE_REQUIRED_MESSAGE);
        }
        try {
            return videoPublicCodeSupport.normalize(raw);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PUBLIC_CODE_INVALID_FORMAT_MESSAGE);
        }
    }

    private String resolvePublicCodeForUpdate(String raw, String currentPublicCode) {
        if (raw == null || raw.isBlank()) {
            if (currentPublicCode != null && !currentPublicCode.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        PUBLIC_CODE_REMOVAL_NOT_ALLOWED_MESSAGE
                );
            }
            return null;
        }
        try {
            return videoPublicCodeSupport.normalize(raw);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PUBLIC_CODE_INVALID_FORMAT_MESSAGE);
        }
    }

    private void ensurePublicCodeAvailable(String publicCode, Long excludeVideoId) {
        if (publicCode == null) {
            return;
        }
        boolean taken = excludeVideoId == null
                ? videoRepository.existsByPublicCode(publicCode)
                : videoRepository.existsByPublicCodeAndIdNot(publicCode, excludeVideoId);
        if (taken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kod filmu " + publicCode + " jest już używany."
            );
        }
    }

    private List<String> resolveBlockReasons(Video video, List<VideoProduct> relations) {
        List<String> reasons = new ArrayList<>();
        if (!Boolean.TRUE.equals(video.getIsActive())) {
            reasons.add("Film oznaczony jako nieaktywny");
        }
        if (!isRecognizedTikTokVideoUrl(video.getTiktokUrl())) {
            reasons.add("Nieprawidłowy lub nierozpoznany link do wideo");
        }
        List<Product> products = relations.stream()
                .map(VideoProduct::getProduct)
                .filter(Objects::nonNull)
                .toList();
        long workingProductCount = products.stream()
                .filter(this::hasPubliclyAvailableLink)
                .count();
        if (products.isEmpty() || workingProductCount == 0L) {
            reasons.add("Brak przypisanych i działających produktów");
        }
        return reasons;
    }

    private boolean isRecognizedTikTokVideoUrl(String tiktokUrl) {
        if (tiktokUrl == null || tiktokUrl.isBlank()) {
            return false;
        }
        return TIKTOK_VIDEO_ID_PATTERN.matcher(tiktokUrl.trim()).find();
    }

    private String resolveAndPersistPreview(Video video) {
        String current = video.getPreviewImageUrl();
        if (videoThumbnailStorageService.isReadableStoredUrl(current)) {
            return current;
        }
        if (current != null && !current.isBlank() && !requiresRemoteRefresh(current)) {
            String stored = videoThumbnailStorageService.storeFromRemoteUrl(current);
            return persistPreviewIfChanged(video, current, stored);
        }
        String remote = fetchThumbnailUrlFromTikTokOembed(video.getTiktokUrl());
        String stored = videoThumbnailStorageService.storeFromRemoteUrl(remote);
        return persistPreviewIfChanged(video, current, stored);
    }

    private String persistPreviewIfChanged(Video video, String current, String stored) {
        if (stored == null || stored.isBlank()) {
            stored = videoThumbnailStorageService.ensureDefaultThumbnail();
        }
        if (!Objects.equals(current, stored)) {
            video.setPreviewImageUrl(stored);
            videoRepository.save(video);
        }
        return stored;
    }

    private boolean requiresRemoteRefresh(String previewUrl) {
        String lower = previewUrl.toLowerCase(Locale.ROOT);
        if (UploadPaths.isStoredVideoThumbnailUrl(previewUrl)) {
            return false;
        }
        return lower.contains("tiktokcdn")
                || lower.contains("tiktokv.com")
                || lower.contains("muscdn")
                || lower.contains("byteimg")
                || lower.contains("ibyteimg");
    }

    private String fetchThumbnailUrlFromTikTokOembed(String tiktokUrl) {
        try {
            URI uri = UriComponentsBuilder.fromUriString("https://www.tiktok.com/oembed")
                    .queryParam("url", tiktokUrl)
                    .build()
                    .encode()
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "PolskaAmazonka/1.0");
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode thumbnail = root.get("thumbnail_url");
            if (thumbnail == null || thumbnail.isNull() || thumbnail.asText().isBlank()) {
                return null;
            }
            return thumbnail.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private void applyProductMetadataAfterUrlChange(Product product, ProductDTO dto, String verificationUrl) {
        String previousImageUrl = product.getImageUrl();
        boolean nameExplicitlyUpdated = isExplicitNameUpdate(dto, product);
        boolean imageExplicitlyUpdated = isExplicitImageUpdate(dto, product);

        if (nameExplicitlyUpdated) {
            product.setName(productPageScraperService.resolveProductName(verificationUrl, dto.getName().trim()));
        }
        if (imageExplicitlyUpdated) {
            String imageUrl = persistProductImage(dto.getImageUrl().trim(), verificationUrl);
            replaceProductImage(product, previousImageUrl, imageUrl);
            previousImageUrl = imageUrl;
        }
        if (nameExplicitlyUpdated && imageExplicitlyUpdated) {
            return;
        }

        ProductPageData scraped = productPageScraperService.scrape(verificationUrl);
        if (!nameExplicitlyUpdated) {
            if (hasMeaningfulScrapedName(scraped, verificationUrl)) {
                product.setName(resolveScrapedProductName(scraped, verificationUrl));
            } else {
                product.setName(buildPendingProductName(verificationUrl));
            }
        }
        if (!imageExplicitlyUpdated) {
            String scrapedImage = scraped != null ? scraped.getImageUrl() : null;
            if (scrapedImage != null && !scrapedImage.isBlank()) {
                String imageUrl = persistProductImage(scrapedImage, verificationUrl);
                replaceProductImage(product, previousImageUrl, imageUrl);
            } else {
                String defaultImage = productImageStorageService.ensureDefaultImage();
                replaceProductImage(product, previousImageUrl, defaultImage);
            }
        }
    }

    private boolean isExplicitNameUpdate(ProductDTO dto, Product product) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return false;
        }
        String nextName = dto.getName().trim();
        String currentName = product.getName() == null ? "" : product.getName().trim();
        return !nextName.equals(currentName);
    }

    private boolean isExplicitImageUpdate(ProductDTO dto, Product product) {
        if (dto.getImageUrl() == null || dto.getImageUrl().isBlank()) {
            return false;
        }
        String nextImage = dto.getImageUrl().trim();
        String currentImage = product.getImageUrl() == null ? "" : product.getImageUrl().trim();
        return !nextImage.equals(currentImage);
    }

    private boolean hasMeaningfulScrapedName(ProductPageData scraped, String shopUrl) {
        if (scraped == null || scraped.getName() == null || scraped.getName().isBlank()) {
            return false;
        }
        String scrapedName = scraped.getName().trim();
        if (productNameCleaner.isWeakScrapedName(scrapedName, shopUrl)) {
            return false;
        }
        String fallback = productNameCleaner.fallbackFromUrl(shopUrl);
        return fallback == null || !scrapedName.equalsIgnoreCase(fallback.trim());
    }

    private String buildPendingProductName(String shopUrl) {
        String slugName = productNameCleaner.nameFromUrlSlug(shopUrl);
        if (slugName != null && !slugName.isBlank()) {
            String cleaned = productNameCleaner.clean(slugName);
            if (cleaned != null && !cleaned.isBlank()) {
                return "[Do weryfikacji] " + cleaned;
            }
        }
        return "[Do weryfikacji] nowy link";
    }

    private void replaceProductImage(Product product, String previousImageUrl, String newImageUrl) {
        product.setImageUrl(newImageUrl);
        if (previousImageUrl != null
                && !previousImageUrl.isBlank()
                && !previousImageUrl.equals(newImageUrl)
                && UploadPaths.isStoredProductImageUrl(previousImageUrl)) {
            productImageStorageService.deleteByPublicUrl(previousImageUrl);
        }
    }

    private String persistProductImage(String imageUrl, String shopUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return productImageStorageService.ensureDefaultImage();
        }
        if (UploadPaths.isStoredProductImageUrl(imageUrl)) {
            return imageUrl;
        }
        String stored = productImageStorageService.tryStoreFromRemoteUrl(imageUrl, shopUrl);
        if (stored != null) {
            return stored;
        }
        if (productImageStorageService.isBrowserDisplayableRemoteUrl(imageUrl)) {
            return imageUrl.trim();
        }
        return productImageStorageService.ensureDefaultImage();
    }
}
