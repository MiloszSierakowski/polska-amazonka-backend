package pl.polskaamazonka.backend.service;



import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;

import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;

import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;

import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;



import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertTrue;



class AffiliateTrackingDetectorTest {



    private static final String ALIEXPRESS_ITEM = "https://www.aliexpress.com/item/1005001234567890.html";

    private static final String TEMU_ITEM = "https://www.temu.com/pl/produkt-g-601099999999999.html";

    private static final String AMAZON_ITEM = "https://www.amazon.pl/dp/B012345678";

    private static final String ALLEGRO_ITEM = "https://allegro.pl/oferta/produkt-123456789";



    private AffiliateTrackingDetector detector;



    @BeforeEach

    void setUp() {

        detector = new AffiliateTrackingDetector(

                new AffiliateShortLinkResolver(

                        url -> {

                            throw new AssertionError("Short link expansion should not run during detection.");

                        },

                        new AliExpressUrlNormalizer(),

                        new TemuUrlNormalizer()

                ),

                new AllegroUrlNormalizer(),

                new AliExpressUrlNormalizer(),

                new TemuUrlNormalizer(),

                new AmazonUrlNormalizer()

        );

    }



    @Test

    void aliExpressAffFskIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?aff_fsk=EXISTING"));

    }



    @Test

    void aliExpressAffPlatformIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?aff_platform=portals-tool"));

    }



    @Test

    void aliExpressAffFcidIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?aff_fcid=network123"));

    }



    @Test

    void aliExpressAffTraceKeyIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?aff_trace_key=trace123"));

    }



    @Test

    void aliExpressAffShortKeyIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?aff_short_key=short123"));

    }



    @Test

    void aliExpressTerminalIdIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?terminal_id=term123"));

    }



    @Test

    void aliExpressClickShortLinkIsTreatedAsAffiliated() {

        assertTrue(detector.hasExistingAffiliateTracking("https://s.click.aliexpress.com/e/_abc123"));

    }



    @Test

    void aliExpressStarShortLinkIsTreatedAsAffiliated() {

        assertTrue(detector.hasExistingAffiliateTracking("https://star.aliexpress.com/share/abc123"));

    }



    @Test

    void aliExpressSpmOnlyIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?spm=a2g0o"));

    }



    @Test

    void temuReferShareIdIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(TEMU_ITEM + "?referShareId=abc"));

    }



    @Test

    void temuPRfsIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(TEMU_ITEM + "?_p_rfs=1"));

    }



    @Test

    void temuShareShortLinkIsTreatedAsAffiliated() {

        assertTrue(detector.hasExistingAffiliateTracking("https://share.temu.com/abc123"));

    }



    @Test

    void temuToShortLinkIsTreatedAsAffiliated() {

        assertTrue(detector.hasExistingAffiliateTracking("https://temu.to/abc123"));

    }



    @Test

    void amazonTagIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(AMAZON_ITEM + "?tag=existing-21"));

    }



    @Test

    void amazonAmznToShortLinkIsTreatedAsAffiliated() {

        assertTrue(detector.hasExistingAffiliateTracking("https://amzn.to/3abcXYZ"));

    }



    @Test

    void allegroAffiliationIsDetected() {

        assertTrue(detector.hasExistingAffiliateTracking(ALLEGRO_ITEM + "?affiliation=existing"));

    }



    @Test

    void paramNameIsCaseInsensitive() {

        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?AFF_FCID=network123"));

    }



    @Test

    void paramOrderDoesNotMatter() {

        assertTrue(detector.hasExistingAffiliateTracking(

                ALIEXPRESS_ITEM + "?spm=a2g0o&aff_fcid=network123&utm_source=test"

        ));

    }



    @Test
    void encodedParamNameIsDetected() {
        assertTrue(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM + "?%61ff_fcid=network123"));
    }

    @Test
    void tagOnNonAmazonDomainIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking("https://example.com/product?tag=affiliate-21"));

    }



    @Test

    void affFcidOnNonAliExpressDomainIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking("https://example.com/product?aff_fcid=network123"));

    }



    @Test

    void utmSourceIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking(ALLEGRO_ITEM + "?utm_source=test"));

    }



    @Test

    void refIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking(ALLEGRO_ITEM + "?ref=abc"));

    }



    @Test

    void sourceIsNotAffiliated() {

        assertFalse(detector.hasExistingAffiliateTracking(ALLEGRO_ITEM + "?source=newsletter"));

    }



    @Test

    void cleanProductUrlHasNoAffiliation() {

        assertFalse(detector.hasExistingAffiliateTracking(ALIEXPRESS_ITEM));

    }

}


