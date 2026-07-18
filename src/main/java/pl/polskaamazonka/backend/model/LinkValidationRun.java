package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.polskaamazonka.backend.service.ProductLinkVerificationSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "link_validation_run")
public class LinkValidationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductLinkVerificationSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LinkValidationRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;
    @Column(name = "checked_count", nullable = false)
    private int checkedCount;
    @Column(name = "working_count", nullable = false)
    private int workingCount;
    @Column(name = "broken_count", nullable = false)
    private int brokenCount;
    @Column(name = "uncertain_count", nullable = false)
    private int uncertainCount;
    @Column(name = "blocked_count", nullable = false)
    private int blockedCount;
    @Column(name = "technical_error_count", nullable = false)
    private int technicalErrorCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "triggered_by", length = 255)
    private String triggeredBy;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LinkValidationRunItem> items = new ArrayList<>();
}
