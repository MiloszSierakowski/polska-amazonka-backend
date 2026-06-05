package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;
import pl.polskaamazonka.backend.model.enums.UserRole;

@Getter
@Setter
public class UserProfileDTO {
    private Long id;
    private String login;
    private String firstName;
    private String lastName;
    private String email;
    private UserRole role;
}
