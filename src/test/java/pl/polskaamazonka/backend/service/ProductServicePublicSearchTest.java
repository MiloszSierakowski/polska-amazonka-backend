package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.PublicProductDto;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.repository.ProductRepository;

import java.lang.reflect.Method;
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
class ProductServicePublicSearchTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product publicProduct;

    @BeforeEach
    void setUp() {
        publicProduct = product(1L, "Szukany produkt", "https://allegro.pl/oferta/test-123");
    }

    @Test
    void searchPublicByNameOrTagQueryPreservesPublicVisibilityFilters() throws Exception {
        Method method = ProductRepository.class.getDeclaredMethod("searchPublicByNameOrTag", String.class, Pageable.class);
        org.springframework.data.jpa.repository.Query query =
                method.getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertNotNull(query);
        String jpql = query.value();
        String normalizedJpql = jpql.replaceAll("\\s+", " ").trim();
        assertTrue(jpql.contains("SELECT DISTINCT p"));
        assertTrue(jpql.contains("JOIN FETCH p.productLink l"));
        assertTrue(jpql.contains("LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))"));
        assertTrue(jpql.contains("OR EXISTS ("));
        assertTrue(jpql.contains("SELECT t.id FROM ProductTag t"));
        assertTrue(jpql.contains("WHERE t.product = p"));
        assertTrue(jpql.contains("LOWER(t.value) LIKE LOWER(CONCAT('%', :search, '%'))"));
        assertTrue(normalizedJpql.contains(") AND (l.isBroken IS NULL OR l.isBroken = FALSE)"));
        assertFalse(jpql.contains("needsReview"));
        assertTrue(jpql.contains("(l.isActive IS NULL OR l.isActive = TRUE)"));
        assertTrue(jpql.contains("l.url IS NOT NULL"));
        assertTrue(jpql.contains("TRIM(l.url) <> ''"));
        assertFalse(jpql.contains("JOIN FETCH p.tags"));
        assertTrue(jpql.contains("v.isActive = TRUE"));
        assertTrue(jpql.contains("v.publicCode IS NOT NULL"));
        assertTrue(jpql.contains("TRIM(v.publicCode) <> ''"));
        assertTrue(jpql.contains("ORDER BY p.name ASC"));
    }

    @Test
    void searchPublicReturnsEmptyForBlankQuery() {
        assertTrue(productService.searchPublic(null).isEmpty());
        assertTrue(productService.searchPublic("   ").isEmpty());
    }

    @Test
    void searchPublicMapsProductsMatchingName() {
        when(productRepository.searchPublicByNameOrTag(eq("szuk"), any(Pageable.class)))
                .thenReturn(List.of(publicProduct));

        List<PublicProductDto> result = productService.searchPublic("  szuk  ");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Szukany produkt", result.get(0).getName());
        assertEquals("https://img.example/product.jpg", result.get(0).getImageUrl());

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).searchPublicByNameOrTag(searchCaptor.capture(), any(Pageable.class));
        assertEquals("szuk", searchCaptor.getValue());
    }

    @Test
    void searchPublicKeepsProductNeedingReview() {
        publicProduct.getProductLink().setNeedsReview(true);
        when(productRepository.searchPublicByNameOrTag(eq("produkt"), any(Pageable.class)))
                .thenReturn(List.of(publicProduct));

        List<PublicProductDto> result = productService.searchPublic("produkt");

        assertEquals(List.of(1L), result.stream().map(PublicProductDto::getId).toList());
    }

    @Test
    void searchPublicReturnsSingleEntryWhenRepositoryReturnsDistinctProduct() {
        when(productRepository.searchPublicByNameOrTag(eq("produkt"), any(Pageable.class)))
                .thenReturn(List.of(publicProduct));

        List<PublicProductDto> result = productService.searchPublic("produkt");

        assertEquals(1, result.size());
    }

    @Test
    void searchPublicReturnsEmptyWhenRepositoryFindsNoEligibleProducts() {
        when(productRepository.searchPublicByNameOrTag(eq("brak"), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(productService.searchPublic("brak").isEmpty());
    }

    @Test
    void adminGetAllStillUsesFindAll() {
        when(productRepository.findAll()).thenReturn(List.of(publicProduct));

        List<ProductDTO> result = productService.getAll();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(productRepository).findAll();
    }

    @Test
    void adminGetByIdStillUsesFindById() {
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(publicProduct));

        ProductDTO result = productService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(productRepository).findById(1L);
    }

    private Product product(Long id, String name, String url) {
        Link link = new Link();
        link.setId(id + 100);
        link.setUrl(url);
        link.setType("product");
        link.setIsBroken(false);

        Product result = new Product();
        result.setId(id);
        result.setName(name);
        result.setImageUrl("https://img.example/product.jpg");
        result.setProductLink(link);
        return result;
    }
}
