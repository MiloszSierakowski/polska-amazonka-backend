package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.VideoDTO;
import pl.polskaamazonka.backend.model.Video;

public class VideoMapper {

    public static VideoDTO toDTO(Video video) {
        if (video == null) return null;

        VideoDTO dto = new VideoDTO();
        dto.setId(video.getId());
        dto.setTiktokUrl(video.getTiktokUrl());
        dto.setLocalMp4Url(video.getLocalMp4Url());
        dto.setPreviewImageUrl(video.getPreviewImageUrl());
        dto.setTitle(video.getTitle());
        dto.setIsActive(video.getIsActive());
        dto.setProducts(
                video.getVideoProducts() == null
                        ? java.util.List.of()
                        : video.getVideoProducts().stream()
                        .map(videoProduct -> ProductMapper.toDTO(videoProduct.getProduct()))
                        .toList()
        );
        return dto;
    }
}
