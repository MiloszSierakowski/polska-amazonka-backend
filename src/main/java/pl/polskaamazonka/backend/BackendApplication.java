package pl.polskaamazonka.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.polskaamazonka.backend.config.LinkValidationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LinkValidationProperties.class)
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
