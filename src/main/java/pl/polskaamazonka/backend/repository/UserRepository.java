package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.User;
import pl.polskaamazonka.backend.model.enums.UserRole;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findFirstByRoleOrderByIdAsc(UserRole role);

    Optional<User> findByLogin(String login);

    boolean existsByLoginAndIdNot(String login, Long id);

    boolean existsByLogin(String login);
}
