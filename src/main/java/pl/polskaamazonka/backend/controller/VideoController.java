package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.service.VideoService;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @GetMapping
    public List<VideoDTO> getAll(@RequestParam(required = false) Long categoryId) {
        return videoService.getAll(categoryId);
    }

    @GetMapping("/{id}")
    public VideoDTO getById(@PathVariable Long id) {
        return videoService.getById(id);
    }

    @PostMapping
    public VideoDTO create(@RequestBody VideoDTO dto) {
        return videoService.create(dto);
    }

    @PostMapping("/{videoId}/products")
    public VideoDTO addProduct(@PathVariable Long videoId, @RequestBody ProductDTO dto) {
        return videoService.addProduct(videoId, dto);
    }

    @DeleteMapping("/{videoId}/products/{productId}")
    public VideoDTO detachProduct(@PathVariable Long videoId, @PathVariable Long productId) {
        return videoService.detachProduct(videoId, productId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        videoService.delete(id);
    }
}
