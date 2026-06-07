package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.model.ClickStat;
import pl.polskaamazonka.backend.service.ClickStatService;

import java.util.List;

@RestController
@RequestMapping("/api/click-stats")
@RequiredArgsConstructor
public class ClickStatController {

    private final ClickStatService clickStatService;

    @GetMapping
    public List<ClickStat> getAll() {
        return clickStatService.getAll();
    }

    @GetMapping("/{id}")
    public ClickStat getById(@PathVariable Long id) {
        return clickStatService.getById(id);
    }
}
