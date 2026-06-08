package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserBlockedRequest {
    private Boolean isBlocked;
}
