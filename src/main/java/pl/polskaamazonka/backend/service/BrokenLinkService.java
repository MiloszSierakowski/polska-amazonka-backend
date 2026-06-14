package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.dto.BrokenLinkProductDTO;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Video;
import pl.polskaamazonka.backend.model.VideoProduct;
import pl.polskaamazonka.backend.repository.VideoProductRepository;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrokenLinkService {

    private final VideoProductRepository videoProductRepository;

    @Transactional(readOnly = true)
    public List<BrokenLinkProductDTO> getLinksNeedingReview() {
        return videoProductRepository.findAllWithProductLinksNeedingAttention().stream()
                .sorted(reviewListComparator())
                .map(this::toDto)
                .toList();
    }

    /** @deprecated use {@link #getLinksNeedingReview()} */
    @Transactional(readOnly = true)
    public List<BrokenLinkProductDTO> getBrokenProductLinks() {
        return getLinksNeedingReview();
    }

    private Comparator<VideoProduct> reviewListComparator() {
        return Comparator
                .comparing((VideoProduct relation) -> !Boolean.TRUE.equals(
                        relation.getProduct() != null
                                && relation.getProduct().getProductLink() != null
                                && relation.getProduct().getProductLink().getIsBroken()
                ))
                .thenComparing(relation -> {
                    Video video = relation.getVideo();
                    return video != null && video.getTitle() != null ? video.getTitle() : "";
                }, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(relation -> {
                    Product product = relation.getProduct();
                    return product != null && product.getName() != null ? product.getName() : "";
                }, String.CASE_INSENSITIVE_ORDER);
    }

    private BrokenLinkProductDTO toDto(VideoProduct relation) {
        Product product = relation.getProduct();
        Video video = relation.getVideo();
        BrokenLinkProductDTO dto = new BrokenLinkProductDTO();
        dto.setVideoId(video != null ? video.getId() : null);
        dto.setVideoTitle(video != null ? video.getTitle() : null);
        dto.setVideoPreviewImageUrl(video != null ? video.getPreviewImageUrl() : null);
        dto.setProductId(product != null ? product.getId() : null);
        if (product != null) {
            dto.setProductName(product.getName());
            dto.setImageUrl(product.getImageUrl());
            if (product.getProductLink() != null) {
                dto.setShopUrl(product.getProductLink().getUrl());
                dto.setLinkId(product.getProductLink().getId());
                dto.setIsBroken(Boolean.TRUE.equals(product.getProductLink().getIsBroken()));
                dto.setNeedsReview(Boolean.TRUE.equals(product.getProductLink().getNeedsReview()));
            }
        }
        return dto;
    }
}
