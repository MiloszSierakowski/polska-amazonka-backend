package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.model.Videocategory;
import pl.polskaamazonka.backend.service.VideoCategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/video-categories")
@RequiredArgsConstructor
public class VideoCategoryController {

    private final VideoCategoryService videoCategoryService;

    @GetMapping
    public List<Videocategory> getAll() {
        return videoCategoryService.getAll();
    }

    @GetMapping("/{id}")
    public Videocategory getById(@PathVariable Integer id) {
        return videoCategoryService.getById(id);
    }
}
