package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.polskaamazonka.backend.dto.UpdateUserProfileRequest;
import pl.polskaamazonka.backend.dto.UserProfileDTO;
import pl.polskaamazonka.backend.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    @GetMapping("/profile")
    public UserProfileDTO getProfile() {
        return userService.getCurrentProfile();
    }

    @PutMapping("/profile")
    public UserProfileDTO updateProfile(@RequestBody UpdateUserProfileRequest request) {
        return userService.updateCurrentProfile(request);
    }
}
