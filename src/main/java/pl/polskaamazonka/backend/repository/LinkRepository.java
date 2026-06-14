package pl.polskaamazonka.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.Link;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Long> {
    List<Link> findByType(String type);
    List<Link> findByTypeOrderByDisplayOrderAscIdAsc(String type);
    List<Link> findByTypeAndIsActiveTrueOrderByDisplayOrderAscIdAsc(String type);
    long countByTypeAndIsActiveTrue(String type);
    long countByTypeAndIsActiveTrueAndIdNot(String type, Long id);

    @Query("""
            SELECT l FROM Link l
            WHERE l.type = 'product' AND (l.isBroken = true OR l.needsReview = true)
            """)
    List<Link> findProductLinksNeedingAttention();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Link l
            SET l.isBroken = :isBroken,
                l.needsReview = :needsReview,
                l.lastCheckedAt = :checkedAt
            WHERE l.id = :linkId
            """)
    void updateReviewFlags(
            @Param("linkId") Long linkId,
            @Param("isBroken") boolean isBroken,
            @Param("needsReview") boolean needsReview,
            @Param("checkedAt") Instant checkedAt
    );

    @Query("SELECT l.id FROM Link l WHERE l.id IN :linkIds AND l.type = 'product' AND (l.isBroken = true OR l.needsReview = true)")
    List<Long> filterProductLinkIdsNeedingAttention(@Param("linkIds") Collection<Long> linkIds);

    @Query("""
            SELECT l FROM Link l
            WHERE (l.lastCheckedAt IS NULL OR l.lastCheckedAt < :cutoff)
              AND (l.lastCheckedAt IS NULL OR l.needsReview = true OR l.isBroken = false)
            ORDER BY
              CASE WHEN l.lastCheckedAt IS NULL THEN 0 ELSE 1 END,
              CASE WHEN l.needsReview = true THEN 0 ELSE 1 END,
              CASE WHEN l.isBroken = false THEN 0 ELSE 1 END,
              l.lastCheckedAt ASC,
              l.id ASC
            """)
    List<Link> findLinksForScheduledValidation(@Param("cutoff") Instant cutoff, Pageable pageable);
}