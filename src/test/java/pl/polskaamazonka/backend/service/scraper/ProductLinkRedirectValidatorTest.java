package pl.polskaamazonka.backend.service.scraper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductLinkRedirectValidatorTest {

    private ProductLinkRedirectValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProductLinkRedirectValidator(
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer(),
                new AllegroUrlNormalizer()
        );
    }

    @Test
    void shareTemuExpandedToProductIsNotSuspicious() {
        assertFalse(validator.isSuspiciousRedirect(
                "https://share.temu.com/abc123",
                "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html"
        ));
    }

    @Test
    void aliExpressClickExpandedToProductIsNotSuspicious() {
        assertFalse(validator.isSuspiciousRedirect(
                "https://s.click.aliexpress.com/e/_c2x0Gp5j",
                "https://pl.aliexpress.com/item/1005001234567890.html"
        ));
    }

    @Test
    void shortLinkExpandedToHomepageIsSuspicious() {
        assertTrue(validator.isSuspiciousRedirect(
                "https://share.temu.com/abc123",
                "https://www.temu.com/"
        ));
    }

    @Test
    void shortLinkExpandedToSearchIsSuspicious() {
        assertTrue(validator.isSuspiciousRedirect(
                "https://s.click.aliexpress.com/e/_c2x0Gp5j",
                "https://www.aliexpress.com/search?q=phone"
        ));
    }
}
