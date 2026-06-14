package pl.polskaamazonka.backend.service;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
public class ScheduledLinkValidationStats {

    private final Instant startedAt;
    private final int maxLinksPerRun;
    private final long delayMsBetweenChecks;
    private final int minHoursBetweenChecks;

    private int selected;
    private int checked;
    private int working;
    private int broken;
    private int uncertain;
    private int technicalErrors;
    private Instant finishedAt;

    public ScheduledLinkValidationStats(
            Instant startedAt,
            int maxLinksPerRun,
            long delayMsBetweenChecks,
            int minHoursBetweenChecks
    ) {
        this.startedAt = startedAt;
        this.maxLinksPerRun = maxLinksPerRun;
        this.delayMsBetweenChecks = delayMsBetweenChecks;
        this.minHoursBetweenChecks = minHoursBetweenChecks;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public void recordOutcome(ScheduledLinkValidationOutcome outcome) {
        checked++;
        switch (outcome) {
            case WORKING -> working++;
            case BROKEN -> broken++;
            case UNCERTAIN -> uncertain++;
        }
    }

    public void recordTechnicalError() {
        checked++;
        technicalErrors++;
    }

    public void finish() {
        this.finishedAt = Instant.now();
    }

    public long getDurationMs() {
        if (finishedAt == null) {
            return Duration.between(startedAt, Instant.now()).toMillis();
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }
}
