package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import pl.polskaamazonka.backend.service.scraper.AllegroProductPageParser;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressProductPageParser;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.FetchHeaders;
import pl.polskaamazonka.backend.service.scraper.MetaPageDataExtractor;
import pl.polskaamazonka.backend.service.scraper.PageDocumentFetcher;
import pl.polskaamazonka.backend.service.scraper.ProductLinkAvailability;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.ProductPageHtmlSignals;
import pl.polskaamazonka.backend.service.scraper.ProductPageParser;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;
import pl.polskaamazonka.backend.service.scraper.ProductLinkRedirectValidator;
import pl.polskaamazonka.backend.service.scraper.TemuProductPageParser;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

@Service
@RequiredArgsConstructor
public class ProductPageScraperService {

    private static final List<String> BROKEN_REDIRECT_MARKERS = List.of(
            "/not-found",
            "/404/",
            "/error",
            "item-unavailable",
            "product-not-found",
            "page-not-found",
            "goods-not-found"
    );

    private final ProductPageParserFactory productPageParserFactory;
    private final PageDocumentFetcher pageDocumentFetcher;
    private final ProductNameCleaner productNameCleaner;
    private final MetaPageDataExtractor metaPageDataExtractor;
    private final TemuProductPageParser temuProductPageParser;
    private final AllegroProductPageParser allegroProductPageParser;
    private final AliExpressProductPageParser aliExpressProductPageParser;
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;
    private final ProductLinkRedirectValidator productLinkRedirectValidator;
    private final ProductPageHtmlSignals productPageHtmlSignals;

    public ProductPageData scrape(String pageUrl) {
        try {
            String normalizedUrl = normalizePageUrl(pageUrl);
            ProductPageParser parser = productPageParserFactory.resolve(normalizedUrl);
            FetchHeaders headers = resolveFetchHeaders(parser.fetchHeaders(), normalizedUrl);
            Document document = pageDocumentFetcher.fetch(normalizedUrl, headers);
            String effectiveUrl = resolveEffectiveUrl(document, normalizedUrl);
            ProductPageData data = parser.parse(document, effectiveUrl);
            String name = data.getName();
            if (productNameCleaner.isWeakScrapedName(name, effectiveUrl)) {
                String slugName = productNameCleaner.nameFromUrlSlug(effectiveUrl);
                if (slugName != null && !slugName.isBlank()) {
                    name = slugName;
                } else {
                    name = productNameCleaner.fallbackFromUrl(effectiveUrl);
                }
            }
            return new ProductPageData(name, data.getImageUrl());
        } catch (Exception e) {
            return new ProductPageData(productNameCleaner.fallbackFromUrl(pageUrl), null);
        }
    }

