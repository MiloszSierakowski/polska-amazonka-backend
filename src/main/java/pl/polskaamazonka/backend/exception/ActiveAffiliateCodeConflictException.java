package pl.polskaamazonka.backend.exception;

public class ActiveAffiliateCodeConflictException extends RuntimeException {

    public ActiveAffiliateCodeConflictException(String message) {
        super(message);
    }
}
