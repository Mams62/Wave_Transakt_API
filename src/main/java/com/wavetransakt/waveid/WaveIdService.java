package com.wavetransakt.waveid;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * A Wave ID is a public recipient handle, never a payment credential.
 * A unique database constraint is the final collision guard across replicas.
 */
@Service
@RequiredArgsConstructor
public class WaveIdService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O confusion
    private final UserRepository users;
    private final WalletRepository wallets;

    public static String newCandidate() {
        StringBuilder result = new StringBuilder(8);
        for (int i = 0; i < 4; i++) result.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        for (int i = 0; i < 4; i++) result.append(RANDOM.nextInt(10));
        return result.toString();
    }

    // Used only inside verified registration and row-locked legacy activation.
    public String newUnusedWaveId() {
        for (int i = 0; i < 30; i++) {
            String candidate = newCandidate();
            if (!users.existsByWaveId(candidate)) return candidate;
        }
        throw new IllegalStateException("Wave ID generation is temporarily unavailable");
    }

    @Transactional(readOnly = true)
    public WaveIdRecord myId(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        Wallet wallet = wallets.findByUserId(userId).orElse(null);
        return new WaveIdRecord(user.getWaveId(),
                wallet == null ? null : wallet.getWalletNumber(), user.getFullName(), false);
    }

    /**
     * Existing accounts predating the migration can opt in once.
     * The authenticated database row is locked so repeated activation is idempotent.
     * Account activation never authorizes payments.
     */
    @Transactional
    public WaveIdRecord activate(UUID userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE ||
                !Boolean.TRUE.equals(user.getBvnVerified()) ||
                !Boolean.TRUE.equals(user.getNinVerified())) {
            throw new IllegalStateException("Account verification is required before enabling Wave ID");
        }
        if (user.getWaveId() == null || user.getWaveId().isBlank()) {
            user.setWaveId(newUnusedWaveId());
            users.saveAndFlush(user);
        }
        Wallet wallet = wallets.findByUserId(userId).orElse(null);
        return new WaveIdRecord(user.getWaveId(),
                wallet == null ? null : wallet.getWalletNumber(), user.getFullName(), false);
    }

    @Transactional(readOnly = true)
    public WaveIdRecord resolve(String rawWaveId) {
        String waveId = rawWaveId == null ? "" : rawWaveId.trim().toUpperCase(Locale.ROOT);
        if (!waveId.matches("[A-Z]{4}[0-9]{2,4}")) {
            throw new IllegalArgumentException("Enter a valid Wave ID");
        }
        User user = users.findByWaveId(waveId)
                .orElseThrow(() -> new IllegalArgumentException("Wave ID not found"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Wave ID not found");
        }
        Wallet wallet = wallets.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient wallet unavailable"));
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException("Recipient wallet unavailable");
        }
        return new WaveIdRecord(user.getWaveId(), wallet.getWalletNumber(), user.getFullName(), false);
    }

    public record WaveIdRecord(String waveId, String walletNumber, String displayName,
                               boolean paymentsEnabled) {}
}
