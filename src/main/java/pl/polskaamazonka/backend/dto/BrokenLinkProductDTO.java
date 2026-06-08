package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class BrokenLinkProductDTO {
    private Long videoId;
    private String videoTitle;
    private Long productId;
    private String productName;
    private String imageUrl;
    private String shopUrl;
    private Long linkId;
}
