package pl.polskaamazonka.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "link-checker.worker")
public class LinkCheckerWorkerProperties {

    private String baseUrl = "http://localhost:3001";
    private String token;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(45);
}
