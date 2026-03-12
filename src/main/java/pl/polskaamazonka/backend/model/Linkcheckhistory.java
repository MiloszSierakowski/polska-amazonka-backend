package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "linkcheckhistory")
public class Linkcheckhistory {
    @Id
    @ColumnDefault("nextval('linkcheckhistory_id_seq'::regclass)")
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "link_id", nullable = false)
    private Link link;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @ColumnDefault("now()")
    @Column(name = "checked_at")
    private Instant checkedAt;

}