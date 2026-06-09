package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
        return linkService.getByType(LinkService.SOCIAL_LINK_TYPE);
    }

    @GetMapping("/{id}")
    public LinkDTO getById(@PathVariable Long id) {
        return linkService.getSocialLinkById(id);
    }

    @PostMapping
    public LinkDTO create(@RequestBody SaveLinkRequest request) {
        return linkService.createSocialLink(request);
    }

    @PutMapping("/{id}")
    public LinkDTO update(@PathVariable Long id, @RequestBody SaveLinkRequest request) {
        return linkService.updateSocialLink(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        linkService.deleteSocialLink(id);
    }
}
