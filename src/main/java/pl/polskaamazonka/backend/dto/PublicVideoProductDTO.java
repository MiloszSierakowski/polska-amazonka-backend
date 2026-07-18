package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicVideoProductDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private Long productLinkId;
    private LinkDTO productLink;
    private String promoCode;
}
