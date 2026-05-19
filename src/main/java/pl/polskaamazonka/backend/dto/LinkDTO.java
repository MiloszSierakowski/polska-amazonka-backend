package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class LinkDTO {
    private Long id;
    private String url;
    private String type;
    private Boolean isActive;
}
