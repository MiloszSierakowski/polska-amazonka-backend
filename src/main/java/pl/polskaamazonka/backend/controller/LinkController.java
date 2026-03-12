package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.service.LinkService;

import java.util.List;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @GetMapping
    public List<LinkDTO> getAll() {
        return linkService.getAll();
    }

    @GetMapping("/{id}")
    public LinkDTO getById(@PathVariable Integer id) {
        return linkService.getById(id);
    }

    @GetMapping(params = "type")
    public List<LinkDTO> getByType(@RequestParam String type) {
        return linkService.getByType(type);
    }

}
