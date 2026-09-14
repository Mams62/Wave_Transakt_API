package com.wavetransakt.card.service;

import com.wavetransakt.card.entity.WaveCard;
import com.wavetransakt.card.provider.CardIssuingGateway;
import com.wavetransakt.card.repository.WaveCardRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaveCardService {

    private final List<CardIssuingGateway> gateways;
    private final WaveCardRepository cardRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public List<CardView> list(User principal) {
        requirePrincipal(principal);
        return cardRepository.findAllByUserIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(this::view)
                .toList();
    }

    public List<ProviderReadinessView> readiness() {
        return gateways.stream()
                .map(gateway -> {
                    CardIssuingGateway.CardProgramReadiness value = gateway.readiness();
                    return new ProviderReadinessView(
                            gateway.code(),
                            value.configured(),
                            value.approved(),
                            value.cardCreationEnabled(),
                            value.activationEnabled(),
                            value.blockUnblockEnabled(),
                            value.contactlessProgramApproved(),
                            value.status()
                    );
                })
                .toList();
    }

    public CardView requestCard(User principal, String providerCode, String productCode) {
        User user = managedUser(principal);
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Wave wallet is required before card issuance"));

        CardIssuingGateway gateway = gateway(providerCode);
        CardIssuingGateway.CardholderLinkResult result = gateway.createCard(
                new CardIssuingGateway.CardholderLinkCommand(
                        user.getId().toString(),
                        wallet.getWalletNumber(),
                        normalizeOptional(productCode)
                )
        );

        validateCreateResult(gateway, result);

        WaveCard card = WaveCard.builder()
                .user(user)
                .wallet(wallet)
                .provider(gateway.code().toUpperCase(Locale.ROOT))
                .providerCardReference(result.providerCardReference().trim())
                .maskedDisplayReference(normalizeOptional(result.maskedDisplayReference()))
                .productCode(normalizeOptional(productCode))
                .status(normalizeStatus(result.status()))
                .contactlessEnabled(result.contactlessEnabled())
                .build();

        return view(cardRepository.save(card));
    }

    public CardView activate(User principal, UUID cardId) {
        return applyLifecycle(principal, cardId, LifecycleAction.ACTIVATE, null);
    }

    public CardView block(User principal, UUID cardId, String reason) {
        return applyLifecycle(principal, cardId, LifecycleAction.BLOCK, reason);
    }

    public CardView unblock(User principal, UUID cardId, String reason) {
        return applyLifecycle(principal, cardId, LifecycleAction.UNBLOCK, reason);
    }

    public CardView refresh(User principal, UUID cardId) {
        WaveCard card = ownedCard(principal, cardId);
        CardIssuingGateway gateway = gateway(card.getProvider());
        CardIssuingGateway.CardStatusResult result = gateway.status(
                new CardIssuingGateway.CardStatusCommand(card.getProviderCardReference())
        );

        validateProviderReference(gateway, card, result.provider(), result.providerCardReference());
        return saveProviderState(card, result.status(), result.contactlessEnabled());
    }

    private CardView applyLifecycle(
            User principal,
            UUID cardId,
            LifecycleAction action,
            String reason
    ) {
        WaveCard card = ownedCard(principal, cardId);
        CardIssuingGateway gateway = gateway(card.getProvider());
        CardIssuingGateway.CardLifecycleCommand command =
                new CardIssuingGateway.CardLifecycleCommand(
                        card.getProviderCardReference(),
                        normalizeOptional(reason)
                );

        CardIssuingGateway.CardLifecycleResult result = switch (action) {
            case ACTIVATE -> gateway.activate(command);
            case BLOCK -> gateway.block(command);
            case UNBLOCK -> gateway.unblock(command);
        };

        if (result == null) {
            throw new IllegalStateException("Card provider returned no lifecycle result");
        }
        validateProviderReference(gateway, card, result.provider(), result.providerCardReference());
        return saveProviderState(card, result.status(), card.isContactlessEnabled());
    }

    @Transactional
    protected CardView saveProviderState(WaveCard card, String status, boolean contactlessEnabled) {
        card.setStatus(normalizeStatus(status));
        card.setContactlessEnabled(contactlessEnabled);
        return view(cardRepository.save(card));
    }

    private WaveCard ownedCard(User principal, UUID cardId) {
        requirePrincipal(principal);
        if (cardId == null) {
            throw new IllegalArgumentException("Card ID is required");
        }
        return cardRepository.findByIdAndUserId(cardId, principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wave card not found"));
    }

    private User managedUser(User principal) {
        requirePrincipal(principal);
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user no longer exists"));
    }

    private void requirePrincipal(User principal) {
        if (principal == null || principal.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
    }

    private CardIssuingGateway gateway(String providerCode) {
        String code = providerCode == null || providerCode.isBlank()
                ? "INTERSWITCH"
                : providerCode.trim();
        return gateways.stream()
                .filter(candidate -> candidate.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported card issuing provider"));
    }

    private void validateCreateResult(
            CardIssuingGateway gateway,
            CardIssuingGateway.CardholderLinkResult result
    ) {
        if (result == null) {
            throw new IllegalStateException("Card provider returned no issuance result");
        }
        if (!gateway.code().equalsIgnoreCase(result.provider())) {
            throw new IllegalStateException("Card provider result does not match requested provider");
        }
        if (result.providerCardReference() == null || result.providerCardReference().isBlank()) {
            throw new IllegalStateException("Card provider returned no opaque card reference");
        }
        normalizeStatus(result.status());
    }

    private void validateProviderReference(
            CardIssuingGateway gateway,
            WaveCard card,
            String resultProvider,
            String resultReference
    ) {
        if (resultProvider == null || !gateway.code().equalsIgnoreCase(resultProvider)) {
            throw new IllegalStateException("Card lifecycle result provider mismatch");
        }
        if (resultReference == null ||
                !card.getProviderCardReference().equals(resultReference.trim())) {
            throw new IllegalStateException("Card lifecycle result reference mismatch");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalStateException("Card provider returned no status");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_\\-]{2,40}")) {
            throw new IllegalStateException("Card provider returned an invalid status");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private CardView view(WaveCard card) {
        return new CardView(
                card.getId(),
                card.getProvider(),
                card.getMaskedDisplayReference(),
                card.getProductCode(),
                card.getStatus(),
                card.isContactlessEnabled(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    private enum LifecycleAction { ACTIVATE, BLOCK, UNBLOCK }

    public record CardView(
            UUID id,
            String provider,
            String maskedDisplayReference,
            String productCode,
            String status,
            boolean contactlessEnabled,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {}

    public record ProviderReadinessView(
            String provider,
            boolean configured,
            boolean approved,
            boolean cardCreationEnabled,
            boolean activationEnabled,
            boolean blockUnblockEnabled,
            boolean contactlessProgramApproved,
            String status
    ) {}
}
