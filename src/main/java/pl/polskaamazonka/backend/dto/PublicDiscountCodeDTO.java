package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class PublicDiscountCodeDTO {
    private Long id;
    private String platform;
    private String codeValue;
    private String description;
}
