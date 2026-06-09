package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LoginRequest;
import pl.polskaamazonka.backend.dto.LoginResponse;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.repository.UserRepository;
import pl.polskaamazonka.backend.security.JwtService;
import pl.polskaamazonka.backend.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public LoginResponse login(LoginRequest request) {
        if (request.getLogin() == null || request.getLogin().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (principal.getRole() != UserRole.ADMIN && principal.getRole() != UserRole.WORKER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień do logowania w panelu.");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String token = jwtService.generateToken(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        activityLogService.logAction(
                "ZALOGOWANIE",
                "Użytkownik o loginie " + user.getLogin() + " zalogował się do systemu."
        );
        return new LoginResponse(
                token,
                user.getId(),
                user.getLogin(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }

    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return;
        }
        activityLogService.logAction(
                "WYLOGOWANIE",
                "Użytkownik o loginie " + principal.getLogin() + " wylogował się z systemu."
        );
    }
}
