package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Linkcheckhistory;

public interface LinkCheckHistoryRepository extends JpaRepository<Linkcheckhistory, Integer> {
}
