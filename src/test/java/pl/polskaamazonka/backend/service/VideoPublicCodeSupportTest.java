package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPublicCodeSupportTest {

    private VideoPublicCodeSupport support;

    @BeforeEach
    void setUp() {
        support = new VideoPublicCodeSupport();
    }

    @Test
    void normalize_nullInput_returnsNull() {
        assertNull(support.normalize(null));
    }

    @Test
    void normalize_lowercaseLetters_uppercasesToRootLocale() {
        assertEquals("A110", support.normalize("a110"));
    }

    @Test
    void normalize_withSpaces_removesSpaces() {
        assertEquals("A110", support.normalize(" A 110 "));
    }

    @Test
    void normalize_multiLetterPrefix_isValid() {
        assertEquals("AB120", support.normalize("ab120"));
    }

    @Test
    void normalize_sameCodeDifferentCase_producesIdenticalValueForUniquenessChecks() {
        assertEquals(support.normalize("A110"), support.normalize("a110"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"110A", "A-110", "A_110", "ABC", "123"})
    void normalize_invalidFormat_throws(String raw) {
        assertThrows(IllegalArgumentException.class, () -> support.normalize(raw));
    }

    @Test
    void normalize_tooLongCode_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> support.normalize("ABCDEFGHIJ12345678901")
        );
    }

    @Test
    void validateNormalized_null_isAllowed() {
        support.validateNormalized(null);
    }

    @Test
    void migrationEnforcesUniquePublicCodeConstraint() {
        String migration = readMigration("V28__add_video_public_code.sql");
        assertTrue(
                migration.contains("uq_video_public_code"),
                "Migration must define a unique constraint on public_code"
        );
    }

    private static String readMigration(String fileName) {
        try (var inputStream = VideoPublicCodeSupportTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing migration resource: " + fileName);
            }
            return new String(inputStream.readAllBytes());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read migration: " + fileName, exception);
        }
    }
}
