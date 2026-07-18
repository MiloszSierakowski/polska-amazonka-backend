package pl.polskaamazonka.backend.dto;

import java.util.List;

public record LinkValidationRunDetailsDTO(
        LinkValidationRunDTO run,
        List<LinkValidationRunItemDTO> items
) {
}
