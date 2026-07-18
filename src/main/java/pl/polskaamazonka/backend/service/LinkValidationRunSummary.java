package pl.polskaamazonka.backend.service;

public record LinkValidationRunSummary(
        int selected,
        int checked,
        int working,
        int broken,
        int uncertain,
        int blocked,
        int technicalErrors
) {
    public static LinkValidationRunSummary forSingle(ProductLinkVerificationResult result) {
        return new LinkValidationRunSummary(
                1,
                1,
                result.status() == ProductLinkVerificationStatus.WORKING ? 1 : 0,
                result.status() == ProductLinkVerificationStatus.BROKEN ? 1 : 0,
                result.status() == ProductLinkVerificationStatus.UNCERTAIN ? 1 : 0,
                result.status() == ProductLinkVerificationStatus.BLOCKED ? 1 : 0,
                result.status() == ProductLinkVerificationStatus.TECHNICAL_ERROR ? 1 : 0
        );
    }
}
