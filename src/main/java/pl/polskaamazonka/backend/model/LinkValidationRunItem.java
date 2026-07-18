package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pl.polskaamazonka.backend.service.ProductLinkVerificationStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "link_validation_run_item")
public class LinkValidationRunItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private LinkValidationRun run;

    @Column(name = "link_id") private Long linkId;
    @Column(name = "product_id") private Long productId;
    @Column(name = "product_name_snapshot", length = 500) private String productNameSnapshot;
    @Column(name = "original_url", columnDefinition = "TEXT") private String originalUrl;
    @Column(name = "normalized_url", columnDefinition = "TEXT") private String normalizedUrl;
    @Column(name = "final_url", columnDefinition = "TEXT") private String finalUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private ProductLinkVerificationStatus verificationStatus;

    @Column(length = 1000) private String reason;
    @Column(name = "http_status") private Integer httpStatus;
    @Column(name = "duration_ms", nullable = false) private long durationMs;
    @Column(name = "checked_at", nullable = false) private Instant checkedAt;
    @Column(name = "technical_error", nullable = false) private boolean technicalError;
    @Column(name = "previous_is_broken") private Boolean previousIsBroken;
    @Column(name = "new_is_broken") private Boolean newIsBroken;
    @Column(name = "previous_needs_review") private Boolean previousNeedsReview;
    @Column(name = "new_needs_review") private Boolean newNeedsReview;
}
