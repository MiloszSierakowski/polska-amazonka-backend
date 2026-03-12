package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.service.VideoService;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public List<VideoDTO> getAll() {
        return videoService.getAll();
    }

    @GetMapping("/{id}")
    public VideoDTO getById(@PathVariable Integer id) {
        return videoService.getById(id);
    }
}
