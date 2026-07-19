package pl.polskaamazonka.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.polskaamazonka.backend.service.LinkValidatorService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkValidationScheduler {

    private final LinkValidatorService linkValidatorService;
    private final PostgresAdvisoryLockService advisoryLockService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Warsaw")
    public void validateLinksDaily() {
        if (!advisoryLockService.executeWithLock(
                PostgresAdvisoryLockService.LINK_VALIDATION_LOCK_ID,
                linkValidatorService::validateAllLinks
        )) {
            log.info("Skipping scheduled link validation because another instance holds the lock");
        }
    }
}
