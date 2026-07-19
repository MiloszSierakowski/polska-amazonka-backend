package pl.polskaamazonka.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByProductLink_Id(Long productLinkId);
    Optional<Product> findFirstByProductLink_Id(Long productLinkId);

    @Query("SELECT p FROM Product p JOIN FETCH p.productLink WHERE p.id = :id")
    Optional<Product> findByIdWithProductLink(@Param("id") Long id);

    @Query("""
            SELECT p.id FROM Product p
            WHERE :query = ''
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR EXISTS (
                    SELECT t.id FROM ProductTag t
                    WHERE t.product = p
                      AND LOWER(t.value) LIKE LOWER(CONCAT('%', :query, '%'))
               )
            ORDER BY LOWER(p.name) ASC, p.id ASC
            """)
    List<Long> searchAdminProductIds(@Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"productLink", "tags"})
    List<Product> findByIdIn(List<Long> ids);

    @Query("""
            SELECT DISTINCT p FROM VideoProduct vp
            JOIN vp.product p
            JOIN FETCH p.productLink l
            JOIN vp.video v
            WHERE (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR EXISTS (
                    SELECT t.id FROM ProductTag t
                    WHERE t.product = p
                      AND LOWER(t.value) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            )
              AND (l.isBroken IS NULL OR l.isBroken = FALSE)
              AND (l.isActive IS NULL OR l.isActive = TRUE)
              AND l.url IS NOT NULL
              AND TRIM(l.url) <> ''
              AND v.isActive = TRUE
              AND v.publicCode IS NOT NULL
              AND TRIM(v.publicCode) <> ''
            ORDER BY p.name ASC
            """)
    List<Product> searchPublicByNameOrTag(@Param("search") String search, Pageable pageable);
}
