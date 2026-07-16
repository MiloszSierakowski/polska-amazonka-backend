package pl.polskaamazonka.backend.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class VideoPublicCodeSupport {

    public static final int MAX_LENGTH = 20;

    private static final Pattern FORMAT_PATTERN = Pattern.compile("^[A-Z]+[0-9]+$");

    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String withoutSpaces = raw.replaceAll("\\s+", "");
        if (withoutSpaces.isEmpty()) {
            throw new IllegalArgumentException("Invalid public code format");
        }
        String normalized = withoutSpaces.toUpperCase(Locale.ROOT);
        validateNormalized(normalized);
        return normalized;
    }

    public void validateNormalized(String code) {
        if (code == null) {
            return;
        }
        if (code.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Public code exceeds maximum length");
        }
        if (!FORMAT_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid public code format");
        }
    }
}
