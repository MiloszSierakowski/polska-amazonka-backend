package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PageDocumentFetcher {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 12000;

    public Document fetch(String pageUrl, FetchHeaders headers) throws IOException {
        String userAgent = headers.resolvedUserAgent(BROWSER_USER_AGENT);
        Connection connection = Jsoup.connect(pageUrl)
                .userAgent(userAgent)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", headers.acceptLanguage())
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache");
        if (BROWSER_USER_AGENT.equals(userAgent)) {
            connection
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1");
        }
        if (headers.referer() != null && !headers.referer().isBlank()) {
            connection.header("Referer", headers.referer());
        }
        return connection.get();
    }
}
