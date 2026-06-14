package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Locale;

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

    public boolean isNotFoundPage(Document document) {
        if (document == null) {
            return true;
        }
        String title = metaPageDataExtractor.extractTitle(document);
        if (title == null || title.isBlank()) {
            title = document.title();
        }
        if (isErrorLikeTitle(title)) {
            return true;
        }
        String body = document.text().toLowerCase(Locale.ROOT);
        return body.contains("ups, nic tu nie ma")
                || body.contains("ta strona nie istnieje")
                || body.contains("mogliśmy ją usunąć lub przenieść");
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
                || lower.contains("unavailable");
    }
}
