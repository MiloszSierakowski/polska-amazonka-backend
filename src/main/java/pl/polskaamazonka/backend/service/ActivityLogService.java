package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.dto.ChangeLogDTO;
import pl.polskaamazonka.backend.model.ChangeLog;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.model.enums.UserRole;
import pl.polskaamazonka.backend.repository.ChangeLogRepository;
import pl.polskaamazonka.backend.repository.UserRepository;
import pl.polskaamazonka.backend.security.UserPrincipal;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ChangeLogRepository changeLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logAction(String action, String details) {
        UserPrincipal principal = resolvePrincipal();
        ChangeLog entry = new ChangeLog();
        entry.setUserId(resolveUserId(principal));
        entry.setAction(normalizeAction(action));
        entry.setDetails(resolveDetails(principal, details));
        entry.setCreatedAt(LocalDateTime.now());
        changeLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ChangeLogDTO> getRecentLogs() {
        return changeLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private ChangeLogDTO toDto(ChangeLog entry) {
        ChangeLogDTO dto = new ChangeLogDTO();
        dto.setId(entry.getId());
        dto.setUserId(entry.getUserId());
        dto.setUserLogin(resolveUserLogin(entry));
        dto.setAction(entry.getAction());
        dto.setDetails(entry.getDetails());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }

    private String resolveUserLogin(ChangeLog entry) {
        if (entry.getDetails() != null && entry.getDetails().startsWith("System: ")) {
            return "System";
        }
        return userRepository.findById(entry.getUserId())
                .map(User::getLogin)
                .orElse("System");
    }

    private UserPrincipal resolvePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private Long resolveUserId(UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        return userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
                .map(User::getId)
                .orElseGet(() -> userRepository.findAll().stream()
                        .findFirst()
                        .map(User::getId)
                        .orElse(1L));
    }

    private String resolveDetails(UserPrincipal principal, String details) {
        String normalizedDetails = normalizeDetails(details);
        if (principal != null) {
            return normalizedDetails;
        }
        return "System: " + normalizedDetails;
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = action.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private String normalizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return "-";
        }
        return details.trim();
    }
}
