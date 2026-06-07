package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.ClickStatRequest;
import pl.polskaamazonka.backend.service.ClickStatService;

@RestController
@RequestMapping("/api/public/click-stats")
@RequiredArgsConstructor
public class PublicClickStatController {

    private final ClickStatService clickStatService;

    @PostMapping
    public ResponseEntity<Void> recordClick(@RequestBody ClickStatRequest request) {
        if (request.getEntityType() == null || request.getEntityType().isBlank() || request.getEntityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType and entityId are required");
        }
        clickStatService.recordClick(request.getEntityType().trim(), request.getEntityId());
        return ResponseEntity.noContent().build();
    }
}
