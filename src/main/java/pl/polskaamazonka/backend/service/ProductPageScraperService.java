package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.FetchHeaders;
import pl.polskaamazonka.backend.service.scraper.PageDocumentFetcher;
import pl.polskaamazonka.backend.service.scraper.ProductNameCleaner;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.ProductPageParser;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

@Service
@RequiredArgsConstructor
public class ProductPageScraperService {

    private final ProductPageParserFactory productPageParserFactory;
    private final PageDocumentFetcher pageDocumentFetcher;
    private final ProductNameCleaner productNameCleaner;
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

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
