package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.PublicVideoDTO;
import pl.polskaamazonka.backend.service.VideoService;

import java.util.List;

@RestController
@RequestMapping("/api/public/videos")
@RequiredArgsConstructor
public class PublicVideoController {

    private final VideoService videoService;

    @GetMapping("/promoted")
    public List<PublicVideoDTO> getPromoted() {
        return videoService.getAllPromotedPublic();
    }

    @GetMapping
    public List<PublicVideoDTO> getAll(@RequestParam(required = false) Long categoryId) {
        return videoService.getAllPublic(categoryId);
    }

    @GetMapping("/by-code/{publicCode}")
    public PublicVideoDTO getByPublicCode(@PathVariable String publicCode) {
        return videoService.getByPublicCodePublic(publicCode);
    }

    @GetMapping("/{id}")
    public PublicVideoDTO getById(@PathVariable Long id) {
        return videoService.getByIdPublic(id);
    }
}
