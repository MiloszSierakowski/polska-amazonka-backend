package pl.polskaamazonka.backend.service;

import org.springframework.stereotype.Component;
import pl.polskaamazonka.backend.model.Shop;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AffiliateUrlBuilder {

    public String apply(String productUrl, String codeValue, Shop shop) {
        if (productUrl == null || productUrl.isBlank()) {
            return productUrl;
        }
        if (codeValue == null || codeValue.isBlank()) {
            return productUrl.trim();
        }
        if (shop == null || shop.getSlug() == null || shop.getSlug().isBlank()) {
            return productUrl.trim();
        }

        String trimmedCode = codeValue.trim();
        String trimmedProductUrl = productUrl.trim();

        if (trimmedCode.startsWith("http://") || trimmedCode.startsWith("https://")) {
            if (trimmedCode.contains("{url}")) {
                String encodedProductUrl = URLEncoder.encode(trimmedProductUrl, StandardCharsets.UTF_8);
                return trimmedCode.replace("{url}", encodedProductUrl);
            }
            return trimmedCode;
        }

        if (trimmedCode.startsWith("?") || trimmedCode.startsWith("&") || trimmedCode.contains("=")) {
            String queryFragment = trimmedCode.startsWith("?") || trimmedCode.startsWith("&")
                    ? trimmedCode.substring(1)
                    : trimmedCode;
            return mergeQueryString(trimmedProductUrl, queryFragment);
        }

        Map<String, String> params = shopParams(shop.getSlug(), trimmedCode);
        return mergeQueryParams(trimmedProductUrl, params);
    }

    private Map<String, String> shopParams(String slug, String codeValue) {
        Map<String, String> params = new LinkedHashMap<>();
        switch (slug) {
            case "aliexpress" -> {
                params.put("aff_fsk", codeValue);
                params.put("aff_platform", "portals-tool");
            }
            case "temu" -> {
                params.put("_p_rfs", "1");
                params.put("referShareId", codeValue);
            }
            case "amazon" -> params.put("tag", codeValue);
            case "allegro" -> params.put("affiliation", codeValue);
        }
        return params;
    }

    private String mergeQueryString(String baseUrl, String queryFragment) {
        Map<String, String> params = parseQueryString(queryFragment);
        return mergeQueryParams(baseUrl, params);
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new LinkedHashMap<>();
        if (queryString == null || queryString.isBlank()) {
            return params;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = decodeQueryComponent(pair.substring(0, separatorIndex));
            String value = decodeQueryComponent(pair.substring(separatorIndex + 1));
            params.put(key, value);
        }
        return params;
    }

    private String decodeQueryComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String mergeQueryParams(String baseUrl, Map<String, String> params) {
        if (params.isEmpty()) {
            return baseUrl;
        }
        try {
            URI uri = URI.create(baseUrl);
            Map<String, String> mergedParams = new LinkedHashMap<>();
            String existingQuery = uri.getRawQuery();
            if (existingQuery != null && !existingQuery.isBlank()) {
                mergedParams.putAll(parseQueryString(existingQuery));
            }
            mergedParams.putAll(params);

            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, String> entry : mergedParams.entrySet()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                query.append('=');
                query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }

            URI rebuilt = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    query.toString(),
                    uri.getFragment()
            );
            return rebuilt.toString();
        } catch (Exception e) {
            return baseUrl;
        }
    }
}
