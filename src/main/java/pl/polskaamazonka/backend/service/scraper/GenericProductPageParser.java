package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

@Component
public class GenericProductPageParser extends AbstractMetaProductPageParser {

    public GenericProductPageParser(MetaPageDataExtractor metaPageDataExtractor, ProductNameCleaner productNameCleaner) {
        super(metaPageDataExtractor, productNameCleaner);
    }

    @Override
    public boolean supports(String host) {
        return false;
    }

    @Override
    public String platformKey() {
        return "generic";
    }

    @Override
    public FetchHeaders fetchHeaders() {
        return FetchHeaders.defaults();
    }
}
