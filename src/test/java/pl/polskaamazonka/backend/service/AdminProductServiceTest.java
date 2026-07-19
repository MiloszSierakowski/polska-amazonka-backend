package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import pl.polskaamazonka.backend.dto.AdminProductSearchResultDTO;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.ProductTag;
import pl.polskaamazonka.backend.repository.ProductRepository;
import pl.polskaamazonka.backend.repository.VideoProductRepository;
import pl.polskaamazonka.backend.repository.VideoRepository;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private VideoProductRepository videoProductRepository;
    @Mock private VideoRepository videoRepository;
    @InjectMocks private AdminProductService service;

    @Test
    void querySearchesNameAndTagWithoutUrlOrTagJoinDuplicates() throws Exception {
        Method method = ProductRepository.class.getDeclaredMethod(
                "searchAdminProductIds", String.class, Pageable.class
        );
        org.springframework.data.jpa.repository.Query query =
                method.getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertNotNull(query);
        String jpql = query.value();
        assertTrue(jpql.contains(":query = ''"));
        assertTrue(jpql.contains("LOWER(p.name)"));
        assertFalse(jpql.contains("LOWER(l.url)"));
        assertTrue(jpql.contains("OR EXISTS ("));
        assertTrue(jpql.contains("LOWER(t.value)"));
        assertFalse(jpql.contains("JOIN p.tags"));
    }

    @Test
    void searchTrimsQueryLimitsResultsAndMapsAdministrativeState() {
        Instant checkedAt = Instant.parse("2026-07-19T10:00:00Z");
        Product product = product(7L, checkedAt);
        when(videoRepository.existsById(2L)).thenReturn(true);
        when(productRepository.searchAdminProductIds(eq("gąbka"), any(Pageable.class)))
                .thenReturn(List.of(7L));
        when(productRepository.findByIdIn(List.of(7L))).thenReturn(List.of(product));
        when(videoProductRepository.findProductIdsAssignedToVideo(2L, List.of(7L)))
                .thenReturn(List.of(7L));

        List<AdminProductSearchResultDTO> result = service.search("  gąbka  ", 2L, 0, 1000);

        assertEquals(1, result.size());
        AdminProductSearchResultDTO dto = result.get(0);
        assertEquals(7L, dto.getProductId());
        assertEquals(List.of("Gąbka", "Kuchnia"), dto.getTags());
        assertTrue(dto.isAlreadyAssigned());
        assertTrue(dto.getIsBroken());
        assertTrue(dto.getNeedsReview());
        assertFalse(dto.getIsActive());
        assertEquals(checkedAt, dto.getLastCheckedAt());
        verify(productRepository).searchAdminProductIds(eq("gąbka"), any(Pageable.class));
    }

    @Test
    void emptyQueryReturnsStableFirstLimitedPage() {
        Product first = product(1L, null);
        Product second = product(2L, null);
        when(videoRepository.existsById(2L)).thenReturn(true);
        when(productRepository.searchAdminProductIds(eq(""), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        when(productRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(second, first));
        when(videoProductRepository.findProductIdsAssignedToVideo(2L, List.of(1L, 2L)))
                .thenReturn(List.of());

        List<AdminProductSearchResultDTO> result = service.search("  ", 2L, 0, 25);

        assertEquals(List.of(1L, 2L), result.stream().map(AdminProductSearchResultDTO::getProductId).toList());
    }

    private Product product(Long id, Instant checkedAt) {
        Link link = new Link();
        link.setId(12L);
        link.setUrl("https://allegro.pl/oferta/gabka-123");
        link.setType("product");
        link.setIsBroken(true);
        link.setNeedsReview(true);
        link.setIsActive(false);
        link.setLastCheckedAt(checkedAt);
        Product product = new Product();
        product.setId(id);
        product.setName("Gąbka");
        product.setProductLink(link);
        product.replaceTags(List.of("Gąbka", "Kuchnia"));
        return product;
    }
}
