package pl.polskaamazonka.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChangeLogDTO {
    private Long id;
    private Long userId;
    private String userLogin;
    private String action;
    private String details;
    private LocalDateTime createdAt;
}
