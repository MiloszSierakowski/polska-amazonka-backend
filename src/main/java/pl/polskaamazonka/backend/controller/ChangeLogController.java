package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.ChangeLogDTO;
import pl.polskaamazonka.backend.service.ActivityLogService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/changelogs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChangeLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public List<ChangeLogDTO> getAll() {
        return activityLogService.getRecentLogs();
    }
}
