package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.LinkValidationRunDTO;
import pl.polskaamazonka.backend.dto.LinkValidationRunDetailsDTO;
import pl.polskaamazonka.backend.dto.LinkValidationStatusDTO;
import pl.polskaamazonka.backend.service.LinkValidationQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/link-validation")
@PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
@RequiredArgsConstructor
public class AdminLinkValidationController {

    private final LinkValidationQueryService queryService;

    @GetMapping("/status")
    public LinkValidationStatusDTO getStatus() {
        return queryService.getStatus();
    }

    @GetMapping("/runs")
    public List<LinkValidationRunDTO> getRuns(@RequestParam(required = false) Integer limit) {
        return queryService.getRuns(limit);
    }

    @GetMapping("/runs/{runId}")
    public LinkValidationRunDetailsDTO getRun(@PathVariable Long runId) {
        return queryService.getRun(runId);
    }
}
