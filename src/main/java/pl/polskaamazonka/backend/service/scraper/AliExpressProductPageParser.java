package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

@Component
public class AliExpressProductPageParser extends AbstractMetaProductPageParser {

    public AliExpressProductPageParser(MetaPageDataExtractor metaPageDataExtractor, ProductNameCleaner productNameCleaner) {
        super(metaPageDataExtractor, productNameCleaner);
    }

    @Override
    public boolean supports(String host) {
        return host.contains("aliexpress.");
    }

    @Override
    public String platformKey() {
        return "aliexpress";
    }

    @Override
    public FetchHeaders fetchHeaders() {
        return FetchHeaders.withReferer("https://www.aliexpress.com/");
    }
}
