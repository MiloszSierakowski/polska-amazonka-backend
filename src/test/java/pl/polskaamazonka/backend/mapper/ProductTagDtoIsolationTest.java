package pl.polskaamazonka.backend.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.PublicProductDto;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.dto.PublicVideoProductDTO;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.VideoProduct;
import pl.polskaamazonka.backend.service.ProductTagNormalizer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTagDtoIsolationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void administrativeProductDtoContainsTagsInDisplayOrder() {
        Product product = new Product();
        Link link = new Link();
        link.setIsBroken(true);
        link.setNeedsReview(true);
        product.setProductLink(link);
        ProductTagNormalizer.replaceTags(product, List.of("Pierwszy", "drugi"));

        ProductDTO dto = ProductMapper.toDTO(product);

        assertEquals(List.of("Pierwszy", "drugi"), dto.getTags());
        assertTrue(dto.getIsBroken());
        assertTrue(dto.getNeedsReview());
        assertTrue(objectMapper.valueToTree(dto).has("tags"));
    }

    @Test
    void publicProductContractsHaveNoTagsProperty() {
        Product product = new Product();
        ProductTagNormalizer.replaceTags(product, List.of("tajny"));
        VideoProduct relation = new VideoProduct();
        relation.setProduct(product);

        PublicVideoProductDTO videoProduct = ProductMapper.toPublicVideoDTO(relation, false);
        PublicVideoDTO video = new PublicVideoDTO();
        video.setProducts(List.of(videoProduct));

        assertFalse(objectMapper.valueToTree(videoProduct).has("tags"));
        assertFalse(objectMapper.valueToTree(videoProduct).has("isBroken"));
        assertFalse(objectMapper.valueToTree(videoProduct).has("needsReview"));
        assertFalse(objectMapper.valueToTree(video).toString().contains("tags"));
        assertFalse(objectMapper.valueToTree(new PublicProductDto()).has("tags"));
    }
}
