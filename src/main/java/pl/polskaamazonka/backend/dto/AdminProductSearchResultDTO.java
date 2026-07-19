package pl.polskaamazonka.backend.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class AdminProductSearchResultDTO {
    private Long productId;
    private String name;
    private String imageUrl;
    private List<String> tags;
    private LinkDTO productLink;
    private Boolean isBroken;
    private Boolean needsReview;
    private Boolean isActive;
    private Instant lastCheckedAt;
    private boolean alreadyAssigned;
}
