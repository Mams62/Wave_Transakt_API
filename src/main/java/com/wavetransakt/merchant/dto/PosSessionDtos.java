package com.wavetransakt.merchant.dto;

import java.time.LocalDateTime;

public final class PosSessionDtos {
    private PosSessionDtos() {}

    public record PairingCodeResponse(
            String terminalCode,
            String pairingCode,
            LocalDateTime expiresAt
    ) {}

    public record RedeemPairingRequest(
            String pairingCode,
            String deviceLabel
    ) {}

    public record TerminalSessionResponse(
            String terminalCode,
            String sessionToken,
            LocalDateTime expiresAt
    ) {}

    public record RevokeSessionsResponse(
            String terminalCode,
            int revokedSessions
    ) {}
}
