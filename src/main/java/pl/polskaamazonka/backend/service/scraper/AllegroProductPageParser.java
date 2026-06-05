package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class AllegroProductPageParser extends AbstractMetaProductPageParser {

    private final AllegroImageExtractor allegroImageExtractor;

    public AllegroProductPageParser(
            MetaPageDataExtractor metaPageDataExtractor,
            ProductNameCleaner productNameCleaner,
            AllegroImageExtractor allegroImageExtractor
    ) {
        super(metaPageDataExtractor, productNameCleaner);
        this.allegroImageExtractor = allegroImageExtractor;
    }

    @Override
    public boolean supports(String host) {
        return host.contains("allegro.");
    }

    @Override
    public String platformKey() {
        return "allegro";
    }

    @Override
    public FetchHeaders fetchHeaders() {
        return FetchHeaders.withReferer("https://allegro.pl/");
    }

    @Override
    public ProductPageData parse(Document document, String pageUrl) {
        ProductPageData data = super.parse(document, pageUrl);
        String name = data.getName();
        if (productNameCleaner.isWeakScrapedName(name, pageUrl)) {
            String slugName = productNameCleaner.nameFromUrlSlug(pageUrl);
            if (slugName != null && !slugName.isBlank()) {
                name = slugName;
            }
        }
        String imageUrl = allegroImageExtractor.extract(document);
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = data.getImageUrl();
        }
        return new ProductPageData(name, imageUrl);
    }
}
