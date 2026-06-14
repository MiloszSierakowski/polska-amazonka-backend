package pl.polskaamazonka.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "link.validation")
public class LinkValidationProperties {

    private int maxLinksPerRun = 25;
    private long delayMsBetweenChecks = 2000;
    private int minHoursBetweenChecks = 24;
}
