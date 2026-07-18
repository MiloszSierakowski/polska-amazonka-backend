package pl.polskaamazonka.backend.model;

import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTagMappingTest {

    @Test
    void productOwnsTagsWithCascadeAndOrphanRemoval() throws Exception {
        Field tags = Product.class.getDeclaredField("tags");
        OneToMany mapping = tags.getAnnotation(OneToMany.class);

        assertTrue(mapping.orphanRemoval());
        assertTrue(java.util.List.of(mapping.cascade()).contains(jakarta.persistence.CascadeType.ALL));
        assertEquals("product", mapping.mappedBy());
    }
}
