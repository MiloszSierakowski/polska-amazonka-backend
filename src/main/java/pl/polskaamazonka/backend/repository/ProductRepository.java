package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByProductLink_Id(Long productLinkId);
}
