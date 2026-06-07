package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClickStatRequest {
    private String entityType;
    private Long entityId;
}
