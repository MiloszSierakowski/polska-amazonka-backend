package pl.polskaamazonka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "changelog")
@SequenceGenerator(name = "changelog_seq", sequenceName = "changelog_id_seq", allocationSize = 1)
public class ChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "changelog_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "details", nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
