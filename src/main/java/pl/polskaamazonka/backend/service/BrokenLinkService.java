package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.dto.BrokenLinkProductDTO;
import pl.polskaamazonka.backend.model.Product;
import pl.polskaamazonka.backend.model.Video;
import pl.polskaamazonka.backend.model.VideoProduct;
import pl.polskaamazonka.backend.repository.VideoProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrokenLinkService {

    private final VideoProductRepository videoProductRepository;

    @Transactional(readOnly = true)
    public List<BrokenLinkProductDTO> getBrokenProductLinks() {
        return videoProductRepository.findAllWithBrokenProductLinks().stream()
                .map(this::toDto)
                .toList();
    }

    private BrokenLinkProductDTO toDto(VideoProduct relation) {
        Product product = relation.getProduct();
        Video video = relation.getVideo();
        BrokenLinkProductDTO dto = new BrokenLinkProductDTO();
        dto.setVideoId(video != null ? video.getId() : null);
        dto.setVideoTitle(video != null ? video.getTitle() : null);
        dto.setProductId(product != null ? product.getId() : null);
        if (product != null) {
            dto.setProductName(product.getName());
            dto.setImageUrl(product.getImageUrl());
            if (product.getProductLink() != null) {
                dto.setShopUrl(product.getProductLink().getUrl());
                dto.setLinkId(product.getProductLink().getId());
            }
        }
        return dto;
    }
}
