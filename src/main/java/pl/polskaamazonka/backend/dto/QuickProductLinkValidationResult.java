package pl.polskaamazonka.backend.dto;

public record QuickProductLinkValidationResult(
        QuickProductLinkValidationStatus status,
        SupportedProductPlatform platform,
        String originalUrl,
        String normalizedUrl,
        String externalProductId,
        String reason
) {
    public static QuickProductLinkValidationResult valid(
            SupportedProductPlatform platform,
            String originalUrl,
            String normalizedUrl,
            String externalProductId
    ) {
        return new QuickProductLinkValidationResult(
                QuickProductLinkValidationStatus.VALID_PRODUCT_URL,
                platform,
                originalUrl,
                normalizedUrl,
                externalProductId,
                null
        );
    }

    public static QuickProductLinkValidationResult invalid(
            String originalUrl,
            SupportedProductPlatform platform,
            String reason
    ) {
        return new QuickProductLinkValidationResult(
                QuickProductLinkValidationStatus.INVALID_PRODUCT_URL,
                platform == null ? SupportedProductPlatform.UNKNOWN : platform,
                originalUrl,
                null,
                null,
                reason
        );
    }

    public static QuickProductLinkValidationResult unsupported(String originalUrl, String reason) {
        return new QuickProductLinkValidationResult(
                QuickProductLinkValidationStatus.UNSUPPORTED_PLATFORM,
                SupportedProductPlatform.UNKNOWN,
                originalUrl,
                null,
                null,
                reason
        );
    }
}
