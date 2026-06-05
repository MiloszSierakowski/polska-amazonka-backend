package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserProfileRequest {
    private String login;
    private String firstName;
    private String lastName;
    private String email;
    private String currentPassword;
    private String newPassword;
}
