package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LinkValidationRunDTO;
import pl.polskaamazonka.backend.dto.LinkValidationRunDetailsDTO;
import pl.polskaamazonka.backend.dto.LinkValidationRunItemDTO;
import pl.polskaamazonka.backend.dto.LinkValidationStatusDTO;
import pl.polskaamazonka.backend.model.LinkValidationRun;
import pl.polskaamazonka.backend.model.LinkValidationRunItem;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.repository.LinkValidationRunItemRepository;
import pl.polskaamazonka.backend.repository.LinkValidationRunRepository;
import pl.polskaamazonka.backend.service.linkchecker.LinkCheckerWorkerClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkValidationQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final LinkValidationRunRepository runRepository;
    private final LinkValidationRunItemRepository itemRepository;
    private final LinkCheckerWorkerClient workerClient;

    @Transactional(readOnly = true)
    public LinkValidationStatusDTO getStatus() {
        long healthStartedAt = System.nanoTime();
        boolean reachable = workerClient.isReachable();
        long healthDurationMs = Math.max(0L, (System.nanoTime() - healthStartedAt) / 1_000_000L);

        return new LinkValidationStatusDTO(
                reachable
                        ? LinkValidationStatusDTO.WorkerReachability.REACHABLE
                        : LinkValidationStatusDTO.WorkerReachability.UNREACHABLE,
                healthDurationMs,
                runRepository.findFirstByOrderByStartedAtDescIdDesc().map(this::toRunDto).orElse(null),
                runRepository.findFirstBySourceOrderByStartedAtDescIdDesc(ProductLinkVerificationSource.MANUAL)
                        .map(this::toRunDto).orElse(null),
                runRepository.findFirstBySourceOrderByStartedAtDescIdDesc(ProductLinkVerificationSource.SCHEDULED)
                        .map(this::toRunDto).orElse(null),
                runRepository.countByStatus(LinkValidationRunStatus.RUNNING),
                runRepository.findFirstByLastErrorIsNotNullOrderByStartedAtDescIdDesc()
                        .map(LinkValidationRun::getLastError).orElse(null),
                runRepository.findFirstByStatusNotOrderByStartedAtDescIdDesc(LinkValidationRunStatus.RUNNING)
                        .map(this::toRunDto).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<LinkValidationRunDTO> getRuns(Integer requestedLimit) {
        int limit = requestedLimit == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
        return runRepository.findAllByOrderByStartedAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(this::toRunDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public LinkValidationRunDetailsDTO getRun(Long runId) {
        LinkValidationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Historia weryfikacji nie istnieje."));
        List<LinkValidationRunItemDTO> items = itemRepository
                .findByRun_IdOrderByCheckedAtAscIdAsc(runId)
                .stream()
                .map(this::toItemDto)
                .toList();
        return new LinkValidationRunDetailsDTO(toRunDto(run), items);
    }

    private LinkValidationRunDTO toRunDto(LinkValidationRun run) {
        return new LinkValidationRunDTO(
                run.getId(),
                run.getSource(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getSelectedCount(),
                run.getCheckedCount(),
                run.getWorkingCount(),
                run.getBrokenCount(),
                run.getUncertainCount(),
                run.getBlockedCount(),
                run.getTechnicalErrorCount(),
                run.getLastError(),
                run.getTriggeredBy()
        );
    }

    private LinkValidationRunItemDTO toItemDto(LinkValidationRunItem item) {
        return new LinkValidationRunItemDTO(
                item.getId(),
                item.getLinkId(),
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getOriginalUrl(),
                item.getNormalizedUrl(),
                item.getFinalUrl(),
                item.getVerificationStatus(),
                item.getReason(),
                item.getHttpStatus(),
                item.getDurationMs(),
                item.getCheckedAt(),
                item.isTechnicalError(),
                item.getPreviousIsBroken(),
                item.getNewIsBroken(),
                item.getPreviousNeedsReview(),
                item.getNewNeedsReview()
        );
    }
}
