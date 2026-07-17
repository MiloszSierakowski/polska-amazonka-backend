package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.ProductRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRedirectServiceTest {

    private static final String ALIEXPRESS_URL = "https://pl.aliexpress.com/item/1005001234567890.html";
    private static final String ALLEGRO_URL = "https://allegro.pl/oferta/produkt-1234567890";
    private static final String TEMU_URL = "https://www.temu.com/pl/product-1234567890.html";
    private static final String AMAZON_URL = "https://www.amazon.pl/dp/B012345678";

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductRedirectService productRedirectService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(10L);
    }

    @Test
    void resolveRedirectUrlForAliExpressReturnsExactStoredUrl() {
        setProductUrl(ALIEXPRESS_URL);
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(ALIEXPRESS_URL, result);
    }

    @Test
    void resolveRedirectUrlForAllegroReturnsExactStoredUrl() {
        setProductUrl(ALLEGRO_URL);
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(ALLEGRO_URL, result);
    }

    @Test
    void resolveRedirectUrlForTemuReturnsExactStoredUrl() {
        setProductUrl(TEMU_URL);
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(TEMU_URL, result);
    }

    @Test
    void resolveRedirectUrlForAmazonReturnsExactStoredUrl() {
        setProductUrl(AMAZON_URL);
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(AMAZON_URL, result);
    }

    @Test
    void resolveRedirectUrlForMissingProductThrowsNotFound() {
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productRedirectService.resolveRedirectUrl(10L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void resolveRedirectUrlForMissingLinkThrowsNotFound() {
        product.setProductLink(null);
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productRedirectService.resolveRedirectUrl(10L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void resolveRedirectUrlForBlankLinkThrowsNotFound() {
        setProductUrl("   ");
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productRedirectService.resolveRedirectUrl(10L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void resolveRedirectUrlTrimsStoredUrl() {
        setProductUrl("  " + ALIEXPRESS_URL + "  ");
        when(productRepository.findByIdWithProductLink(10L)).thenReturn(Optional.of(product));

        String result = productRedirectService.resolveRedirectUrl(10L);

        assertEquals(ALIEXPRESS_URL, result);
    }

    private void setProductUrl(String url) {
        Link link = new Link();
        link.setUrl(url);
        product.setProductLink(link);
    }
}
