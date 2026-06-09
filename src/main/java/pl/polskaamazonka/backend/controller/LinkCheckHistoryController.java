package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.model.Linkcheckhistory;
import pl.polskaamazonka.backend.service.LinkCheckHistoryService;

import java.util.List;

@RestController
@RequestMapping("/api/link-check-history")
@RequiredArgsConstructor
public class LinkCheckHistoryController {

    private final LinkCheckHistoryService linkCheckHistoryService;

    @GetMapping
    public List<Linkcheckhistory> getAll() {
        return linkCheckHistoryService.getAll();
    }

    @GetMapping("/{id}")
    public Linkcheckhistory getById(@PathVariable Long id) {
        return linkCheckHistoryService.getById(id);
    }
}
