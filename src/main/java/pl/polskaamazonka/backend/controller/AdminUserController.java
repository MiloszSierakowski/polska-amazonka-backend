package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.UserResponseDTO;
import pl.polskaamazonka.backend.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponseDTO> getAll() {
        return userService.getAllForAdmin();
    }
}
