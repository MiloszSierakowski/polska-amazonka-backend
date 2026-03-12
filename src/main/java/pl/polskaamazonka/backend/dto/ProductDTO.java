package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class ProductDTO {
    private Integer id;
    private String name;
    private String imageUrl;
    private Integer productLinkId;
}
