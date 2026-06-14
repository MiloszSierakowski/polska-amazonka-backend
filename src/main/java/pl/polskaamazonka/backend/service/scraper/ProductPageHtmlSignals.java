package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ProductPageHtmlSignals {

    private static final List<String> VISIBLE_NOT_FOUND_PHRASES = List.of(
            "ups, nic tu nie ma",
            "ta strona nie istnieje",
            "mogliśmy ją usunąć lub przenieść",
            "nie możemy znaleźć tej strony",
            "ojej, nie możemy znaleźć tej strony",
            "nie znaleziono pożądanej strony",
            "page not found",
            "product not found",
            "goods not found",
            "item is unavailable",
            "item is not available",
            "this item is unavailable",
            "this item is no longer available",
            "product is unavailable",
            "the page you requested cannot be found",
            "sorry, the page you requested cannot be found",
            "this item is invalid",
            "item does not exist",
            "product does not exist",
            "przedmiot chwilowo niedostępny",
            "chwilowo niedostępny",
            "404 not found",
            "error 404",
            "http 404",
            "błąd 404",
            "sorry, this page can't be found",
            "sorry, this page cannot be found"
    );

    private static final List<String> STRUCTURED_NOT_FOUND_MARKERS = List.of(
            "\"notfound\":true",
            "\"notfound\": true",
            "productstatus\":\"offline\"",
            "productstatus\": \"offline\"",
            "itemstatus\":\"offline\"",
            "isnotfound\":true",
            "isnotfound\": true",
            "\"redirectreason\":\"itemnotfound\"",
            "\"redirectreason\": \"itemnotfound\"",
            "\"itemstatus\":404",
            "\"statuscode\":404"
    );

    private static final List<String> BOT_CHALLENGE_MARKERS = List.of(
            "captcha-delivery.com",
            "geo.captcha-delivery.com",
            "ct.captcha-delivery.com",
            "please enable js and disable any ad blocker",
            "cf-challenge",
            "cf-chl-bypass",
            "challenge-platform",
            "verify you are human",
            "just a moment...",
            "attention required",
            "dd={'rt'"
    );

    private static final Pattern ERROR_TITLE_PATTERN = Pattern.compile(
            "404|not\\s+found|unavailable|nie\\s+znaleziono|nic\\s+tu\\s+nie\\s+ma|strona\\s+nie\\s+istnieje|błąd",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public boolean isConfidentNotFound(Document document, String html) {
        if (isErrorTitle(extractDocumentTitle(document))) {
            return true;
        }
        String visibleText = extractVisibleText(document);
        if (containsVisibleNotFoundPhrase(visibleText)) {
            return true;
        }
        return isConfidentNotFoundStructuredHtml(html);
    }

    public boolean isBotChallengeHtml(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String normalized = html.toLowerCase(Locale.ROOT);
        for (String marker : BOT_CHALLENGE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConfidentNotFoundStructuredHtml(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String normalized = html.toLowerCase(Locale.ROOT);
        for (String marker : STRUCTURED_NOT_FOUND_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String extractDocumentTitle(Document document) {
        if (document == null) {
            return null;
        }
        String title = document.title();
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    private String extractVisibleText(Document document) {
        if (document == null) {
            return "";
        }
        if (document.body() != null) {
            return document.body().text().toLowerCase(Locale.ROOT);
        }
        return document.text().toLowerCase(Locale.ROOT);
    }

    private boolean containsVisibleNotFoundPhrase(String visibleText) {
        if (visibleText == null || visibleText.isBlank()) {
            return false;
        }
        for (String phrase : VISIBLE_NOT_FOUND_PHRASES) {
            if (visibleText.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean isErrorTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        return ERROR_TITLE_PATTERN.matcher(title).find();
    }
}
