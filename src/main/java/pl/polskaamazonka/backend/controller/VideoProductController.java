package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.VideoProductDTO;
import pl.polskaamazonka.backend.service.VideoProductService;

import java.util.List;

@RestController
@RequestMapping("/api/video-products")
@RequiredArgsConstructor
public class VideoProductController {

    private final VideoProductService service;

    @GetMapping
    public List<VideoProductDTO> getAll() {
        return service.getAll();
    }
}
