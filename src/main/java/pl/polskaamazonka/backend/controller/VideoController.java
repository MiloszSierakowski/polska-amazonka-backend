package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.polskaamazonka.backend.dto.ProductDTO;
import pl.polskaamazonka.backend.dto.ProductLinkFlagDTO;
import pl.polskaamazonka.backend.dto.ProductLinkVerifyResultDTO;
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

    @PutMapping("/{id}")
    public VideoDTO update(@PathVariable Long id, @RequestBody VideoDTO dto) {
        return videoService.update(id, dto);
    }

    @PostMapping("/{videoId}/products")
    public VideoDTO addProduct(@PathVariable Long videoId, @RequestBody ProductDTO dto) {
        return videoService.addProduct(videoId, dto);
    }

    @PutMapping("/{videoId}/products/{productId}")
    public VideoDTO updateProduct(
            @PathVariable Long videoId,
            @PathVariable Long productId,
            @RequestBody ProductDTO dto
    ) {
        return videoService.updateProduct(videoId, productId, dto);
    }

    @DeleteMapping("/{videoId}/products/{productId}")
    public VideoDTO detachProduct(@PathVariable Long videoId, @PathVariable Long productId) {
        return videoService.detachProduct(videoId, productId);
    }

    @PostMapping("/{videoId}/products/{productId}/resync")
    public VideoDTO resyncProduct(@PathVariable Long videoId, @PathVariable Long productId) {
        return videoService.resyncProduct(videoId, productId);
    }

    @PostMapping("/{videoId}/products/{productId}/verify-link")
    public ProductLinkVerifyResultDTO verifyProductLink(
            @PathVariable Long videoId,
            @PathVariable Long productId
    ) {
        return videoService.verifyProductLink(videoId, productId);
    }

    @PostMapping("/{videoId}/products/{productId}/link-flag")
    public void setProductLinkFlag(
            @PathVariable Long videoId,
            @PathVariable Long productId,
            @RequestBody ProductLinkFlagDTO dto
    ) {
        if (dto.getIsBroken() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Pole isBroken jest wymagane"
            );
        }
        boolean needsReview = Boolean.TRUE.equals(dto.getNeedsReview());
        videoService.setProductLinkFlag(videoId, productId, dto.getIsBroken(), needsReview);
    }

    @PostMapping("/{videoId}/products/{productId}/apply-store-title")
    public VideoDTO applyStoreTitle(
            @PathVariable Long videoId,
            @PathVariable Long productId
    ) {
        return videoService.applyStoreTitleToProduct(videoId, productId);
    }

    @PostMapping("/{videoId}/products/{productId}/apply-store-image")
    public VideoDTO applyStoreImage(
            @PathVariable Long videoId,
            @PathVariable Long productId
    ) {
        return videoService.applyStoreImageToProduct(videoId, productId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        videoService.delete(id);
    }
}
