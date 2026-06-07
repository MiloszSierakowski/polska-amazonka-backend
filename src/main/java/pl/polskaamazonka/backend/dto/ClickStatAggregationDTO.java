package pl.polskaamazonka.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClickStatAggregationDTO {
    private String entityType;
    private Long entityId;
    private Long clickCount;
}
