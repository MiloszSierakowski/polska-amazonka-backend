package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.model.Clickstat;
import pl.polskaamazonka.backend.service.ClickStatService;

import java.util.List;

@RestController
@RequestMapping("/api/click-stats")
@RequiredArgsConstructor
public class ClickStatController {

    private final ClickStatService clickStatService;

    @GetMapping
    public List<Clickstat> getAll() {
        return clickStatService.getAll();
    }

    @GetMapping("/{id}")
    public Clickstat getById(@PathVariable Integer id) {
        return clickStatService.getById(id);
    }
}
