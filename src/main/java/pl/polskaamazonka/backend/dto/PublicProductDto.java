package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicProductDto {
    private Long id;
    private String name;
    private String imageUrl;
}
