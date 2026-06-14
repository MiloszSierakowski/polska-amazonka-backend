package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class ProductLinkFlagDTO {
    private Boolean isBroken;
    private Boolean needsReview;
}