    public ProductLinkAvailability evaluateProductLinkAvailability(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return ProductLinkAvailability.BROKEN;
        }
        try {
            String normalizedUrl = normalizePageUrl(pageUrl);
            ProductPageParser parser = productPageParserFactory.resolve(normalizedUrl);
            FetchHeaders headers = resolveFetchHeaders(parser.fetchHeaders(), normalizedUrl);
            Document document = pageDocumentFetcher.fetch(normalizedUrl, headers);
            int statusCode = document.connection().response().statusCode();
            String effectiveUrl = resolveEffectiveUrl(document, normalizedUrl);
            String html = document.html();
            ProductPageData data = parser.parse(document, effectiveUrl);

            if (statusCode == 404 || statusCode == 410) {
                return ProductLinkAvailability.BROKEN;
            }
            if (isBrokenRedirectUrl(effectiveUrl)) {
                return ProductLinkAvailability.BROKEN;
            }
            if (productLinkRedirectValidator.isSuspiciousRedirect(normalizedUrl, effectiveUrl)) {
                return ProductLinkAvailability.BROKEN;
            }
            if (hasConfirmedProductPage(document, effectiveUrl, data)) {
                return ProductLinkAvailability.WORKING;
            }
            if (isConfidentNotFound(document, effectiveUrl, html)) {
                return ProductLinkAvailability.BROKEN;
            }

            if (temuUrlNormalizer.isTemuUrl(effectiveUrl) && temuProductPageParser.isChallengePage(document)) {
                return ProductLinkAvailability.UNCERTAIN;
            }
            if (productPageHtmlSignals.isBotChallengeHtml(html)) {
                return hasConfirmedProductPage(document, effectiveUrl, data)
                        ? ProductLinkAvailability.WORKING
                        : ProductLinkAvailability.UNCERTAIN;
            }
            if (statusCode == 429 || statusCode == 503 || statusCode >= 500) {
                return ProductLinkAvailability.UNCERTAIN;
            }
            if (statusCode == 403) {
                return hasConfirmedProductPage(document, effectiveUrl, data)
                        ? ProductLinkAvailability.WORKING
                        : ProductLinkAvailability.UNCERTAIN;
            }

            if (hasConfirmedProductPage(document, effectiveUrl, data)) {
                return ProductLinkAvailability.WORKING;
            }
            if (statusCode >= 400) {
                return ProductLinkAvailability.BROKEN;
            }
            return ProductLinkAvailability.BROKEN;
        } catch (Exception ignored) {
            return ProductLinkAvailability.UNCERTAIN;
        }
    }

    public boolean isProductLinkBroken(String pageUrl) {
        return evaluateProductLinkAvailability(pageUrl) == ProductLinkAvailability.BROKEN;
    }

    private boolean isConfidentNotFound(Document document, String effectiveUrl, String html) {
        if (productPageHtmlSignals.isConfidentNotFound(document, html)) {
            return true;
        }
        if (isNotFoundDocument(document, effectiveUrl)) {
            return true;
        }
        if (isAliExpressUrl(effectiveUrl) && aliExpressProductPageParser.isNotFoundHtml(html)) {
            return true;
        }
        return false;
    }

    private boolean hasConfirmedProductPage(Document document, String pageUrl, ProductPageData data) {
        if (isNotFoundDocument(document, pageUrl)) {
            return false;
        }
        if (requiresStrictProductSignals(pageUrl)) {
            return hasStrictProductSignals(document, pageUrl, data);
        }
        if (hasRealProductTitle(document, pageUrl, data)) {
            return true;
        }
        return hasUsableProductImage(data, document, pageUrl);
    }

    private boolean requiresStrictProductSignals(String pageUrl) {
        return allegroUrlNormalizer.isAllegroUrl(pageUrl) || isAliExpressUrl(pageUrl);
    }

    private boolean hasStrictProductSignals(Document document, String pageUrl, ProductPageData data) {
        if (hasRealProductTitle(document, pageUrl, data)) {
            return true;
        }
        return hasUsableProductImage(data, document, pageUrl);
    }

    private boolean hasUsableProductImage(ProductPageData data, Document document, String pageUrl) {
        String imageUrl = data.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaPageDataExtractor.extractImage(document);
        }
        if (imageUrl == null || imageUrl.isBlank() || isGenericPlaceholderImage(imageUrl.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (allegroUrlNormalizer.isAllegroUrl(pageUrl)) {
            String lower = imageUrl.toLowerCase(Locale.ROOT);
            return lower.contains("allegroimg") || lower.contains("/original/");
        }
        if (isAliExpressUrl(pageUrl)) {
            return imageUrl.toLowerCase(Locale.ROOT).contains("alicdn.com");
        }
        return true;
    }

    private boolean isGenericPlaceholderImage(String lowerImageUrl) {
        return lowerImageUrl.contains("404")
                || lowerImageUrl.contains("notfound")
                || lowerImageUrl.contains("not-found")
                || lowerImageUrl.contains("placeholder")
                || lowerImageUrl.contains("empty")
                || lowerImageUrl.contains("default-img");
    }

    private boolean hasRealProductTitle(Document document, String pageUrl, ProductPageData data) {
        String rawTitle = metaPageDataExtractor.extractTitle(document);
        String cleanedTitle = productNameCleaner.clean(rawTitle);
        if (isValidProductTitle(cleanedTitle, pageUrl)) {
            return true;
        }
        return isValidProductTitle(data.getName(), pageUrl);
    }

    private boolean isValidProductTitle(String title, String pageUrl) {
        if (title == null || title.isBlank()) {
            return false;
        }
        if (isErrorLikeTitle(title)) {
            return false;
        }
        if (productNameCleaner.isWeakScrapedName(title, pageUrl)) {
            return false;
        }
        if (isSlugDerivedTitle(title, pageUrl)) {
            return false;
        }
        return !title.equals(productNameCleaner.fallbackFromUrl(pageUrl));
    }

    private boolean isNotFoundDocument(Document document, String effectiveUrl) {
        if (isExplicitNotFoundPage(document)) {
            return true;
        }
        if (allegroUrlNormalizer.isAllegroUrl(effectiveUrl) && allegroProductPageParser.isNotFoundPage(document)) {
            return true;
        }
        if (isAliExpressUrl(effectiveUrl) && aliExpressProductPageParser.isNotFoundPage(document)) {
            return true;
        }
        return false;
    }

    private boolean isSlugDerivedTitle(String title, String pageUrl) {
        String slugName = productNameCleaner.nameFromUrlSlug(pageUrl);
        if (slugName == null || slugName.isBlank()) {
            return false;
        }
        return title.equalsIgnoreCase(slugName)
                || title.equalsIgnoreCase(productNameCleaner.clean(slugName));
    }

    private boolean isErrorLikeTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("404")
                || lower.contains("błąd 404")
                || lower.contains("not found")
                || lower.contains("nie znaleziono")
                || lower.contains("nic tu nie ma")
                || lower.contains("strona nie istnieje")
                || lower.contains("niedostępny")
                || lower.contains("unavailable")
                || lower.contains("does not exist")
                || lower.contains("nie możemy znaleźć");
    }

    private boolean isBrokenRedirectUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (String marker : BROKEN_REDIRECT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExplicitNotFoundPage(Document document) {
        String title = metaPageDataExtractor.extractTitle(document);
        if (title == null || title.isBlank()) {
            title = document.title();
        }
        if (isErrorLikeTitle(title)) {
            return true;
        }
        String body = document.body() != null
                ? document.body().text().toLowerCase(Locale.ROOT)
                : document.text().toLowerCase(Locale.ROOT);
        return body.contains("ups, nic tu nie ma")
                || body.contains("ta strona nie istnieje");
    }

    private boolean isAliExpressUrl(String pageUrl) {
        try {
            String host = java.net.URI.create(pageUrl).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("aliexpress.");
        } catch (Exception ignored) {
            return pageUrl.toLowerCase(Locale.ROOT).contains("aliexpress.");
        }
    }

    private String resolveEffectiveUrl(Document document, String normalizedUrl) {
        String effectiveUrl = document.baseUri();
        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            return normalizedUrl;
        }
        return normalizePageUrl(effectiveUrl);
    }

    private FetchHeaders resolveFetchHeaders(FetchHeaders headers, String pageUrl) {
        if (amazonUrlNormalizer.isAmazonUrl(pageUrl)) {
            return FetchHeaders.withReferer(amazonUrlNormalizer.refererFor(pageUrl));
        }
        return headers;
    }

    private String normalizePageUrl(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return pageUrl;
        }
        if (allegroUrlNormalizer.isAllegroUrl(pageUrl)) {
            return allegroUrlNormalizer.normalize(pageUrl);
        }
        if (temuUrlNormalizer.isTemuUrl(pageUrl)) {
            return temuUrlNormalizer.normalize(pageUrl);
        }
        if (amazonUrlNormalizer.isAmazonUrl(pageUrl)) {
            return amazonUrlNormalizer.normalize(pageUrl);
        }
        return pageUrl.trim();
    }

    public String resolveProductName(String pageUrl, String providedName) {
        if (providedName != null && !providedName.isBlank()) {
            String cleaned = productNameCleaner.clean(providedName);
            if (cleaned != null && !cleaned.isBlank()) {
                return cleaned;
            }
        }
        return scrape(pageUrl).getName();
    }

    public String detectPlatform(String pageUrl) {
        return productPageParserFactory.detectPlatform(pageUrl);
    }
}
