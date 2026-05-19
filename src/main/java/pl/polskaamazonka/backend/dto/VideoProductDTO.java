package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VideoProductDTO {
    private Long id;
    private Long videoId;
    private Long productId;
}
