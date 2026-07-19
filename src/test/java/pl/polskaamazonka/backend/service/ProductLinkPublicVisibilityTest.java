package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.Test;
import pl.polskaamazonka.backend.model.Link;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductLinkPublicVisibilityTest {

    @Test
    void acceptsPublicLinkAndHistoricalNullFlags() {
        assertTrue(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, true, "https://example.com")));
        assertTrue(ProductLinkPublicVisibility.isPubliclyAvailable(link(null, false, true, "https://example.com")));
        assertTrue(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, null, true, "https://example.com")));
        assertTrue(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, true, true, "https://example.com")));
        assertTrue(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, null, "https://example.com")));
    }

    @Test
    void rejectsBrokenOrInactiveLink() {
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(true, false, true, "https://example.com")));
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, false, "https://example.com")));
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(true, true, false, "https://example.com")));
    }

    @Test
    void rejectsMissingOrBlankLink() {
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(null));
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, true, null)));
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, true, "")));
        assertFalse(ProductLinkPublicVisibility.isPubliclyAvailable(link(false, false, true, "   ")));
    }

    private Link link(Boolean isBroken, Boolean needsReview, Boolean isActive, String url) {
        Link link = new Link();
        link.setUrl(url);
        link.setIsBroken(isBroken);
        link.setNeedsReview(needsReview);
        link.setIsActive(isActive);
        return link;
    }
}
