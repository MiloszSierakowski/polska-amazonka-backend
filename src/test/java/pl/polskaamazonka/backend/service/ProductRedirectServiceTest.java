package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.polskaamazonka.backend.model.AffiliateCode;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Shop;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.ShopRepository;
import pl.polskaamazonka.backend.service.scraper.ProductPageParserFactory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductRedirectServiceTest {

    private static final String PRODUCT_URL = "https://pl.aliexpress.com/item/1005001234567890.html";
    private static final String AFFILIATED_URL = PRODUCT_URL + "?aff_fsk=EXISTING&aff_platform=portals-tool";
    private static final String ALIEXPRESS_SHORT_URL = "https://s.click.aliexpress.com/e/_abc123";
    private static final String TEMU_SHORT_URL = "https://share.temu.com/abc123";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private AffiliateCodeService affiliateCodeService;
    @Mock
    private ProductPageParserFactory productPageParserFactory;
    @Mock
    private AffiliateUrlBuilder affiliateUrlBuilder;
    @Mock
    private AffiliateTrackingDetector affiliateTrackingDetector;
    @Mock
    private AffiliateShortLinkResolver affiliateShortLinkResolver;
    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private ProductRedirectService productRedirectService;

    private Product product;
    private Shop shop;
    private AffiliateCode affiliateCode;

    @BeforeEach
    void setUp() {
        Link link = new Link();
        link.setUrl(PRODUCT_URL);

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
        when(affiliateShortLinkResolver.isWhitelistedShortLink(anyString())).thenReturn(false);
        when(affiliateTrackingDetector.hasExistingAffiliateTracking(anyString())).thenReturn(false);
    }

    @Test
    void resolveRedirectUrlAppliesActiveAffiliateCode() {
        when(productPageParserFactory.detectPlatform(PRODUCT_URL)).thenReturn("aliexpress");
        when(shopRepository.findBySlug("aliexpress")).thenReturn(Optional.of(shop));
        when(affiliateCodeService.getActiveAffiliateCode(shop)).thenReturn(Optional.of(affiliateCode));
        when(affiliateUrlBuilder.apply(PRODUCT_URL, "AFF123", shop))
                .thenReturn(PRODUCT_URL + "?aff_fsk=AFF123&aff_platform=portals-tool");

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(PRODUCT_URL + "?aff_fsk=AFF123&aff_platform=portals-tool", result);
        verify(affiliateCodeService).getActiveAffiliateCode(shop);
    }

    @Test
    void resolveRedirectUrlWithoutAffiliateCodeReturnsOriginalUrl() {
        when(productPageParserFactory.detectPlatform(PRODUCT_URL)).thenReturn("aliexpress");
        when(shopRepository.findBySlug("aliexpress")).thenReturn(Optional.of(shop));
        when(affiliateCodeService.getActiveAffiliateCode(shop)).thenReturn(Optional.empty());

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(PRODUCT_URL, result);
        verify(affiliateCodeService).getActiveAffiliateCode(shop);
        verifyNoInteractions(affiliateUrlBuilder);
    }

    @Test
    void resolveRedirectUrlWithoutDetectedShopReturnsOriginalUrl() {
        when(productPageParserFactory.detectPlatform(PRODUCT_URL)).thenReturn("generic");

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(PRODUCT_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository);
    }

    @Test
    void resolveRedirectUrlWithExistingAffiliateParamsReturnsStoredUrlUnchanged() {
        product.getProductLink().setUrl(AFFILIATED_URL);
        when(affiliateTrackingDetector.hasExistingAffiliateTracking(AFFILIATED_URL)).thenReturn(true);

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(AFFILIATED_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository, productPageParserFactory);
    }

    @Test
    void resolveRedirectUrlForAliExpressShortLinkReturnsStoredUrlUnchanged() {
        product.getProductLink().setUrl(ALIEXPRESS_SHORT_URL);
        when(affiliateShortLinkResolver.isWhitelistedShortLink(ALIEXPRESS_SHORT_URL)).thenReturn(true);

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(ALIEXPRESS_SHORT_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository, productPageParserFactory);
    }

    @Test
    void resolveRedirectUrlForTemuShortLinkReturnsStoredUrlUnchanged() {
        product.getProductLink().setUrl(TEMU_SHORT_URL);
        when(affiliateShortLinkResolver.isWhitelistedShortLink(TEMU_SHORT_URL)).thenReturn(true);

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(TEMU_SHORT_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository, productPageParserFactory);
    }
}
