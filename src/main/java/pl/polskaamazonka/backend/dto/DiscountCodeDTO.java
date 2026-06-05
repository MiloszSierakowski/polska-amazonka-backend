package pl.polskaamazonka.backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class DiscountCodeDTO {
    private Long id;
    private String platform;
    private String codeValue;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
}
