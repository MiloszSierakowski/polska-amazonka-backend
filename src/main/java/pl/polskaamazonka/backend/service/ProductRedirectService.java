package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductRedirectService {

    private final ProductRepository productRepository;
    private final AffiliateCodeService affiliateCodeService;
    private final ProductPageParserFactory productPageParserFactory;
    private final AffiliateUrlBuilder affiliateUrlBuilder;
    private final AffiliateTrackingDetector affiliateTrackingDetector;
    private final AffiliateShortLinkResolver affiliateShortLinkResolver;
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public String resolveRedirectUrl(Long productId) {
        Product product = productRepository.findByIdWithProductLink(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Link productLink = product.getProductLink();
        if (productLink == null || productLink.getUrl() == null || productLink.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String storedUrl = productLink.getUrl().trim();
        if (affiliateShortLinkResolver.isWhitelistedShortLink(storedUrl)
                || affiliateTrackingDetector.hasExistingAffiliateTracking(storedUrl)) {
            return storedUrl;
        }

        Optional<Shop> shop = resolveShop(storedUrl);
        if (shop.isEmpty()) {
            return storedUrl;
        }

        Optional<AffiliateCode> affiliateCode = affiliateCodeService.getActiveAffiliateCode(shop.get());
        if (affiliateCode.isEmpty()) {
            return storedUrl;
        }

        String codeValue = affiliateCode.get().getCodeValue();
        if (codeValue == null || codeValue.isBlank()) {
            return storedUrl;
        }

        return affiliateUrlBuilder.apply(storedUrl, codeValue, shop.get());
    }

    private Optional<Shop> resolveShop(String productUrl) {
        String platformKey = productPageParserFactory.detectPlatform(productUrl);
        if (platformKey == null || platformKey.isBlank() || "generic".equals(platformKey)) {
            return Optional.empty();
        }
        return shopRepository.findBySlug(platformKey);
    }
}
