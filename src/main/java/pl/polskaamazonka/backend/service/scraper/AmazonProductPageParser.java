package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class AmazonProductPageParser extends AbstractMetaProductPageParser {

    private final AmazonImageExtractor amazonImageExtractor;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

    public AmazonProductPageParser(
            MetaPageDataExtractor metaPageDataExtractor,
            ProductNameCleaner productNameCleaner,
            AmazonImageExtractor amazonImageExtractor,
            AmazonUrlNormalizer amazonUrlNormalizer
    ) {
        super(metaPageDataExtractor, productNameCleaner);
        this.amazonImageExtractor = amazonImageExtractor;
        this.amazonUrlNormalizer = amazonUrlNormalizer;
    }

    @Override
    public boolean supports(String host) {
        return host.contains("amazon.") || host.equals("amzn.to") || host.endsWith(".amzn.to");
    }

    @Override
    public String platformKey() {
        return "amazon";
    }

    @Override
    public FetchHeaders fetchHeaders() {
        return FetchHeaders.withReferer("https://www.amazon.pl/");
    }

    @Override
    public ProductPageData parse(Document document, String pageUrl) {
        String effectiveUrl = document.baseUri();
        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            effectiveUrl = pageUrl;
        }
        effectiveUrl = amazonUrlNormalizer.normalize(effectiveUrl);

        ProductPageData data = super.parse(document, effectiveUrl);
        String name = data.getName();
        Element titleElement = document.selectFirst("#productTitle, #title span, h1#title");
        if (titleElement != null) {
            String candidate = refineAmazonTitle(titleElement.text());
            if (candidate != null && !candidate.isBlank()) {
                name = candidate;
            }
        }
        if (productNameCleaner.isWeakScrapedName(name, effectiveUrl)) {
            String slugName = productNameCleaner.nameFromUrlSlug(effectiveUrl);
            if (slugName != null && !slugName.isBlank()) {
                name = slugName;
            }
        }

        String imageUrl = amazonImageExtractor.extract(document);
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = data.getImageUrl();
        }
        return new ProductPageData(name, imageUrl);
    }

    private String refineAmazonTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return null;
        }
        String normalized = Jsoup.parse(rawTitle).text().trim();
        int commaIndex = normalized.indexOf(',');
        if (commaIndex > 0 && commaIndex < normalized.length() - 1) {
            String prefix = normalized.substring(0, commaIndex).trim();
            String suffix = productNameCleaner.clean(normalized.substring(commaIndex + 1));
            if (suffix != null && !suffix.isBlank()) {
                if (prefix.regionMatches(true, 0, "TODO", 0, 4) || prefix.length() < 30) {
                    return suffix;
                }
            }
        }
        String cleaned = productNameCleaner.clean(normalized);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }
        if (cleaned.regionMatches(true, 0, "TODO", 0, 4)) {
            String withoutTodo = productNameCleaner.clean(cleaned.replaceFirst("(?i)^TODO\\s+", ""));
            if (withoutTodo != null && !withoutTodo.isBlank()) {
                return withoutTodo;
            }
        }
        return cleaned;
    }
}
