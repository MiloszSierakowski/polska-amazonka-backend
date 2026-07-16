package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.Video;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findAllByOrderByCreatedAtDesc();

    @Query("SELECT vc.video FROM Videocategory vc WHERE vc.category.id = :categoryId ORDER BY vc.video.createdAt DESC")
    List<Video> findAllByCategoryId(@Param("categoryId") Long categoryId);

    @Query("""
            SELECT v FROM Video v
            WHERE v.isActive = TRUE
              AND v.promotionStartAt IS NOT NULL
              AND v.promotionEndAt IS NOT NULL
              AND v.promotionStartAt <= :now
              AND v.promotionEndAt > :now
            ORDER BY v.promotionStartAt DESC
            """)
    List<Video> findAllActivePromoted(@Param("now") Instant now);

    @Query("SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.videoProducts vp LEFT JOIN FETCH vp.product p LEFT JOIN FETCH p.productLink WHERE v.id = :id")
    Optional<Video> findWithProductsById(@Param("id") Long id);

    @Query("SELECT DISTINCT v FROM Video v LEFT JOIN FETCH v.videoProducts vp LEFT JOIN FETCH vp.product p LEFT JOIN FETCH p.productLink WHERE v.publicCode = :publicCode")
    Optional<Video> findWithProductsByPublicCode(@Param("publicCode") String publicCode);

    Optional<Video> findByPublicCode(String publicCode);

    boolean existsByPublicCode(String publicCode);

    boolean existsByPublicCodeAndIdNot(String publicCode, Long id);
}
