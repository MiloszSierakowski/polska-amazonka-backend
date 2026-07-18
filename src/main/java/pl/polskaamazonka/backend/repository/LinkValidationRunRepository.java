package pl.polskaamazonka.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.polskaamazonka.backend.model.LinkValidationRun;
import pl.polskaamazonka.backend.model.LinkValidationRunStatus;
import pl.polskaamazonka.backend.service.ProductLinkVerificationSource;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LinkValidationRunRepository extends JpaRepository<LinkValidationRun, Long> {
    Optional<LinkValidationRun> findFirstByOrderByStartedAtDescIdDesc();
    Optional<LinkValidationRun> findFirstBySourceOrderByStartedAtDescIdDesc(ProductLinkVerificationSource source);
    Optional<LinkValidationRun> findFirstByStatusNotOrderByStartedAtDescIdDesc(LinkValidationRunStatus status);
    Optional<LinkValidationRun> findFirstByLastErrorIsNotNullOrderByStartedAtDescIdDesc();
    List<LinkValidationRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);
    long countByStatus(LinkValidationRunStatus status);
}
