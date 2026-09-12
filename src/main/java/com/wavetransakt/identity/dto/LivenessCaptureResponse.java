package com.wavetransakt.identity.dto;

import java.util.UUID;

public record LivenessCaptureResponse(
        UUID sessionId,
        String provider,
        String status,
        double livenessProbability,
        boolean faceDetected,
        boolean multipleFacesDetected,
        boolean livenessPassed,
        boolean identityMatchRequired,
        String message
) {}
