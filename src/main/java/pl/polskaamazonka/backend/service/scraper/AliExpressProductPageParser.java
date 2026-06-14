package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Locale;

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
        String body = document.body() != null
                ? document.body().text().toLowerCase(Locale.ROOT)
                : document.text().toLowerCase(Locale.ROOT);
        return body.contains("page not found")
                || body.contains("product not found")
                || body.contains("item not found")
                || body.contains("this item is unavailable")
                || body.contains("this item is no longer available")
                || body.contains("goods not found")
                || body.contains("sorry, this page can't be found")
                || body.contains("sorry, this page cannot be found");
    }

    public boolean isNotFoundHtml(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String normalized = html.toLowerCase(Locale.ROOT);
        return normalized.contains("\"redirectreason\":\"itemnotfound\"")
                || normalized.contains("\"redirectreason\": \"itemnotfound\"")
                || normalized.contains("\"itemstatus\":404")
                || normalized.contains("\"statuscode\":404")
                || normalized.contains("/img/404")
                || normalized.contains("image/404");
    }

    private boolean isErrorLikeTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("404")
                || lower.contains("not found")
                || lower.contains("unavailable")
                || lower.contains("nie znaleziono")
                || lower.contains("niedostępny");
    }
}
