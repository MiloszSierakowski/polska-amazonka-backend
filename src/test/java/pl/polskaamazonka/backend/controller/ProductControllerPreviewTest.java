package pl.polskaamazonka.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.polskaamazonka.backend.dto.ProductPreviewDTO;
import pl.polskaamazonka.backend.service.AffiliateShortLinkResolver;
import pl.polskaamazonka.backend.service.ProductImageFileStorageService;
import pl.polskaamazonka.backend.service.ProductPageScraperService;
import pl.polskaamazonka.backend.service.ProductService;
import pl.polskaamazonka.backend.service.ShortLinkResolution;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.ProductPageData;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductControllerPreviewTest {

    private static final String TEMU_SHORT_URL = "https://share.temu.com/abc123";
    private static final String TEMU_PRODUCT_URL = "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html";

    private ProductPageScraperService productPageScraperService;
    private AffiliateShortLinkResolver affiliateShortLinkResolver;
    private ProductController productController;

    @BeforeEach
    void setUp() {
        productPageScraperService = mock(ProductPageScraperService.class);
        affiliateShortLinkResolver = mock(AffiliateShortLinkResolver.class);
        productController = new ProductController(
                mock(ProductService.class),
                productPageScraperService,
                mock(ProductImageFileStorageService.class),
                affiliateShortLinkResolver,
                new AllegroUrlNormalizer(),
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer()
        );
    }

    @Test
    void previewWithShortLinkUsesExpandedUrlForScraper() {
        when(affiliateShortLinkResolver.resolve(TEMU_SHORT_URL))
                .thenReturn(ShortLinkResolution.success(TEMU_PRODUCT_URL));
        when(productPageScraperService.scrape(TEMU_PRODUCT_URL))
                .thenReturn(new ProductPageData("Produkt Temu", "https://img.example/product.jpg"));
        when(productPageScraperService.detectPlatform(TEMU_PRODUCT_URL)).thenReturn("temu");

        ProductPreviewDTO result = productController.preview(TEMU_SHORT_URL);

        assertEquals("Produkt Temu", result.getName());
        assertEquals("https://img.example/product.jpg", result.getImageUrl());
        assertEquals("temu", result.getPlatform());
        verify(productPageScraperService).scrape(TEMU_PRODUCT_URL);
        verify(productPageScraperService).detectPlatform(TEMU_PRODUCT_URL);
    }
}
