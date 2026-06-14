package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.VideoProduct;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoProductRepository extends JpaRepository<VideoProduct, Long> {

    @Query("SELECT vp FROM VideoProduct vp JOIN FETCH vp.product p LEFT JOIN FETCH p.productLink WHERE vp.video.id = :videoId")
    List<VideoProduct> findByVideo_Id(@Param("videoId") Long videoId);

    Optional<VideoProduct> findByVideo_IdAndProduct_Id(Long videoId, Long productId);

    @Query("""
            SELECT vp FROM VideoProduct vp
            JOIN FETCH vp.product p
            JOIN FETCH p.productLink
            WHERE vp.video.id = :videoId AND vp.product.id = :productId
            """)
    Optional<VideoProduct> findByVideo_IdAndProduct_IdWithLink(
            @Param("videoId") Long videoId,
            @Param("productId") Long productId
    );

    long countByProduct_Id(Long productId);

    @Query("""
            SELECT vp FROM VideoProduct vp
            JOIN FETCH vp.product p
            JOIN FETCH p.productLink l
            JOIN FETCH vp.video v
            WHERE l.id IN :linkIds
            """)
    List<VideoProduct> findAllByProductLinkIdIn(@Param("linkIds") Collection<Long> linkIds);

    @Query("""
            SELECT vp FROM VideoProduct vp
            JOIN FETCH vp.product p
            JOIN FETCH p.productLink l
            JOIN FETCH vp.video v
            WHERE l.type = 'product'
              AND (l.isBroken = true OR l.needsReview = true)
            """)
    List<VideoProduct> findAllWithProductLinksNeedingAttention();
}
