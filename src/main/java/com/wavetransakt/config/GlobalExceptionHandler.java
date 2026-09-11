package com.wavetransakt.config;

import com.wavetransakt.transaction.exception.IdempotencyConflictException;
import com.wavetransakt.wallet.wema.WemaProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<?> handleIdempotencyConflict(
            IdempotencyConflictException ex
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", "IDEMPOTENCY_CONFLICT");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", ex.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Wema failures are wrapped at the Wema client boundary so the app receives
     * an actionable classification without exposing raw bank payloads or keys.
     */
    @ExceptionHandler(WemaProviderException.class)
    public ResponseEntity<?> handleWemaProvider(
            WemaProviderException ex
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", ex.getErrorCode());
        if (ex.getUpstreamStatus() != null) {
            response.put("upstreamStatus", ex.getUpstreamStatus());
        }

        HttpStatus status = switch (ex.getErrorCode()) {
            case "WEMA_API_KEY_MISSING", "WEMA_BASE_URL_INVALID" ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case "WEMA_REQUEST_REJECTED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Other provider clients (for example the controlled VTpass catalog) may
     * still use RestTemplate directly. Do not mislabel those failures as Wema.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<?> handleGenericUpstreamProviderHttp(
            RestClientResponseException ex
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "message",
                "An upstream provider request failed with HTTP " +
                        ex.getStatusCode().value() + "."
        );
        response.put("error", "UPSTREAM_PROVIDER_ERROR");
        response.put("upstreamStatus", ex.getStatusCode().value());

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(
            IllegalStateException ex
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "message",
                "A required server component is temporarily unavailable."
        );
        response.put("error", "SERVER_COMPONENT_UNAVAILABLE");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
