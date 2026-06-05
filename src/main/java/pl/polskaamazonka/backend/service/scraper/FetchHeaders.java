package pl.polskaamazonka.backend.service.scraper;

public record FetchHeaders(String acceptLanguage, String referer, String userAgent) {
    private static final String DEFAULT_ACCEPT_LANGUAGE = "pl-PL,pl;q=0.9,en-US;q=0.9,en;q=0.8,en;q=0.7";
    private static final String SOCIAL_PREVIEW_USER_AGENT =
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)";

    public static FetchHeaders defaults() {
        return new FetchHeaders(DEFAULT_ACCEPT_LANGUAGE, null, null);
    }

    public static FetchHeaders withReferer(String referer) {
        return new FetchHeaders(DEFAULT_ACCEPT_LANGUAGE, referer, null);
    }

    public static FetchHeaders withSocialPreviewReferer(String referer) {
        return new FetchHeaders(DEFAULT_ACCEPT_LANGUAGE, referer, SOCIAL_PREVIEW_USER_AGENT);
    }

    public String resolvedUserAgent(String fallbackUserAgent) {
        if (userAgent != null && !userAgent.isBlank()) {
            return userAgent;
        }
        return fallbackUserAgent;
    }
}
