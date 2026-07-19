package pl.polskaamazonka.backend.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private Long productLinkId;
    private LinkDTO productLink;
    private String promoCode;
    private List<String> tags;
    private Boolean isBroken;
    private Boolean needsReview;
    private Instant lastCheckedAt;
}
