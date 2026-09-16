package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.entity.UserRole;
import com.wavetransakt.wallet.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-heavy, admin-only operations surface for provider funding exceptions.
 *
 * Operator actions are append-only audit metadata. This service deliberately
 * cannot verify, credit, reject, reverse, edit a wallet balance, or post a
 * ledger transaction. Financial state changes remain inside authenticated
 * provider ingress and the deny-by-default funding credit policy boundary.
 */
@Service
public class CustomerFundingOperationsService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerFundingEventRepository eventRepository;
    private final CustomerFundingReconciliationActionRepository actionRepository;

    public CustomerFundingOperationsService(
            CustomerFundingEventRepository eventRepository,
            CustomerFundingReconciliationActionRepository actionRepository
    ) {
        this.eventRepository = eventRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional(readOnly = true)
    public FundingEventPage listEvents(
            CustomerFundingEventStatus status,
            String providerCode,
            Integer page,
            Integer size,
            User actor
    ) {
        requireAdmin(actor);

        CustomerFundingEventStatus effectiveStatus = status == null
                ? CustomerFundingEventStatus.RECONCILIATION_REQUIRED
                : status;
        String provider = normalizeOptionalProvider(providerCode);
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "receivedAt")
        );

        Page<CustomerFundingEvent> result = provider == null
                ? eventRepository.findByStatus(effectiveStatus, pageable)
                : eventRepository.findByStatusAndProviderCodeIgnoreCase(
                        effectiveStatus,
                        provider,
                        pageable
                );

        return new FundingEventPage(
                result.getContent().stream().map(this::summary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public FundingEventDetail getEvent(UUID eventId, User actor) {
        requireAdmin(actor);
        if (eventId == null) {
            throw new IllegalArgumentException("Funding event ID is required");
        }

        CustomerFundingEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Funding event not found"));

        List<ReconciliationActionView> actions = actionRepository
                .findTop50ByFundingEventIdOrderByCreatedAtDesc(eventId)
                .stream()
                .map(this::actionView)
                .toList();

        return new FundingEventDetail(
                summary(event),
                event.getCreditDecisionReason(),
                actions
        );
    }

    @Transactional
    public ReconciliationActionView recordAction(
            UUID eventId,
            FundingReconciliationActionType actionType,
            FundingReconciliationReasonCode reasonCode,
            User actor
    ) {
        requireAdmin(actor);
        if (eventId == null) {
            throw new IllegalArgumentException("Funding event ID is required");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("Reconciliation action is required");
        }
        if (reasonCode == null) {
            throw new IllegalArgumentException("Reconciliation reason is required");
        }

        CustomerFundingEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Funding event not found"));

        if (event.getStatus() != CustomerFundingEventStatus.RECONCILIATION_REQUIRED) {
            throw new IllegalStateException(
                    "Operational actions are allowed only for funding events requiring reconciliation"
            );
        }

        CustomerFundingReconciliationAction action = CustomerFundingReconciliationAction.builder()
                .fundingEventId(eventId)
                .actionType(actionType)
                .reasonCode(reasonCode)
                .actorUserId(actor.getId())
                .build();

        return actionView(actionRepository.save(action));
    }

    private FundingEventSummary summary(CustomerFundingEvent event) {
        CustomerFundingAccount fundingAccount = event.getFundingAccount();
        UUID fundingAccountId = fundingAccount == null ? null : fundingAccount.getId();
        Wallet wallet = fundingAccount == null ? null : fundingAccount.getWallet();
        UUID walletId = wallet == null ? null : wallet.getId();

        return new FundingEventSummary(
                event.getId(),
                event.getStatus(),
                event.getProviderCode(),
                event.getProviderEventId(),
                event.getProviderReference(),
                event.getAmount(),
                event.getCurrency(),
                fundingAccountId,
                walletId,
                event.getReceivedAt(),
                event.getVerifiedAt(),
                event.getCreditedAt(),
                event.getCreditPolicyCode(),
                event.getCreditDecision(),
                event.getLedgerReference()
        );
    }

    private ReconciliationActionView actionView(CustomerFundingReconciliationAction action) {
        return new ReconciliationActionView(
                action.getId(),
                action.getFundingEventId(),
                action.getActionType(),
                action.getReasonCode(),
                action.getActorUserId(),
                action.getCreatedAt()
        );
    }

    private void requireAdmin(User actor) {
        if (actor == null || actor.getId() == null || actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Funding reconciliation operations require an administrator");
        }
    }

    private String normalizeOptionalProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40 || !normalized.matches("[A-Z0-9_\\-]+")) {
            throw new IllegalArgumentException("Invalid funding provider");
        }
        return normalized;
    }

    public record FundingEventSummary(
            UUID eventId,
            CustomerFundingEventStatus status,
            String providerCode,
            String providerEventId,
            String providerReference,
            BigDecimal amount,
            String currency,
            UUID fundingAccountId,
            UUID walletId,
            LocalDateTime receivedAt,
            LocalDateTime verifiedAt,
            LocalDateTime creditedAt,
            String creditPolicyCode,
            String creditDecision,
            String ledgerReference
    ) {
    }

    public record ReconciliationActionView(
            UUID actionId,
            UUID eventId,
            FundingReconciliationActionType actionType,
            FundingReconciliationReasonCode reasonCode,
            UUID actorUserId,
            LocalDateTime createdAt
    ) {
    }

    public record FundingEventDetail(
            FundingEventSummary event,
            String creditDecisionReason,
            List<ReconciliationActionView> recentActions
    ) {
    }

    public record FundingEventPage(
            List<FundingEventSummary> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
