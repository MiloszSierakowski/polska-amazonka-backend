package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "link")
public class Link {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "image_path")
    private String imagePath;

    @ColumnDefault("0")
    @Column(name = "display_order", nullable = false)
    private Long displayOrder;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @ColumnDefault("false")
    @Column(name = "is_broken", nullable = false)
    private Boolean isBroken;

    @ColumnDefault("false")
    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @PrePersist
    public void prePersist() {
        if (displayOrder == null) {
            displayOrder = 0L;
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
        if (isBroken == null) {
            isBroken = Boolean.FALSE;
        }
        if (needsReview == null) {
            needsReview = Boolean.FALSE;
        }
    }
}
