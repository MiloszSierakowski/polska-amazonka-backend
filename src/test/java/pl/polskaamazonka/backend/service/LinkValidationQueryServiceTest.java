package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LinkValidationRunDTO;
import pl.polskaamazonka.backend.dto.LinkValidationRunDetailsDTO;
import pl.polskaamazonka.backend.dto.LinkValidationStatusDTO;
import pl.polskaamazonka.backend.model.LinkValidationRun;
import pl.polskaamazonka.backend.model.LinkValidationRunItem;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.repository.LinkValidationRunItemRepository;
import pl.polskaamazonka.backend.repository.LinkValidationRunRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkValidationQueryServiceTest {

    @Mock private LinkValidationRunRepository runRepository;
    @Mock private LinkValidationRunItemRepository itemRepository;
    @Mock private LinkCheckerWorkerClient workerClient;

    private LinkValidationQueryService service;

    @BeforeEach
    void setUp() {
        service = new LinkValidationQueryService(runRepository, itemRepository, workerClient);
    }

    @Test
    void statusReportsReachableWorkerAndEmptyHistory() {
        when(workerClient.isReachable()).thenReturn(true);

        LinkValidationStatusDTO status = service.getStatus();

        assertEquals(LinkValidationStatusDTO.WorkerReachability.REACHABLE, status.workerStatus());
        assertTrue(status.workerHealthDurationMs() >= 0L);
        assertNull(status.lastRun());
        assertNull(status.lastManualRun());
        assertNull(status.lastScheduledRun());
        assertNull(status.lastCompletedRun());
        assertNull(status.latestError());
    }

    @Test
    void statusReportsUnreachableWorkerWithoutFailingHistoryResponse() {
        when(workerClient.isReachable()).thenReturn(false);

        LinkValidationStatusDTO status = service.getStatus();

        assertEquals(LinkValidationStatusDTO.WorkerReachability.UNREACHABLE, status.workerStatus());
    }

    @Test
    void statusReturnsLatestRunsRunningCountAndLatestError() {
        LinkValidationRun latest = run(3L, ProductLinkVerificationSource.MANUAL, LinkValidationRunStatus.RUNNING);
        LinkValidationRun manual = run(2L, ProductLinkVerificationSource.MANUAL, LinkValidationRunStatus.COMPLETED);
        LinkValidationRun scheduled = run(1L, ProductLinkVerificationSource.SCHEDULED, LinkValidationRunStatus.COMPLETED_WITH_ERRORS);
        scheduled.setLastError("safe error");
        when(runRepository.findFirstByOrderByStartedAtDescIdDesc()).thenReturn(Optional.of(latest));
        when(runRepository.findFirstBySourceOrderByStartedAtDescIdDesc(ProductLinkVerificationSource.MANUAL))
                .thenReturn(Optional.of(manual));
        when(runRepository.findFirstBySourceOrderByStartedAtDescIdDesc(ProductLinkVerificationSource.SCHEDULED))
                .thenReturn(Optional.of(scheduled));
        when(runRepository.countByStatus(LinkValidationRunStatus.RUNNING)).thenReturn(2L);
        when(runRepository.findFirstByLastErrorIsNotNullOrderByStartedAtDescIdDesc()).thenReturn(Optional.of(scheduled));
        when(runRepository.findFirstByStatusNotOrderByStartedAtDescIdDesc(LinkValidationRunStatus.RUNNING))
                .thenReturn(Optional.of(manual));

        LinkValidationStatusDTO status = service.getStatus();

        assertEquals(3L, status.lastRun().id());
        assertEquals(2L, status.lastManualRun().id());
        assertEquals(1L, status.lastScheduledRun().id());
        assertEquals(2L, status.runningCount());
        assertEquals("safe error", status.latestError());
        assertEquals(2L, status.lastCompletedRun().id());
    }

    @Test
    void runsUsesDefaultLimitTwentyAndReturnsDtos() {
        when(runRepository.findAllByOrderByStartedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(List.of(
                        run(2L, ProductLinkVerificationSource.MANUAL, LinkValidationRunStatus.COMPLETED),
                        run(1L, ProductLinkVerificationSource.SCHEDULED, LinkValidationRunStatus.COMPLETED)
                ));

        List<LinkValidationRunDTO> result = service.getRuns(null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findAllByOrderByStartedAtDescIdDesc(captor.capture());
        assertEquals(20, captor.getValue().getPageSize());
        assertInstanceOf(LinkValidationRunDTO.class, result.get(0));
        assertEquals(List.of(2L, 1L), result.stream().map(LinkValidationRunDTO::id).toList());
    }

    @Test
    void runsClampsLimitToOneAndOneHundred() {
        when(runRepository.findAllByOrderByStartedAtDescIdDesc(any(Pageable.class))).thenReturn(List.of());

        service.getRuns(0);
        service.getRuns(500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository, org.mockito.Mockito.times(2))
                .findAllByOrderByStartedAtDescIdDesc(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getPageSize());
        assertEquals(100, captor.getAllValues().get(1).getPageSize());
    }

    @Test
    void runDetailsReturnsDtoItemsInRepositoryOrder() {
        LinkValidationRun run = run(10L, ProductLinkVerificationSource.SCHEDULED, LinkValidationRunStatus.COMPLETED);
        LinkValidationRunItem earlier = item(1L, Instant.parse("2026-07-18T10:00:00Z"));
        LinkValidationRunItem later = item(2L, Instant.parse("2026-07-18T10:01:00Z"));
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        when(itemRepository.findByRun_IdOrderByCheckedAtAscIdAsc(10L)).thenReturn(List.of(earlier, later));

        LinkValidationRunDetailsDTO details = service.getRun(10L);

        assertInstanceOf(LinkValidationRunDTO.class, details.run());
        assertEquals(List.of(1L, 2L), details.items().stream().map(item -> item.id()).toList());
        verify(itemRepository).findByRun_IdOrderByCheckedAtAscIdAsc(10L);
    }

    @Test
    void missingRunReturnsNotFound() {
        when(runRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getRun(404L));

        assertEquals(404, exception.getStatusCode().value());
    }

    private static LinkValidationRun run(
            Long id,
            ProductLinkVerificationSource source,
            LinkValidationRunStatus status
    ) {
        LinkValidationRun run = new LinkValidationRun();
        run.setId(id);
        run.setSource(source);
        run.setStatus(status);
        run.setStartedAt(Instant.parse("2026-07-18T10:00:00Z").plusSeconds(id));
        return run;
    }

    private static LinkValidationRunItem item(Long id, Instant checkedAt) {
        LinkValidationRunItem item = new LinkValidationRunItem();
        item.setId(id);
        item.setVerificationStatus(ProductLinkVerificationStatus.WORKING);
        item.setCheckedAt(checkedAt);
        return item;
    }
}
