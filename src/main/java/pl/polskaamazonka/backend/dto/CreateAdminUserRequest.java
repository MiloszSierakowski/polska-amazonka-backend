package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;
import pl.polskaamazonka.backend.model.enums.UserRole;

@Getter
@Setter
public class CreateAdminUserRequest {
    private String login;
    private String password;
    private UserRole role;
    private String email;
}
