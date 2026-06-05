package pl.polskaamazonka.backend.dto;

import lombok.Getter;

@Getter
public class ProductPreviewDTO {
    private final String name;
    private final String imageUrl;
    private final String platform;
    private final boolean requiresManualImage;

    public ProductPreviewDTO(String name, String imageUrl, String platform, boolean requiresManualImage) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.platform = platform;
        this.requiresManualImage = requiresManualImage;
    }
}
