package pl.polskaamazonka.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class PublicVideoPreviewService {

    private static final String SITE_NAME = "Polskie Amalinki";
    private static final String DESCRIPTION = "Obejrzyj film w serwisie Polskie Amalinki.";
    private static final String FALLBACK_IMAGE_PATH = "/assets/logo/polskie-amalinki-og.png";

    private final VideoService videoService;
    private final String publicBaseUrl;

    public PublicVideoPreviewService(
            VideoService videoService,
            @Value("${app.public-base-url:http://localhost:4200}") String publicBaseUrl
    ) {
        this.videoService = videoService;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public String render(String publicCode) {
        PublicVideoDTO video = videoService.getByPublicCodePublic(publicCode);
        String normalizedPublicCode = video.getPublicCode();
        String videoTitle = video.getTitle() == null || video.getTitle().isBlank()
                ? "Film"
                : video.getTitle().trim();
        String pageTitle = videoTitle + " | " + SITE_NAME;
        String publicUrl = publicBaseUrl + "/amafilmy/"
                + UriUtils.encodePathSegment(normalizedPublicCode, StandardCharsets.UTF_8);
        String imageUrl = resolveImageUrl(video.getPreviewImageUrl());

        return """
                <!doctype html>
                <html lang="pl">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <meta name="description" content="%s">
                  <link rel="canonical" href="%s">
                  <meta property="og:type" content="website">
                  <meta property="og:site_name" content="%s">
                  <meta property="og:title" content="%s">
                  <meta property="og:description" content="%s">
                  <meta property="og:url" content="%s">
                  <meta property="og:image" content="%s">
                  <meta property="og:image:alt" content="%s">
                  <meta name="twitter:card" content="summary_large_image">
                  <meta name="twitter:title" content="%s">
                  <meta name="twitter:description" content="%s">
                  <meta name="twitter:image" content="%s">
                </head>
                <body></body>
                </html>
                """.formatted(
                escape(pageTitle), escape(DESCRIPTION), escape(publicUrl), escape(SITE_NAME),
                escape(pageTitle), escape(DESCRIPTION), escape(publicUrl), escape(imageUrl),
                escape(videoTitle), escape(pageTitle), escape(DESCRIPTION), escape(imageUrl)
        );
    }

    private String resolveImageUrl(String previewImageUrl) {
        if (previewImageUrl == null || previewImageUrl.isBlank()) {
            return publicBaseUrl + FALLBACK_IMAGE_PATH;
        }

        String candidate = previewImageUrl.trim();
        try {
            URI uri = URI.create(candidate);
            if (uri.isAbsolute()) {
                if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null) {
                    return candidate;
                }
                return publicBaseUrl + FALLBACK_IMAGE_PATH;
            }
            return publicBaseUrl + (candidate.startsWith("/") ? candidate : "/" + candidate);
        } catch (IllegalArgumentException exception) {
            return publicBaseUrl + FALLBACK_IMAGE_PATH;
        }
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.public-base-url must be a valid HTTP(S) URL", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalStateException("app.public-base-url must be a valid HTTP(S) URL");
        }
        return normalized;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
