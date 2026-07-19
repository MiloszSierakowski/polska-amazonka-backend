package pl.polskaamazonka.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LinkCheckerWorkerProperties.class)
public class LinkCheckerWorkerConfig {

    @Bean
    RestClient linkCheckerWorkerRestClient(LinkCheckerWorkerProperties properties) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("LINK_CHECKER_WORKER_TOKEN must be configured");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .requestFactory(requestFactory)
                .build();
    }
}
