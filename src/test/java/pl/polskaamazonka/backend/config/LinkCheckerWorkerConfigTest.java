package pl.polskaamazonka.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkCheckerWorkerConfigTest {

    @Test
    void missingWorkerTokenFailsWithExplicitConfigurationError() {
        LinkCheckerWorkerProperties properties = new LinkCheckerWorkerProperties();
        properties.setToken("   ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LinkCheckerWorkerConfig().linkCheckerWorkerRestClient(properties)
        );

        assertEquals("LINK_CHECKER_WORKER_TOKEN must be configured", exception.getMessage());
    }
}
