package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.BrokenLinkProductDTO;
import pl.polskaamazonka.backend.service.BrokenLinkService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/broken-links")
@RequiredArgsConstructor
public class AdminBrokenLinkController {

    private final BrokenLinkService brokenLinkService;

    @GetMapping
    public List<BrokenLinkProductDTO> getAll() {
        return brokenLinkService.getLinksNeedingReview();
    }
}
