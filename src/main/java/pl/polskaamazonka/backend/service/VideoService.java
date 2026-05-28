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
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.mapper.ProductMapper;
import pl.polskaamazonka.backend.mapper.VideoMapper;
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

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final ProductRepository productRepository;
    private final VideoProductRepository videoProductRepository;
    private final VideoCategoryRepository videoCategoryRepository;
    private final LinkRepository linkRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductPageScraperService productPageScraperService;
    private final VideoThumbnailStorageService videoThumbnailStorageService;

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
        return getById(saved.getId());
    }

    @Transactional
    public VideoDTO addProduct(Long videoId, ProductDTO dto) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        attachProduct(video, dto);
        return getById(videoId);
    }

    @Transactional
    public VideoDTO detachProduct(Long videoId, Long productId) {
        videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        VideoProduct relation = videoProductRepository.findByVideo_IdAndProduct_Id(videoId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Product product = relation.getProduct();
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

        return getById(videoId);
    }

    @Transactional
    public void delete(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

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
        String shopUrl = resolveShopUrl(dto);
        if (shopUrl == null || shopUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        Link link = new Link();
        link.setUrl(shopUrl);
        link.setType("product");
        link.setIsActive(Boolean.TRUE);
        link.setLastCheckedAt(Instant.now());
        link = linkRepository.save(link);

        ProductPageScraperService.ProductPageData scraped = productPageScraperService.scrape(shopUrl);
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

        Product product = new Product();
        product.setName(productName);
        product.setImageUrl(imageUrl);
        product.setProductLink(link);
        product = productRepository.save(product);

        VideoProduct videoProduct = new VideoProduct();
        videoProduct.setVideo(video);
        videoProduct.setProduct(product);
        videoProductRepository.save(videoProduct);
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
        return dto;
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
}
