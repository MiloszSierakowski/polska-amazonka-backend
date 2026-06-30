package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationResult;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationStatus;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

@Component
@RequiredArgsConstructor
public class ProductLinkUrlSupport {

    private static final String UNSUPPORTED_PLATFORM_MESSAGE =
            "Nieobsługiwana platforma. Obsługiwane platformy: Allegro, AliExpress, Temu, Amazon.";
    private static final String INVALID_PRODUCT_URL_MESSAGE = "Adres URL produktu jest niepoprawny.";

    private final QuickProductLinkValidator quickProductLinkValidator;
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

    public QuickProductLinkValidationResult validateProductUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adres URL produktu jest wymagany.");
        }
        QuickProductLinkValidationResult validation = quickProductLinkValidator.validate(rawUrl.trim());
        if (validation.status() != QuickProductLinkValidationStatus.VALID_PRODUCT_URL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationErrorMessage(validation));
        }
        return validation;
    }

    public String storedUrl(QuickProductLinkValidationResult validation) {
        return validation.originalUrl().trim();
    }

    public String verificationUrl(QuickProductLinkValidationResult validation) {
        String normalizedUrl = validation.normalizedUrl();
        if (normalizedUrl != null && !normalizedUrl.isBlank()) {
            return normalizedUrl;
        }
        return fallbackVerificationUrl(validation.originalUrl().trim());
    }

    public String verificationUrlForStored(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }
        QuickProductLinkValidationResult validation = quickProductLinkValidator.validate(storedUrl.trim());
        if (validation.status() == QuickProductLinkValidationStatus.VALID_PRODUCT_URL) {
            return verificationUrl(validation);
        }
        return fallbackVerificationUrl(storedUrl.trim());
    }

    private String fallbackVerificationUrl(String url) {
        if (allegroUrlNormalizer.isAllegroUrl(url)) {
            return allegroUrlNormalizer.normalize(url);
        }
        if (temuUrlNormalizer.isTemuUrl(url)) {
            return temuUrlNormalizer.normalize(url);
        }
        if (amazonUrlNormalizer.isAmazonUrl(url)) {
            return amazonUrlNormalizer.normalize(url);
        }
        return url.trim();
    }

    private String validationErrorMessage(QuickProductLinkValidationResult validation) {
        if (validation.status() == QuickProductLinkValidationStatus.UNSUPPORTED_PLATFORM) {
            return UNSUPPORTED_PLATFORM_MESSAGE;
        }
        if (validation.reason() != null && !validation.reason().isBlank()) {
            return validation.reason();
        }
        return INVALID_PRODUCT_URL_MESSAGE;
    }
}
