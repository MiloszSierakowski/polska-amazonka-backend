package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByProductLink_Id(Long productLinkId);

    @Query("SELECT p FROM Product p JOIN FETCH p.productLink WHERE p.id = :id")
    Optional<Product> findByIdWithProductLink(@Param("id") Long id);
}
