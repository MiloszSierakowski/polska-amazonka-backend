package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {
}
