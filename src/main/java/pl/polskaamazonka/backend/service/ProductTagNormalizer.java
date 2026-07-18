package pl.polskaamazonka.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.Product;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProductTagNormalizer {
    public static final int MAX_TAGS = 10;
    public static final int MAX_TAG_LENGTH = 50;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ProductTagNormalizer() {
    }

    public static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return List.of();
        }

        List<String> normalizedTags = new ArrayList<>();
        Set<String> comparisonKeys = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                throw badRequest("Tag produktu nie może być pusty.");
            }
            String normalized = WHITESPACE.matcher(tag.trim()).replaceAll(" ");
            if (normalized.isEmpty()) {
                throw badRequest("Tag produktu nie może być pusty.");
            }
            if (normalized.length() > MAX_TAG_LENGTH) {
                throw badRequest("Tag produktu może mieć maksymalnie 50 znaków.");
            }
            if (comparisonKeys.add(normalized.toLowerCase(Locale.ROOT))) {
                normalizedTags.add(normalized);
            }
        }

        if (normalizedTags.size() > MAX_TAGS) {
            throw badRequest("Produkt może mieć maksymalnie 10 tagów.");
        }
        return List.copyOf(normalizedTags);
    }

    public static void replaceTags(Product product, List<String> tags) {
        List<String> normalizedTags = normalize(tags);
        product.replaceTags(normalizedTags);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
