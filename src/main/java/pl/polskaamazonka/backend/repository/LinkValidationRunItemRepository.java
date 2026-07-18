package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.LinkValidationRunItem;

import java.util.List;

public interface LinkValidationRunItemRepository extends JpaRepository<LinkValidationRunItem, Long> {
    List<LinkValidationRunItem> findByRun_IdOrderByCheckedAtAscIdAsc(Long runId);
}
