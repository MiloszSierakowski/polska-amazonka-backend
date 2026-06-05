package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.ProductImageUploadDTO;
import pl.polskaamazonka.backend.dto.ProductPreviewDTO;
import pl.polskaamazonka.backend.service.ProductImageFileStorageService;
import pl.polskaamazonka.backend.service.ProductPageScraperService;
import pl.polskaamazonka.backend.service.ProductService;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductPageScraperService productPageScraperService;
    private final ProductImageFileStorageService productImageFileStorageService;
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

    @GetMapping
    public List<ProductDTO> getAll() {
        return productService.getAll();
    }

    @GetMapping("/preview")
    public ProductPreviewDTO preview(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adres URL produktu jest wymagany.");
        }
        String trimmedUrl = normalizePreviewUrl(url);
        ProductPageData data = productPageScraperService.scrape(trimmedUrl);
        String platform = productPageScraperService.detectPlatform(trimmedUrl);
        boolean requiresManualImage = "allegro".equals(platform)
                && (data.getImageUrl() == null || data.getImageUrl().isBlank());
        return new ProductPreviewDTO(data.getName(), data.getImageUrl(), platform, requiresManualImage);
    }

    @PostMapping(value = "/image-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImageUploadDTO uploadImage(@RequestParam("file") MultipartFile file) {
        return new ProductImageUploadDTO(productImageFileStorageService.store(file));
    }

    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    private String normalizePreviewUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (allegroUrlNormalizer.isAllegroUrl(url)) {
            return allegroUrlNormalizer.normalize(url);
        }
        if (temuUrlNormalizer.isTemuUrl(url)) {
            return temuUrlNormalizer.normalize(url);
        }
        if (amazonUrlNormalizer.isAmazonUrl(url)) {
            return amazonUrlNormalizer.normalize(url);
        }
        return url.trim();
    }
}
