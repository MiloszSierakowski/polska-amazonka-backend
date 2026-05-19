package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;
import pl.polskaamazonka.backend.model.enums.UserRole;

@Getter
@Setter
public class UserResponseDTO {
    private Long id;
    private String login;
    private UserRole role;
}
