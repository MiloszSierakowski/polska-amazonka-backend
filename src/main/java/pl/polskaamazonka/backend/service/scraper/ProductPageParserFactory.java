package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class ProductPageParserFactory {

    private final List<ProductPageParser> parsers;
    private final GenericProductPageParser genericProductPageParser;

    public ProductPageParserFactory(
            List<ProductPageParser> parsers,
            GenericProductPageParser genericProductPageParser
    ) {
        this.parsers = parsers.stream()
                .filter(parser -> !(parser instanceof GenericProductPageParser))
                .toList();
        this.genericProductPageParser = genericProductPageParser;
    }

    public ProductPageParser resolve(String pageUrl) {
        String host = extractHost(pageUrl);
        for (ProductPageParser parser : parsers) {
            if (parser.supports(host)) {
                return parser;
            }
        }
        return genericProductPageParser;
    }

    public String detectPlatform(String pageUrl) {
        return resolve(pageUrl).platformKey();
    }

    private String extractHost(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }
}
