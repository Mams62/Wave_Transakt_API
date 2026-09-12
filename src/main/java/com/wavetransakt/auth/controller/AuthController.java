package com.wavetransakt.auth.controller;

import com.wavetransakt.auth.dto.AuthResponse;
import com.wavetransakt.auth.dto.LoginOtpRequest;
import com.wavetransakt.auth.entity.FaceLoginChallenge;
import com.wavetransakt.auth.service.AuthService;
import com.wavetransakt.auth.service.FaceLoginChallengeService;
import com.wavetransakt.identity.dto.LivenessCaptureResponse;
import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.user.dto.LoginRequest;
import com.wavetransakt.user.dto.RegisterRequest;
import com.wavetransakt.user.dto.UserProfileResponse;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WalletRepository walletRepository;
    private final FaceLoginChallengeService faceLoginChallengeService;
    private final LivenessService livenessService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** Starts credential login. No JWT is issued yet. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Verifies registered-phone OTP and returns only a short-lived face challenge. */
    @PostMapping("/login/otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody LoginOtpRequest request) {
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    /** Creates a LOGIN-purpose liveness session bound to the OTP-approved challenge. */
    @PostMapping("/login/face/start")
    public ResponseEntity<LivenessSessionResponse> startFaceLogin(@RequestBody FaceChallengeRequest request) {
        FaceLoginChallenge challenge = faceLoginChallengeService.requireActive(request.faceChallengeToken());
        LivenessSessionResponse response = livenessService.start(challenge.getUser(), "LOGIN");
        faceLoginChallengeService.bindLivenessSession(request.faceChallengeToken(), response.sessionId());
        return ResponseEntity.ok(response);
    }

    /** Sends the transient selfie through liveness + account-identity matching. */
    @PostMapping("/login/face/capture")
    public ResponseEntity<LivenessCaptureResponse> captureFaceLogin(@RequestBody FaceCaptureRequest request) {
        FaceLoginChallenge challenge = faceLoginChallengeService.requireActive(request.faceChallengeToken());
        if (challenge.getLivenessSessionId() == null ||
                !challenge.getLivenessSessionId().equals(request.sessionId())) {
            throw new IllegalArgumentException("Liveness session does not belong to this login challenge");
        }
        return ResponseEntity.ok(
                livenessService.capture(challenge.getUser(), request.sessionId(), request.imageBase64())
        );
    }

    /** Issues the normal API JWT only after the bound liveness session is VERIFIED. */
    @PostMapping("/login/face/complete")
    public ResponseEntity<AuthResponse> completeFaceLogin(@RequestBody FaceCompleteRequest request) {
        String jwt = faceLoginChallengeService.complete(request.faceChallengeToken(), request.sessionId());
        return ResponseEntity.ok(
                AuthResponse.builder()
                        .message("Login verified successfully")
                        .token(jwt)
                        .requiresOtp(false)
                        .faceVerificationRequired(false)
                        .build()
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            return ResponseEntity.status(401).body("Invalid authentication");
        }

        String walletNumber = walletRepository.findByUserId(user.getId())
                .map(Wallet::getWalletNumber)
                .orElse("");

        return ResponseEntity.ok(UserProfileResponse.from(user, walletNumber));
    }

    public record FaceChallengeRequest(String faceChallengeToken) {}

    public record FaceCaptureRequest(
            String faceChallengeToken,
            UUID sessionId,
            String imageBase64
    ) {}

    public record FaceCompleteRequest(
            String faceChallengeToken,
            UUID sessionId
    ) {}
}
