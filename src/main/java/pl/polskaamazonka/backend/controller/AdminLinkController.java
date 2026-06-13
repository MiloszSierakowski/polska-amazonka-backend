package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.polskaamazonka.backend.dto.LinkDTO;
import pl.polskaamazonka.backend.dto.SaveLinkRequest;
import pl.polskaamazonka.backend.service.LinkService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/links")
@RequiredArgsConstructor
public class AdminLinkController {

    private final LinkService linkService;

    @GetMapping
    public List<LinkDTO> getSocialLinks() {
        return linkService.getSocialLinksForAdmin();
    }

    @GetMapping("/{id}")
    public LinkDTO getById(@PathVariable Long id) {
        return linkService.getSocialLinkById(id);
    }

    @PostMapping
    public LinkDTO create(@RequestBody SaveLinkRequest request) {
        return linkService.createSocialLink(request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LinkDTO createMultipart(
            @RequestParam String url,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "icon", required = false) MultipartFile icon
    ) {
        return linkService.createSocialLink(url, isActive, imageFile != null ? imageFile : icon);
    }

    @PutMapping("/{id}")
    public LinkDTO update(@PathVariable Long id, @RequestBody SaveLinkRequest request) {
        return linkService.updateSocialLink(id, request);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LinkDTO updateMultipart(
            @PathVariable Long id,
            @RequestParam String url,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "icon", required = false) MultipartFile icon
    ) {
        return linkService.updateSocialLink(id, url, isActive, imageFile != null ? imageFile : icon);
    }

    @PutMapping("/social/reorder")
    public void reorder(@RequestBody List<Long> orderedIds) {
        linkService.reorderSocialLinks(orderedIds);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        linkService.deleteSocialLink(id);
    }
}
