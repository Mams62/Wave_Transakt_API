package com.wavetransakt.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "Wave Transakt API",
                "status", "UP",
                "version", "1.0.0"
        );
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "Wave Transakt API",
                "status", "UP"
        );
    }
}