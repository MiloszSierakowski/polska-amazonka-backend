package pl.polskaamazonka.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ApiErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;
}
