package pl.polskaamazonka.backend.service.scraper;

import lombok.Getter;

@Getter
public class ProductPageData {
    private final String name;
    private final String imageUrl;

    public ProductPageData(String name, String imageUrl) {
        this.name = name;
        this.imageUrl = imageUrl;
    }
}
