package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.polskaamazonka.backend.model.ClickStat;

import java.time.Instant;
import java.util.List;

public interface ClickStatRepository extends JpaRepository<ClickStat, Long> {

    interface ClickStatAggregationProjection {
        String getEntityType();

        Long getEntityId();

        Long getClickCount();
    }

    @Query("""
            SELECT c.entityType AS entityType, c.entityId AS entityId, COUNT(c) AS clickCount
            FROM ClickStat c
            WHERE c.clickedAt >= :from AND c.clickedAt <= :to
            GROUP BY c.entityType, c.entityId
            ORDER BY COUNT(c) DESC
            """)
    List<ClickStatAggregationProjection> countGroupedByEntityBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
