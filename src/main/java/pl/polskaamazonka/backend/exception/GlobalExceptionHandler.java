package pl.polskaamazonka.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.dto.ApiErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = fieldErrors.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "Dane formularza są nieprawidłowe.";
        }
        ApiErrorResponse body = buildResponse(
                HttpStatus.BAD_REQUEST,
                request.getRequestURI(),
                message,
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ActiveAffiliateCodeConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleActiveAffiliateCodeConflict(
            ActiveAffiliateCodeConflictException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Istnieje już aktywny kod afiliacyjny dla tej platformy. Zmodyfikuj go lub najpierw dezaktywuj.";
        }
        ApiErrorResponse body = buildResponse(HttpStatus.BAD_REQUEST, request.getRequestURI(), message, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ShopCategoryDeletionException.class)
    public ResponseEntity<ApiErrorResponse> handleShopCategoryDeletion(
            ShopCategoryDeletionException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Nie można usunąć kategorii powiązanej ze sklepem. Usuń najpierw sklep.";
        }
        ApiErrorResponse body = buildResponse(
                HttpStatus.BAD_REQUEST,
                request.getRequestURI(),
                message,
                null
        );
        body.setErrorCode(ShopCategoryDeletionException.ERROR_CODE);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = defaultMessage(status);
        }
        ApiErrorResponse body = buildResponse(status, request.getRequestURI(), message, null);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getRequestURI(),
                "Wystąpił nieoczekiwany błąd serwera.",
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ApiErrorResponse buildResponse(
            HttpStatus status,
            String path,
            String message,
            Map<String, String> fieldErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setStatus(status.value());
        response.setError(status.getReasonPhrase());
        response.setMessage(message);
        response.setPath(path);
        response.setFieldErrors(fieldErrors);
        return response;
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Żądanie zawiera nieprawidłowe dane.";
            case UNAUTHORIZED -> "Brak autoryzacji lub nieprawidłowe dane logowania.";
            case FORBIDDEN -> "Brak uprawnień do wykonania tej operacji.";
            case NOT_FOUND -> "Nie znaleziono żądanego zasobu.";
            case CONFLICT -> "Operacja koliduje z istniejącymi danymi.";
            default -> "Wystąpił błąd podczas przetwarzania żądania.";
        };
    }
}
