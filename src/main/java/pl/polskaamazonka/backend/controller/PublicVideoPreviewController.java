package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.service.PublicVideoPreviewService;

@RestController
@RequestMapping("/api/public/video-preview")
@RequiredArgsConstructor
public class PublicVideoPreviewController {

    private final PublicVideoPreviewService publicVideoPreviewService;

    @GetMapping(value = "/{publicCode}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String getPreview(@PathVariable String publicCode) {
        return publicVideoPreviewService.render(publicCode);
    }
}
