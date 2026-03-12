package pl.polskaamazonka.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import pl.polskaamazonka.backend.model.enums.AffiliateCodeType;
import pl.polskaamazonka.backend.model.enums.Platform;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "affiliatecode")
public class AffiliateCode {

    @Id
    @ColumnDefault("nextval('affiliatecode_id_seq'::regclass)")
    @Column(name = "id", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 50)
    private Platform platform;

    @Column(name = "code_value", nullable = false, length = 255)
    private String codeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AffiliateCodeType type;

    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean isActive;

    @ColumnDefault("now()")
    @Column(name = "created_at")
    private Instant createdAt;
}
