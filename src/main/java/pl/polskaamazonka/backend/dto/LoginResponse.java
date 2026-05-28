package pl.polskaamazonka.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pl.polskaamazonka.backend.model.enums.UserRole;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long id;
    private String login;
    private UserRole role;
    private String firstName;
    private String lastName;
    private String email;
}
