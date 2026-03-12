package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User getById(Integer id) {
        return userRepository.findById(id).orElse(null);
    }
}
