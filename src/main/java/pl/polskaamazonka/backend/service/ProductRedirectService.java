package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.enums.Platform;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductRedirectService {

    private final ProductRepository productRepository;
    private final AffiliateCodeService affiliateCodeService;
    private final ProductPageParserFactory productPageParserFactory;
    private final AffiliateUrlBuilder affiliateUrlBuilder;

    @Transactional(readOnly = true)
    public String resolveRedirectUrl(Long productId) {
        Product product = productRepository.findByIdWithProductLink(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Link productLink = product.getProductLink();
        if (productLink == null || productLink.getUrl() == null || productLink.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String originalUrl = productLink.getUrl().trim();
        Optional<Platform> platform = resolvePlatform(originalUrl);
        if (platform.isEmpty()) {
            return originalUrl;
        }

        Optional<AffiliateCode> affiliateCode = affiliateCodeService.getActiveAffiliateCode(platform.get());
        if (affiliateCode.isEmpty()) {
            return originalUrl;
        }

        String codeValue = affiliateCode.get().getCodeValue();
        if (codeValue == null || codeValue.isBlank()) {
            return originalUrl;
        }

        return affiliateUrlBuilder.apply(originalUrl, codeValue, platform.get());
    }

    private Optional<Platform> resolvePlatform(String productUrl) {
        String platformKey = productPageParserFactory.detectPlatform(productUrl);
        return switch (platformKey) {
            case "aliexpress" -> Optional.of(Platform.ALIEXPRESS);
            case "temu" -> Optional.of(Platform.TEMU);
            case "amazon" -> Optional.of(Platform.AMAZON);
            case "allegro" -> Optional.of(Platform.ALLEGRO);
            default -> Optional.empty();
        };
    }
}
