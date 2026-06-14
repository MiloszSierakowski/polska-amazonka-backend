package pl.polskaamazonka.backend.dto;



import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;



@Data

public class BrokenLinkProductDTO {

    @JsonProperty("videoId")

    private Long videoId;



    @JsonProperty("videoTitle")

    private String videoTitle;

    @JsonProperty("videoPreviewImageUrl")

    private String videoPreviewImageUrl;

    @JsonProperty("productId")

    private Long productId;



    @JsonProperty("productName")

    private String productName;



    @JsonProperty("imageUrl")

    private String imageUrl;



    @JsonProperty("shopUrl")

    private String shopUrl;



    @JsonProperty("linkId")

    private Long linkId;



    @JsonProperty("isBroken")

    private Boolean isBroken;



    @JsonProperty("needsReview")

    private Boolean needsReview;

}


