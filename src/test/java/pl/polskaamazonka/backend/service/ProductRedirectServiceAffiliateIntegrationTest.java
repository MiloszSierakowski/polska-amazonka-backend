package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRedirectServiceAffiliateIntegrationTest {

    private static final String CLEAN_ALIEXPRESS_URL = "https://pl.aliexpress.com/item/1005001234567890.html";
    private static final String AFF_FCID_URL = CLEAN_ALIEXPRESS_URL + "?aff_fcid=network123";
    private static final String AMZN_TO_URL = "https://amzn.to/3abcXYZ";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private AffiliateCodeService affiliateCodeService;
    @Mock
    private ProductPageParserFactory productPageParserFactory;
    @Mock
    private AffiliateUrlBuilder affiliateUrlBuilder;
    @Mock
    private ShopRepository shopRepository;

    private AffiliateTrackingDetector affiliateTrackingDetector;
    private AffiliateShortLinkResolver affiliateShortLinkResolver;
    private ProductRedirectService productRedirectService;

    private Product product;
    private Shop shop;
    private AffiliateCode affiliateCode;

    @BeforeEach
    void setUp() {
        affiliateShortLinkResolver = new AffiliateShortLinkResolver(
                url -> {
                    throw new AssertionError("Short links must not be expanded during redirect.");
                },
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer()
        );
        affiliateTrackingDetector = new AffiliateTrackingDetector(
                affiliateShortLinkResolver,
                new AllegroUrlNormalizer(),
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer()
        );
        productRedirectService = new ProductRedirectService(
                productRepository,
                affiliateCodeService,
                productPageParserFactory,
                affiliateUrlBuilder,
                affiliateTrackingDetector,
                affiliateShortLinkResolver,
                shopRepository
        );

        Link link = new Link();
        link.setUrl(CLEAN_ALIEXPRESS_URL);

        product = new Product();
        product.setId(10L);
        product.setProductLink(link);

        shop = new Shop();
        shop.setId(1L);
        shop.setSlug("aliexpress");

        affiliateCode = new AffiliateCode();
        affiliateCode.setType(AffiliateCodeType.AFFILIATE);
        affiliateCode.setCodeValue("AFF123");
        affiliateCode.setShop(shop);

        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));
    }

    @Test
    void resolveRedirectUrlReturnsStoredUrlWhenDetectorFindsAffiliateTracking() {
        product.getProductLink().setUrl(AFF_FCID_URL);

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(AFF_FCID_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository, productPageParserFactory);
    }

    @Test
    void resolveRedirectUrlReturnsAmznToUnchangedWithoutBuilder() {
        product.getProductLink().setUrl(AMZN_TO_URL);

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(AMZN_TO_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository, productPageParserFactory);
    }

    @Test
    void resolveRedirectUrlUsesBuilderOnlyForCleanUrl() {
        when(productPageParserFactory.detectPlatform(CLEAN_ALIEXPRESS_URL)).thenReturn("aliexpress");
        when(shopRepository.findBySlug("aliexpress")).thenReturn(Optional.of(shop));
        when(affiliateCodeService.getActiveAffiliateCode(shop)).thenReturn(Optional.of(affiliateCode));
        when(affiliateUrlBuilder.apply(CLEAN_ALIEXPRESS_URL, "AFF123", shop))
                .thenReturn(CLEAN_ALIEXPRESS_URL + "?aff_fsk=AFF123&aff_platform=portals-tool");

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(CLEAN_ALIEXPRESS_URL + "?aff_fsk=AFF123&aff_platform=portals-tool", result);
        verify(affiliateUrlBuilder).apply(CLEAN_ALIEXPRESS_URL, "AFF123", shop);
    }
}
