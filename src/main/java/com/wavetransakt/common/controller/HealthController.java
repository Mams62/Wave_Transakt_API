package com.wavetransakt.common.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Basic process liveness; intentionally independent of DB availability. */
    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of("service", "Wave Transakt API", "status", "UP", "version", "1.0.0");
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("service", "Wave Transakt API", "status", "UP");
    }

    /**
     * Read-only dependency readiness probe. Does not enumerate tables, accounts,
     * wallets, financial balances, migrations, or any credential information.
     * Keep the existing /api/health liveness endpoint unchanged for Render.
     */
    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, Object>> databaseReady() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                if (result.next() && result.getInt(1) == 1) {
                    return ResponseEntity.ok(
                        Map.of("service", "Wave Transakt API", "status", "READY")
                    );
                }
            }
        } catch (Exception ignored) {
            // The health API must never leak internal connection URLs or errors.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("service", "Wave Transakt API", "status", "NOT_READY"));
    }
}
