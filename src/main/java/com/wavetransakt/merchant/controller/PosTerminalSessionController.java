package com.wavetransakt.merchant.controller;

import com.wavetransakt.merchant.dto.PosDtos;
import com.wavetransakt.merchant.dto.PosSessionDtos;
import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.service.PosClientService;
import com.wavetransakt.merchant.service.PosTerminalSessionService;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PosTerminalSessionController {

    private static final String POS_SESSION_HEADER = "X-Wave-POS-Session";

    private final PosTerminalSessionService sessionService;
    private final PosClientService posClientService;

    @PostMapping("/business/pos/terminals/{terminalCode}/pairing-code")
    public ResponseEntity<PosSessionDtos.PairingCodeResponse> createPairingCode(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(sessionService.createPairingCode(user.getId(), terminalCode));
    }

    @DeleteMapping("/business/pos/terminals/{terminalCode}/sessions")
    public ResponseEntity<PosSessionDtos.RevokeSessionsResponse> revokeSessions(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(sessionService.revokeAll(user.getId(), terminalCode));
    }

    @PostMapping("/pos/pairing/redeem")
    public ResponseEntity<PosSessionDtos.TerminalSessionResponse> redeem(
            @RequestBody PosSessionDtos.RedeemPairingRequest request
    ) {
        return ResponseEntity.ok(sessionService.redeem(request));
    }

    @GetMapping("/pos/session/profile")
    public ResponseEntity<PosDtos.PosProfileResponse> profile(
            @RequestHeader(POS_SESSION_HEADER) String token
    ) {
        PosTerminal terminal = sessionService.requireValidSession(token);
        return ResponseEntity.ok(posClientService.getProfile(ownerId(terminal), terminal.getTerminalCode()));
    }

    @GetMapping("/pos/session/qr")
    public ResponseEntity<PosDtos.PosQrResponse> qr(
            @RequestHeader(POS_SESSION_HEADER) String token
    ) {
        PosTerminal terminal = sessionService.requireValidSession(token);
        return ResponseEntity.ok(posClientService.getQr(ownerId(terminal), terminal.getTerminalCode()));
    }

    @GetMapping("/pos/session/transactions")
    public ResponseEntity<List<PosDtos.PosTransactionResponse>> transactions(
            @RequestHeader(POS_SESSION_HEADER) String token
    ) {
        PosTerminal terminal = sessionService.requireValidSession(token);
        return ResponseEntity.ok(posClientService.getTransactions(ownerId(terminal), terminal.getTerminalCode()));
    }

    @GetMapping("/pos/session/transactions/{paymentId}/receipt")
    public ResponseEntity<PosDtos.PosReceiptResponse> receipt(
            @RequestHeader(POS_SESSION_HEADER) String token,
            @PathVariable UUID paymentId
    ) {
        PosTerminal terminal = sessionService.requireValidSession(token);
        return ResponseEntity.ok(posClientService.getReceipt(ownerId(terminal), terminal.getTerminalCode(), paymentId));
    }

    @GetMapping("/pos/session/settlements")
    public ResponseEntity<List<PosDtos.PosSettlementResponse>> settlements(
            @RequestHeader(POS_SESSION_HEADER) String token
    ) {
        PosTerminal terminal = sessionService.requireValidSession(token);
        return ResponseEntity.ok(posClientService.getSettlements(ownerId(terminal), terminal.getTerminalCode()));
    }

    private UUID ownerId(PosTerminal terminal) {
        if (terminal.getMerchant() == null || terminal.getMerchant().getOwner() == null) {
            throw new IllegalArgumentException("POS terminal is not attached to an owner");
        }
        return terminal.getMerchant().getOwner().getId();
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
