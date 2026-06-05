package pl.polskaamazonka.backend.dto;

import lombok.Getter;

@Getter
public class ProductImageUploadDTO {
    private final String imageUrl;

    public ProductImageUploadDTO(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
