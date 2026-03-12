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
@Table(name = "clickstat")
public class Clickstat {
    @Id
    @ColumnDefault("nextval('clickstat_id_seq'::regclass)")
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Integer entityId;

    @ColumnDefault("now()")
    @Column(name = "clicked_at")
    private Instant clickedAt;

}