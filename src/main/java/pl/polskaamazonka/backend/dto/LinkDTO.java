package pl.polskaamazonka.backend.dto;

import lombok.Data;

@Data
public class LinkDTO {
    private Integer id;
    private String url;
    private String type;
    private Boolean isActive;
}
