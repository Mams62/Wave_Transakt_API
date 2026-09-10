package com.wavetransakt.config;

import com.wavetransakt.transaction.exception.IdempotencyConflictException;
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
     * Provider gateways such as Wema/ALAT and VTpass may return 4xx/5xx
     * responses. RestTemplate turns those responses into exceptions before the
     * provider payload reaches our client code. Without this handler Android
     * only sees Spring's generic 500 page, which makes controlled testing much
     * harder and can hide a simple sandbox credential/subscription problem.
     *
     * Never return provider response bodies here because they may contain
     * implementation details. Return only a safe, actionable classification.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<?> handleUpstreamProviderHttp(
            RestClientResponseException ex
    ) {
        int upstreamStatus = ex.getStatusCode().value();
        String message;
        String error;

        if (upstreamStatus == 401 || upstreamStatus == 403) {
            message = "Wema sandbox rejected the configured API credentials or Wallet Services subscription.";
            error = "WEMA_AUTH_REJECTED";
        } else if (upstreamStatus == 404) {
            message = "The configured Wema Wallet Services endpoint is not available for this sandbox subscription.";
            error = "WEMA_ENDPOINT_UNAVAILABLE";
        } else if (upstreamStatus == 429) {
            message = "Wema sandbox rate limit reached. Try again shortly.";
            error = "WEMA_RATE_LIMIT";
        } else {
            message = "Wema sandbox request failed with upstream HTTP " + upstreamStatus + ".";
            error = "WEMA_UPSTREAM_ERROR";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("error", error);
        response.put("upstreamStatus", upstreamStatus);

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(response);
    }

    /**
     * Wema client configuration checks intentionally throw IllegalStateException
     * before any network call. Surface only Wema-labelled messages; keep all
     * unrelated internal state exceptions generic.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(
            IllegalStateException ex
    ) {
        String raw = ex.getMessage();
        boolean wemaRelated = raw != null &&
                raw.toLowerCase().contains("wema");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "message",
                wemaRelated
                        ? raw
                        : "A required server component is temporarily unavailable."
        );
        response.put(
                "error",
                wemaRelated
                        ? "WEMA_CONFIGURATION_OR_PROVIDER_ERROR"
                        : "SERVER_COMPONENT_UNAVAILABLE"
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
