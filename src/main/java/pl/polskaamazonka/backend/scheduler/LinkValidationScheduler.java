package pl.polskaamazonka.backend.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.polskaamazonka.backend.service.LinkValidatorService;

@Component
@RequiredArgsConstructor
public class LinkValidationScheduler {

    private final LinkValidatorService linkValidatorService;

    @Scheduled(cron = "0 0 3 * * *")
    public void validateLinksDaily() {
        linkValidatorService.validateAllLinks();
    }
}
