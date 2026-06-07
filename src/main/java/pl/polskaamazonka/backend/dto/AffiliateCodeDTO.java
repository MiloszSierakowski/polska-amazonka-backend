package pl.polskaamazonka.backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class AffiliateCodeDTO {
    private Long id;
    private Long shopId;
    private String shopName;
    private String shopSlug;
    private String codeValue;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
}
