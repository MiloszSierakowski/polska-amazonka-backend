package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.VideoProductDTO;
import pl.polskaamazonka.backend.model.VideoProduct;

public class VideoProductMapper {

    public static VideoProductDTO toDTO(VideoProduct vp) {
        VideoProductDTO dto = new VideoProductDTO();
        dto.setId(vp.getId());
        dto.setVideoId(vp.getVideo().getId());
        dto.setProductId(vp.getProduct().getId());
        return dto;
    }
}
