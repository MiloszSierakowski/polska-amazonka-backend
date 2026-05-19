package pl.polskaamazonka.backend.mapper;

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
        return dto;
    }
}
