package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class TemuProductPageParser extends AbstractMetaProductPageParser {

    private final TemuImageExtractor temuImageExtractor;
    private final TemuUrlNormalizer temuUrlNormalizer;

    public TemuProductPageParser(
            MetaPageDataExtractor metaPageDataExtractor,
            ProductNameCleaner productNameCleaner,
            TemuImageExtractor temuImageExtractor,
            TemuUrlNormalizer temuUrlNormalizer
    ) {
        super(metaPageDataExtractor, productNameCleaner);
        this.temuImageExtractor = temuImageExtractor;
        this.temuUrlNormalizer = temuUrlNormalizer;
    }

    @Override
    public boolean supports(String host) {
        return host.contains("temu.com");
    }

    @Override
    public String platformKey() {
        return "temu";
    }

    @Override
    public FetchHeaders fetchHeaders() {
        return FetchHeaders.withSocialPreviewReferer("https://www.temu.com/");
    }

    @Override
    public ProductPageData parse(Document document, String pageUrl) {
        ProductPageData data = super.parse(document, pageUrl);
        String name = data.getName();
        if (productNameCleaner.isWeakScrapedName(name, pageUrl)) {
            String slugName = temuUrlNormalizer.nameFromTemuSlug(pageUrl);
            if (slugName != null && !slugName.isBlank()) {
                String cleaned = productNameCleaner.clean(slugName);
                if (cleaned != null && !cleaned.isBlank()) {
                    name = cleaned;
                }
            }
        }
        String imageUrl = temuImageExtractor.extract(document);
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = data.getImageUrl();
        }
        return new ProductPageData(name, imageUrl);
    }

    public boolean isChallengePage(Document document) {
        return temuImageExtractor.isChallengePage(document);
    }
}
