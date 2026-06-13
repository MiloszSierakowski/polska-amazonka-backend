package pl.polskaamazonka.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResetUserPasswordResponse {
    private Long userId;
    private String generatedPassword;
}
