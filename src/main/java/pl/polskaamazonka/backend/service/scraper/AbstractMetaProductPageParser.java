package pl.polskaamazonka.backend.service.scraper;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;

@RequiredArgsConstructor
public abstract class AbstractMetaProductPageParser implements ProductPageParser {

    protected final MetaPageDataExtractor metaPageDataExtractor;
    protected final ProductNameCleaner productNameCleaner;

    @Override
    public ProductPageData parse(Document document, String pageUrl) {
        String rawTitle = metaPageDataExtractor.extractTitle(document);
        String cleanedName = productNameCleaner.clean(rawTitle);
        if (cleanedName == null || cleanedName.isBlank()) {
            cleanedName = productNameCleaner.fallbackFromUrl(pageUrl);
        }
        String imageUrl = metaPageDataExtractor.extractImage(document);
        return new ProductPageData(cleanedName, imageUrl);
    }
}
