package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
}
