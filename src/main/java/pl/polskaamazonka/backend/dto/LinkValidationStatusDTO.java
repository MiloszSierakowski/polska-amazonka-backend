package pl.polskaamazonka.backend.dto;

public record LinkValidationStatusDTO(
        WorkerReachability workerStatus,
        long workerHealthDurationMs,
        LinkValidationRunDTO lastRun,
        LinkValidationRunDTO lastManualRun,
        LinkValidationRunDTO lastScheduledRun,
        long runningCount,
        String latestError,
        LinkValidationRunDTO lastCompletedRun
) {
    public enum WorkerReachability {
        REACHABLE,
        UNREACHABLE
    }
}
