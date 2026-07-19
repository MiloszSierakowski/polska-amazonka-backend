package pl.polskaamazonka.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "link-checker.worker.token=test-worker-token")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
