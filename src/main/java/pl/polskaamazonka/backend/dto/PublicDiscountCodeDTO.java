package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class PublicDiscountCodeDTO {
    private Long id;
    private Long shopId;
    private String shopName;
    private String shopSlug;
    private String codeValue;
    private String description;
}
