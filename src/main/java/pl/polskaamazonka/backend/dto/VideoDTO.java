package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class VideoDTO {
    private Long id;
    private String tiktokUrl;
    private String localMp4Url;
    private String previewImageUrl;
    private String title;
    private Boolean isActive;
    private Instant promotionStartAt;
    private Instant promotionEndAt;
    private List<ProductDTO> products;
    private List<String> blockReasons;
}
