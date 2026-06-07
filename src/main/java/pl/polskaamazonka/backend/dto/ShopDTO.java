package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class ShopDTO {
    private Long id;
    private String slug;
    private String code;
    private String name;
    private String shopUrl;
    private Boolean isActive;
    private String colorCode;
}
