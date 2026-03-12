package pl.polskaamazonka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    @ColumnDefault("nextval('link_id_seq'::regclass)")
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

}