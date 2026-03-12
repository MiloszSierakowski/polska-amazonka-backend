package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Clickstat;

public interface ClickStatRepository extends JpaRepository<Clickstat, Integer> {
}
