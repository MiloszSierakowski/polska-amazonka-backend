package pl.polskaamazonka.backend.repository.specification;

import org.springframework.data.jpa.domain.Specification;
import pl.polskaamazonka.backend.model.ChangeLog;

import java.time.LocalDateTime;

public final class ChangeLogSpecifications {

    private ChangeLogSpecifications() {
    }

    public static Specification<ChangeLog> withFilters(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return Specification
                .where(hasUserId(userId))
                .and(createdAtFrom(startDate))
                .and(createdAtTo(endDate));
    }

    private static Specification<ChangeLog> hasUserId(Long userId) {
        return (root, query, builder) -> userId == null
                ? builder.conjunction()
                : builder.equal(root.get("userId"), userId);
    }

    private static Specification<ChangeLog> createdAtFrom(LocalDateTime startDate) {
        return (root, query, builder) -> startDate == null
                ? builder.conjunction()
                : builder.greaterThanOrEqualTo(root.get("createdAt"), startDate);
    }

    private static Specification<ChangeLog> createdAtTo(LocalDateTime endDate) {
        return (root, query, builder) -> endDate == null
                ? builder.conjunction()
                : builder.lessThanOrEqualTo(root.get("createdAt"), endDate);
    }
}
