package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.CreateAdminUserRequest;
import pl.polskaamazonka.backend.dto.ResetUserPasswordResponse;
import pl.polskaamazonka.backend.dto.UpdateUserBlockedRequest;
import pl.polskaamazonka.backend.dto.UpdateUserProfileRequest;
import pl.polskaamazonka.backend.dto.UserProfileDTO;
import pl.polskaamazonka.backend.dto.UserResponseDTO;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.mapper.UserMapper;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.repository.UserRepository;
import pl.polskaamazonka.backend.security.JwtService;
import pl.polskaamazonka.backend.security.UserPrincipal;

import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_LOGIN_LENGTH = 3;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int GENERATED_PASSWORD_LENGTH = 10;
    private static final String GENERATED_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String LOGIN_TAKEN_MESSAGE = "Ten login jest już zajęty.";
    private static final String LOGIN_TAKEN_BLOCKED_MESSAGE =
            "Ten login jest już zajęty (przypisany do zablokowanego użytkownika) i nie może zostać użyty ponownie.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public List<UserResponseDTO> getAllForAdmin() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponseDTO createForAdmin(CreateAdminUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak danych użytkownika.");
        }
        String login = normalizeLogin(request.getLogin());
        ensureLoginAvailable(login);
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hasło jest wymagane.");
        }
        String password = request.getPassword().trim();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hasło musi mieć co najmniej " + MIN_PASSWORD_LENGTH + " znaków."
            );
        }
        UserRole role = request.getRole() != null ? request.getRole() : UserRole.WORKER;
        String email = normalizeOptionalText(request.getEmail());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podany adres e-mail jest nieprawidłowy.");
        }
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEmail(email);
        user.setBlocked(false);
        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    @Transactional
    public void deleteForAdmin(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureNotAdminTarget(user);
        UserPrincipal current = getCurrentPrincipal();
        if (Objects.equals(current.getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie można usunąć własnego konta.");
        }
        user.setBlocked(true);
        userRepository.save(user);
    }

    @Transactional
    public UserResponseDTO setBlockedForAdmin(Long id, UpdateUserBlockedRequest request) {
        if (request == null || request.getIsBlocked() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureNotAdminTarget(user);
        UserPrincipal current = getCurrentPrincipal();
        if (Objects.equals(current.getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie można zmienić blokady własnego konta.");
        }
        user.setBlocked(request.getIsBlocked());
        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    @Transactional
    public ResetUserPasswordResponse resetPasswordForAdmin(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureNotAdminTarget(user);
        String generatedPassword = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        userRepository.save(user);
        return new ResetUserPasswordResponse(user.getId(), generatedPassword);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getCurrentProfile() {
        User user = findCurrentUser();
        return UserMapper.toProfileDTO(user);
    }

    @Transactional
    public ProfileUpdateResult updateCurrentProfile(UpdateUserProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        User user = findCurrentUser();
        String currentLogin = user.getLogin();
        String requestedLogin = normalizeLogin(request.getLogin());
        boolean loginChanging = !Objects.equals(currentLogin, requestedLogin);
        boolean passwordChanging = request.getNewPassword() != null && !request.getNewPassword().isBlank();

        if (loginChanging || passwordChanging) {
            verifyCurrentPassword(user, request.getCurrentPassword());
        }

        if (loginChanging) {
            ensureLoginAvailableForUpdate(requestedLogin, user.getId());
            user.setLogin(requestedLogin);
        }

        if (passwordChanging) {
            String newPassword = request.getNewPassword().trim();
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        user.setFirstName(normalizeOptionalText(request.getFirstName()));
        user.setLastName(normalizeOptionalText(request.getLastName()));
        String email = normalizeOptionalText(request.getEmail());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        user.setEmail(email);

        User saved = userRepository.save(user);
        UserProfileDTO dto = UserMapper.toProfileDTO(saved);
        String refreshedJwt = loginChanging || passwordChanging
                ? jwtService.generateToken(new UserPrincipal(saved))
                : null;
        return new ProfileUpdateResult(dto, refreshedJwt);
    }

    public record ProfileUpdateResult(UserProfileDTO profile, String refreshedJwt) {}

    private void ensureLoginAvailable(String login) {
        userRepository.findByLogin(login).ifPresent(this::throwLoginConflict);
    }

    private void ensureLoginAvailableForUpdate(String login, Long currentUserId) {
        userRepository.findByLogin(login)
                .filter(existing -> !Objects.equals(existing.getId(), currentUserId))
                .ifPresent(this::throwLoginConflict);
    }

    private void throwLoginConflict(User existingUser) {
        String message = existingUser.isBlocked() ? LOGIN_TAKEN_BLOCKED_MESSAGE : LOGIN_TAKEN_MESSAGE;
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private void verifyCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Aktualne hasło jest nieprawidłowe.");
        }
    }

    private void ensureNotAdminTarget(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operacja na koncie administratora jest niedozwolona.");
        }
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(GENERATED_PASSWORD_CHARS.length());
            password.append(GENERATED_PASSWORD_CHARS.charAt(index));
        }
        return password.toString();
    }

    private String normalizeLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login jest wymagany.");
        }
        String trimmed = login.trim();
        if (trimmed.length() < MIN_LOGIN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Login musi mieć co najmniej " + MIN_LOGIN_LENGTH + " znaki."
            );
        }
        return trimmed;
    }

    private User findCurrentUser() {
        UserPrincipal principal = getCurrentPrincipal();
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private UserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
