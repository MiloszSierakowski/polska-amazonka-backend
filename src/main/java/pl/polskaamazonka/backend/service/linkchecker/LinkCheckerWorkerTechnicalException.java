package pl.polskaamazonka.backend.service.linkchecker;

public class LinkCheckerWorkerTechnicalException extends RuntimeException {

    public LinkCheckerWorkerTechnicalException(String message) {
        super(message);
    }

    public LinkCheckerWorkerTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}
