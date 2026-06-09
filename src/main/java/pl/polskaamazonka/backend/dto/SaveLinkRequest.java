package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveLinkRequest {
    private String url;
    private String type;
    private Boolean isActive;
}
