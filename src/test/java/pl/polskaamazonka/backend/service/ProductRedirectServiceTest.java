package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRedirectServiceTest {

    private static final String PRODUCT_URL = "https://pl.aliexpress.com/item/1005001234567890.html";

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
    }

    @Test
    void resolveRedirectUrlAppliesActiveAffiliateCode() {
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));
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
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));
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
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));
        when(productPageParserFactory.detectPlatform(PRODUCT_URL)).thenReturn("generic");

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(PRODUCT_URL, result);
        verifyNoInteractions(affiliateCodeService, affiliateUrlBuilder, shopRepository);
    }
}
