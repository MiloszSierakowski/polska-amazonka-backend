package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.LoginRequest;
import pl.polskaamazonka.backend.dto.LoginResponse;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.security.JwtService;
import pl.polskaamazonka.backend.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        if (request.getLogin() == null || request.getLogin().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (principal.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String token = jwtService.generateToken(principal);
        return new LoginResponse(token, principal.getId(), principal.getLogin(), principal.getRole());
    }
}
