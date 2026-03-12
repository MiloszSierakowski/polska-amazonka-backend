package pl.polskaamazonka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import pl.polskaamazonka.backend.model.enums.UserRole;

@Getter
@Setter
@Entity
@Table(name = "\"User\"")
public class User {

    @Id
    @ColumnDefault("nextval('\"User_id_seq\"'::regclass)")
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private UserRole role;
}
