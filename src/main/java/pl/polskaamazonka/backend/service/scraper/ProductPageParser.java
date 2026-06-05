package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;

public interface ProductPageParser {
    boolean supports(String host);

    String platformKey();

    FetchHeaders fetchHeaders();

    ProductPageData parse(Document document, String pageUrl);
}
