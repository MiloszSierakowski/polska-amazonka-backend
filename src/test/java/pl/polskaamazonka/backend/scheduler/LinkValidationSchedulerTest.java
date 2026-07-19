package pl.polskaamazonka.backend.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import pl.polskaamazonka.backend.service.LinkValidatorService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkValidationSchedulerTest {

    @Test
    void scheduleRunsAtThreeInWarsawTimeZone() throws Exception {
        Method method = LinkValidationScheduler.class.getMethod("validateLinksDaily");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 0 3 * * *", scheduled.cron());
        assertEquals("Europe/Warsaw", scheduled.zone());
    }

    @Test
    void skipsRunWhenAnotherInstanceHoldsLock() {
        LinkValidatorService validator = mock(LinkValidatorService.class);
        PostgresAdvisoryLockService lockService = mock(PostgresAdvisoryLockService.class);
        when(lockService.executeWithLock(eq(PostgresAdvisoryLockService.LINK_VALIDATION_LOCK_ID), any()))
                .thenReturn(false);

        new LinkValidationScheduler(validator, lockService).validateLinksDaily();

        verify(validator, never()).validateAllLinks();
    }
}
