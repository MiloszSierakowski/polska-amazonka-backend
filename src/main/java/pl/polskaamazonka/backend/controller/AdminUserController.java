package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.CreateAdminUserRequest;
import pl.polskaamazonka.backend.dto.ResetUserPasswordResponse;
import pl.polskaamazonka.backend.dto.UpdateUserBlockedRequest;
import pl.polskaamazonka.backend.dto.UserResponseDTO;
import pl.polskaamazonka.backend.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponseDTO> getAll() {
        return userService.getAllForAdmin();
    }

    @PostMapping
    public UserResponseDTO create(@RequestBody CreateAdminUserRequest request) {
        return userService.createForAdmin(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.deleteForAdmin(id);
    }

    @PatchMapping("/{id}/blocked")
    public UserResponseDTO setBlocked(@PathVariable Long id, @RequestBody UpdateUserBlockedRequest request) {
        return userService.setBlockedForAdmin(id, request);
    }

    @PostMapping("/{id}/password-reset")
    public ResetUserPasswordResponse resetPassword(@PathVariable Long id) {
        return userService.resetPasswordForAdmin(id);
    }
}
