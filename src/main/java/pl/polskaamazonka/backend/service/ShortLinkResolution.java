package pl.polskaamazonka.backend.service;

public record ShortLinkResolution(
        Status status,
        String expandedUrl,
        String failureReason
) {
    public enum Status {
        NOT_APPLICABLE,
        SUCCESS,
        FAILURE
    }

    public static ShortLinkResolution notApplicable() {
        return new ShortLinkResolution(Status.NOT_APPLICABLE, null, null);
    }

    public static ShortLinkResolution success(String expandedUrl) {
        return new ShortLinkResolution(Status.SUCCESS, expandedUrl, null);
    }

    public static ShortLinkResolution failure(String failureReason) {
        return new ShortLinkResolution(Status.FAILURE, null, failureReason);
    }
}
