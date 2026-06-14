package pl.polskaamazonka.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProductLinkVerifyResultDTO {
    private Long videoId;
    private Long productId;

    @JsonProperty("linkWorking")
    private Boolean linkWorking;

    @JsonProperty("isBroken")
    private Boolean isBroken;

    @JsonProperty("verificationUncertain")
    private Boolean verificationUncertain;

    @JsonProperty("needsReview")
    private Boolean needsReview;
    private String currentTitle;
    private String currentImageUrl;
    private String storeTitle;
    private String storeImageUrl;
}
