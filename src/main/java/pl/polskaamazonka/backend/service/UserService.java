package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.dto.UserResponseDTO;
import pl.polskaamazonka.backend.mapper.UserMapper;
import pl.polskaamazonka.backend.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<UserResponseDTO> getAllForAdmin() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }
}
