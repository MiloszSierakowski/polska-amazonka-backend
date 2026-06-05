package pl.polskaamazonka.backend.service.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class MetaPageDataExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractTitle(Document document) {
        String title = metaContent(document, "og:title", true);
        if (title == null || title.isBlank()) {
            title = metaContent(document, "twitter:title", false);
        }
        if (title == null || title.isBlank()) {
            title = metaContent(document, "twitter:title", true);
        }
        if (title == null || title.isBlank()) {
            title = metaContent(document, "title", false);
        }
        if (title == null || title.isBlank()) {
            title = extractJsonLdField(document, "name");
        }
        if (title == null || title.isBlank()) {
            title = document.title();
        }
        return title;
    }

    public String extractImage(Document document) {
        String imageUrl = metaContent(document, "og:image", true);
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "og:image:secure_url", true);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "twitter:image", false);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "twitter:image", true);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "twitter:image:src", false);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            Elements links = document.select("link[rel=image_src]");
            for (Element link : links) {
                String href = link.attr("href");
                if (href != null && !href.isBlank()) {
                    imageUrl = href;
                    break;
                }
            }
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = extractJsonLdField(document, "image");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }

    private String metaContent(Document document, String key, boolean property) {
        String selector = property
                ? String.format("meta[property=%s]", key)
                : String.format("meta[name=%s]", key);
        Elements elements = document.select(selector);
        for (Element element : elements) {
            String content = element.attr("content");
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
        }
        return null;
    }

    private String extractJsonLdField(Document document, String field) {
        Elements scripts = document.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                String value = findJsonLdValue(root, field);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String findJsonLdValue(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String value = findJsonLdValue(child, field);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
        if (node.has("@graph")) {
            return findJsonLdValue(node.get("@graph"), field);
        }
        if (isProductNode(node)) {
            JsonNode fieldNode = node.get(field);
            String direct = readJsonLdText(fieldNode);
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
        }
        if (node.isObject()) {
            JsonNode fieldNode = node.get(field);
            String direct = readJsonLdText(fieldNode);
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
        }
        return null;
    }

    private boolean isProductNode(JsonNode node) {
        if (!node.has("@type")) {
            return false;
        }
        JsonNode typeNode = node.get("@type");
        if (typeNode.isTextual()) {
            return "Product".equalsIgnoreCase(typeNode.asText());
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                if (type.isTextual() && "Product".equalsIgnoreCase(type.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String readJsonLdText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray() && !node.isEmpty()) {
            return readJsonLdText(node.get(0));
        }
        if (node.isObject()) {
            if (node.has("url")) {
                return node.get("url").asText();
            }
            if (node.has("@id")) {
                return node.get("@id").asText();
            }
        }
        return null;
    }
}
