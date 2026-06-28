package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.service.VideoService;

import java.util.List;

@RestController
@RequestMapping("/api/public/videos")
@RequiredArgsConstructor
public class PublicVideoController {

    private final VideoService videoService;

    @GetMapping("/promoted")
    public List<VideoDTO> getPromoted() {
        return videoService.getAllPromotedPublic();
    }

    @GetMapping
    public List<VideoDTO> getAll(@RequestParam(required = false) Long categoryId) {
        return videoService.getAllPublic(categoryId);
    }

    @GetMapping("/{id}")
    public VideoDTO getById(@PathVariable Long id) {
        return videoService.getByIdPublic(id);
    }
}
