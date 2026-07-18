package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.ProductTag;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTagNormalizerTest {

    @Test
    void nullAndEmptyListsProduceEmptyList() {
        assertEquals(List.of(), ProductTagNormalizer.normalize(null));
        assertEquals(List.of(), ProductTagNormalizer.normalize(List.of()));
    }

    @Test
    void normalizesWhitespaceAndPreservesMultiWordTags() {
        assertEquals(
                List.of("kuchnia", "gąbka do naczyń", "AMA gąbka"),
                ProductTagNormalizer.normalize(List.of("  kuchnia  ", "gąbka\t\n do   naczyń", "AMA gąbka"))
        );
    }

    @Test
    void removesCaseInsensitiveDuplicatesAndPreservesFirstDisplayForm() {
        assertEquals(
                List.of("Gąbka", "KUCHNIA"),
                ProductTagNormalizer.normalize(List.of("Gąbka", "gąbka", "KUCHNIA", "kuchnia"))
        );
    }

    @Test
    void acceptsExactlyTenUniqueTagsInOrder() {
        List<String> tags = tags(10);
        assertEquals(tags, ProductTagNormalizer.normalize(tags));
    }

    @Test
    void rejectsElevenUniqueTags() {
        assertBadRequest(() -> ProductTagNormalizer.normalize(tags(11)));
    }

    @Test
    void acceptsExactlyFiftyCharactersAndRejectsFiftyOne() {
        assertEquals(List.of("a".repeat(50)), ProductTagNormalizer.normalize(List.of("a".repeat(50))));
        assertBadRequest(() -> ProductTagNormalizer.normalize(List.of("a".repeat(51))));
    }

    @Test
    void rejectsNullAndBlankElements() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertBadRequest(() -> ProductTagNormalizer.normalize(withNull));
        assertBadRequest(() -> ProductTagNormalizer.normalize(List.of(" \t\n ")));
    }

    @Test
    void doesNotRemovePolishDiacritics() {
        assertEquals(List.of("gąbka", "gabka"), ProductTagNormalizer.normalize(List.of("gąbka", "gabka")));
    }

    @Test
    void replacingTagsRemovesMissingValuesAndAssignsDisplayOrder() {
        Product product = new Product();
        ProductTagNormalizer.replaceTags(product, List.of("stary", "drugi"));
        ProductTagNormalizer.replaceTags(product, List.of("nowy"));

        assertEquals(List.of("nowy"), product.getTags().stream().map(tag -> tag.getValue()).toList());
        assertEquals(0, product.getTags().get(0).getDisplayOrder());
        assertEquals(product, product.getTags().get(0).getProduct());
    }

    @Test
    void replacingTagsReusesExistingEntitiesAndOnlyCreatesNewValues() {
        Product product = new Product();
        ProductTagNormalizer.replaceTags(product, List.of("Pierwszy", "usuwany"));
        ProductTag retained = product.getTags().get(0);
        retained.setId(101L);
        product.getTags().get(1).setId(102L);

        ProductTagNormalizer.replaceTags(product, List.of("pierwszy", "nowy"));

        assertEquals(2, product.getTags().size());
        assertEquals(retained, product.getTags().get(0));
        assertEquals(101L, product.getTags().get(0).getId());
        assertEquals("pierwszy", product.getTags().get(0).getValue());
        assertEquals(0, product.getTags().get(0).getDisplayOrder());
        assertEquals(null, product.getTags().get(1).getId());
        assertEquals("nowy", product.getTags().get(1).getValue());
        assertEquals(1, product.getTags().get(1).getDisplayOrder());
    }

    @Test
    void invalidReplacementLeavesExistingTagsUnchanged() {
        Product product = new Product();
        ProductTagNormalizer.replaceTags(product, List.of("istniejący"));

        assertBadRequest(() -> ProductTagNormalizer.replaceTags(product, List.of("a".repeat(51))));
        assertEquals(List.of("istniejący"), product.getTags().stream().map(tag -> tag.getValue()).toList());
    }

    @Test
    void sharedProductHasOneGlobalTagCollectionAcrossVideoRelations() {
        Product sharedProduct = new Product();
        ProductTagNormalizer.replaceTags(sharedProduct, List.of("globalny"));

        Product firstReference = sharedProduct;
        Product secondReference = sharedProduct;
        ProductTagNormalizer.replaceTags(firstReference, List.of("zmieniony"));

        assertEquals("zmieniony", secondReference.getTags().get(0).getValue());
    }

    private List<String> tags(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> "tag " + index).toList();
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
