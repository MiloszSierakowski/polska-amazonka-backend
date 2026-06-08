package pl.polskaamazonka.backend.mapper;

import pl.polskaamazonka.backend.dto.UserProfileDTO;
import pl.polskaamazonka.backend.dto.UserResponseDTO;
import pl.polskaamazonka.backend.model.User;

public class UserMapper {

    public static UserResponseDTO toResponse(User user) {
        if (user == null) {
            return null;
        }
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setLogin(user.getLogin());
        dto.setRole(user.getRole());
        dto.setEmail(user.getEmail());
        dto.setIsBlocked(user.isBlocked());
        return dto;
    }

    public static UserProfileDTO toProfileDTO(User user) {
        if (user == null) {
            return null;
        }
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setLogin(user.getLogin());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        return dto;
    }
}
