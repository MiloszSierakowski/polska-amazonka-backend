package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Link;

import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Integer> {
    List<Link> findByType(String type);
}
