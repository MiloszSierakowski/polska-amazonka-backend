package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private Long shopId;
    private Long displayOrder;
}
