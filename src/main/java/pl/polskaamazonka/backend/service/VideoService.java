package pl.polskaamazonka.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.polskaamazonka.backend.config.UploadPaths;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.ProductLinkVerifyResultDTO;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.mapper.ProductMapper;
import pl.polskaamazonka.backend.mapper.VideoMapper;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;
import pl.polskaamazonka.backend.service.scraper.ProductLinkAvailability;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.model.Link;
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
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;
    private final ActivityLogService activityLogService;

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
    public List<VideoDTO> getAllPublic(Long categoryId) {
        List<Video> videos = categoryId == null
                ? videoRepository.findAllByOrderByCreatedAtDesc()
                : videoRepository.findAllByCategoryId(categoryId);
        return videos.stream()
                .map(this::toPublicDtoWithProducts)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public VideoDTO getByIdPublic(Long id) {
        Video video = videoRepository.findWithProductsById(id)
                .orElse(null);
        if (video == null) {
            return null;
        }
        return toPublicDtoWithProducts(video);
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
        Video video = new Video();
        video.setTiktokUrl(dto.getTiktokUrl());
        video.setLocalMp4Url(dto.getLocalMp4Url());
        video.setTitle(dto.getTitle());
        video.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        video.setCreatedAt(Instant.now());
        Video saved = videoRepository.save(video);
        saved.setPreviewImageUrl(resolveAndPersistPreview(saved));
        saved = videoRepository.save(saved);
        if (dto.getProducts() != null) {
            for (ProductDTO productDto : dto.getProducts()) {
                attachProduct(saved, productDto);
            }
        }
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
        boolean tiktokChanged = !Objects.equals(video.getTiktokUrl(), dto.getTiktokUrl());
        String previousPreview = video.getPreviewImageUrl();
        video.setTitle(dto.getTitle());
        video.setTiktokUrl(dto.getTiktokUrl());
        video.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        if (dto.getLocalMp4Url() != null) {
            video.setLocalMp4Url(dto.getLocalMp4Url());
        }
        if (tiktokChanged) {
            video.setPreviewImageUrl(null);
        }
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
        return getById(videoId);
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
        String shopUrl = normalizeShopUrl(resolveShopUrl(dto));
        if (shopUrl == null || shopUrl.isBlank()) {
            shopUrl = link.getUrl();
        }
        if (shopUrl == null || shopUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        link.setUrl(shopUrl);
        linkRepository.updateReviewFlags(link.getId(), false, false, Instant.now());
        linkRepository.save(link);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            product.setName(dto.getName().trim());
        }
        if (dto.getImageUrl() != null && !dto.getImageUrl().isBlank()) {
            product.setImageUrl(dto.getImageUrl().trim());
        }
        productRepository.save(product);
        activityLogService.logAction("EDYCJA_PRODUKTU", "Zaktualizowano produkt o ID: " + productId + " (Nowy URL: " + shopUrl + ")");
        return getById(videoId);
    }

    @Transactional
    public VideoDTO detachProduct(Long videoId, Long productId) {
        videoRepository.findById(videoId)
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
        String shopUrl = product.getProductLink().getUrl();
        if (shopUrl == null || shopUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String previousImageUrl = product.getImageUrl();
        ProductPageData scraped = productPageScraperService.scrape(shopUrl);
        String productName = scraped.getName();
        if (productNameCleaner.isWeakScrapedName(productName, shopUrl)) {
            String slugName = productNameCleaner.nameFromUrlSlug(shopUrl);
            if (slugName != null && !slugName.isBlank()) {
                productName = slugName;
            } else {
                productName = productNameCleaner.fallbackFromUrl(shopUrl);
            }
        }
        String imageUrl = persistProductImage(scraped.getImageUrl(), shopUrl);
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
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        String shopUrl = link.getUrl().trim();
        ProductLinkAvailability availability = productPageScraperService.evaluateProductLinkAvailability(shopUrl);
        persistLinkAvailability(link, availability);

        ProductPageData scraped = scrapeSafely(shopUrl);
        Link refreshedLink = linkRepository.findById(link.getId()).orElse(link);
        ProductLinkVerifyResultDTO result = new ProductLinkVerifyResultDTO();
        result.setVideoId(videoId);
        result.setProductId(productId);
        result.setVerificationUncertain(availability == ProductLinkAvailability.UNCERTAIN);
        result.setNeedsReview(refreshedLink.getNeedsReview());
        result.setIsBroken(refreshedLink.getIsBroken());
        result.setLinkWorking(availability == ProductLinkAvailability.WORKING);
        result.setCurrentTitle(product.getName());
        result.setCurrentImageUrl(product.getImageUrl());
        result.setStoreTitle(resolveScrapedProductName(scraped, shopUrl));
        result.setStoreImageUrl(scraped != null ? scraped.getImageUrl() : null);
        String availabilityLabel = switch (availability) {
            case WORKING -> "sprawny";
            case BROKEN -> "niesprawny";
            case UNCERTAIN -> "niepewny";
        };
        activityLogService.logAction(
                "WERYFIKACJA_LINKU",
                "Zweryfikowano link produktu o ID: " + productId + " w filmie o ID: " + videoId
                        + " (Wynik: " + availabilityLabel + ")"
        );
        return result;
    }

    @Transactional
    public void setProductLinkFlag(Long videoId, Long productId, boolean isBroken, boolean needsReview) {
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        Instant checkedAt = Instant.now();
        linkRepository.updateReviewFlags(link.getId(), isBroken, needsReview, checkedAt);
        String statusLabel = needsReview ? "wymaga sprawdzenia" : (isBroken ? "niesprawny" : "sprawny");
        activityLogService.logAction(
                "RECZNA_FLAGA_LINKU",
                "Ręcznie ustawiono flagę linku produktu o ID: " + productId + " w filmie o ID: " + videoId
                        + " (Wynik: " + statusLabel + ")"
        );
    }

    private void persistLinkAvailability(Link link, ProductLinkAvailability availability) {
        Instant checkedAt = Instant.now();
        boolean isBroken;
        boolean needsReview;
        switch (availability) {
            case WORKING -> {
                isBroken = false;
                needsReview = false;
            }
            case BROKEN -> {
                isBroken = true;
                needsReview = false;
            }
            case UNCERTAIN -> {
                isBroken = Boolean.TRUE.equals(link.getIsBroken());
                needsReview = true;
            }
            default -> throw new IllegalStateException("Unexpected availability: " + availability);
        }
        linkRepository.updateReviewFlags(link.getId(), isBroken, needsReview, checkedAt);
        link.setIsBroken(isBroken);
        link.setNeedsReview(needsReview);
        link.setLastCheckedAt(checkedAt);
    }

    @Transactional
    public VideoDTO applyStoreTitleToProduct(Long videoId, Long productId) {
        Product product = loadProductForVideo(videoId, productId);
        Link link = requireProductLink(product);
        String shopUrl = link.getUrl().trim();
        ProductPageData scraped = productPageScraperService.scrape(shopUrl);
        String productName = resolveScrapedProductName(scraped, shopUrl);
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
        String shopUrl = link.getUrl().trim();
        ProductPageData scraped = productPageScraperService.scrape(shopUrl);
        String previousImageUrl = product.getImageUrl();
        String imageUrl = persistProductImage(scraped != null ? scraped.getImageUrl() : null, shopUrl);
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
        String shopUrl = normalizeShopUrl(resolveShopUrl(dto));
        if (shopUrl == null || shopUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        Link link = new Link();
        link.setUrl(shopUrl);
        link.setType("product");
        link.setIsActive(Boolean.TRUE);
        link.setIsBroken(false);
        link.setNeedsReview(false);
        link.setLastCheckedAt(Instant.now());
        link = linkRepository.save(link);

        ProductPageData scraped = productPageScraperService.scrape(shopUrl);
        String productName;
        if (dto.getName() != null && !dto.getName().isBlank()) {
            productName = productPageScraperService.resolveProductName(shopUrl, dto.getName());
        } else {
            productName = scraped.getName();
        }

        String imageUrl = dto.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = scraped.getImageUrl();
        }
        imageUrl = persistProductImage(imageUrl, shopUrl);

        Product product = new Product();
        product.setName(productName);
        product.setImageUrl(imageUrl);
        product.setProductLink(link);
        product = productRepository.save(product);

        VideoProduct videoProduct = new VideoProduct();
        videoProduct.setVideo(video);
        videoProduct.setProduct(product);
        videoProductRepository.save(videoProduct);
        activityLogService.logAction("UTWORZENIE_PRODUKTU", "Dodano produkt o ID: " + product.getId() + " do filmu o ID: " + video.getId() + " (Nazwa: " + productName + ", URL: " + shopUrl + ")");
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
                .map(vp -> ProductMapper.toDTO(vp.getProduct()))
                .toList());
        dto.setBlockReasons(resolveBlockReasons(video, relations));
        return dto;
    }

    private VideoDTO toPublicDtoWithProducts(Video video) {
        if (!Boolean.TRUE.equals(video.getIsActive())) {
            return null;
        }
        VideoDTO dto = VideoMapper.toDTO(video);
        dto.setPreviewImageUrl(resolveAndPersistPreview(video));
        List<ProductDTO> products = videoProductRepository.findByVideo_Id(video.getId()).stream()
                .map(VideoProduct::getProduct)
                .filter(Objects::nonNull)
                .filter(this::hasWorkingLink)
                .map(ProductMapper::toDTO)
                .toList();
        if (products.isEmpty()) {
            return null;
        }
        dto.setProducts(products);
        return dto;
    }

    private boolean hasWorkingLink(Product product) {
        Link link = product.getProductLink();
        if (link == null) {
            return false;
        }
        return !Boolean.TRUE.equals(link.getIsBroken());
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
                .filter(this::hasWorkingLink)
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

    private String normalizeShopUrl(String shopUrl) {
        if (shopUrl == null || shopUrl.isBlank()) {
            return shopUrl;
        }
        if (allegroUrlNormalizer.isAllegroUrl(shopUrl)) {
            return allegroUrlNormalizer.normalize(shopUrl);
        }
        if (temuUrlNormalizer.isTemuUrl(shopUrl)) {
            return temuUrlNormalizer.normalize(shopUrl);
        }
        if (amazonUrlNormalizer.isAmazonUrl(shopUrl)) {
            return amazonUrlNormalizer.normalize(shopUrl);
        }
        return shopUrl.trim();
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
