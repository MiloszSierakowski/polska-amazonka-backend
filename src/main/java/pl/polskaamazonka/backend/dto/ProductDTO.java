package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private Long productLinkId;
    private LinkDTO productLink;
    private String promoCode;
}
