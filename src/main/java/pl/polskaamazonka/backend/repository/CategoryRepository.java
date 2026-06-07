package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    Optional<Category> findByShop_Id(Long shopId);

    Optional<Category> findTopByOrderByDisplayOrderDesc();
}
