package com.wavetransakt.wallet.funding;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Provider-neutral ingress/state machine for incoming customer funding notices.
 *
 * Provider-specific webhook adapters must authenticate/verify their own
 * callbacks before promoting an event to VERIFIED. This service intentionally
 * contains no wallet-credit or ledger-posting method. Until Wave has an
 * approved provider finality/settlement contract, a funding webhook cannot make
 * money spendable.
 */
@Service
public class CustomerFundingEventService {

    private final CustomerFundingEventRepository eventRepository;
    private final CustomerFundingAccountRepository fundingAccountRepository;

    public CustomerFundingEventService(
            CustomerFundingEventRepository eventRepository,
            CustomerFundingAccountRepository fundingAccountRepository
    ) {
        this.eventRepository = eventRepository;
        this.fundingAccountRepository = fundingAccountRepository;
    }

    public CustomerFundingEvent recordReceived(ReceivedFundingNotice notice) {
        if (notice == null) {
            throw new IllegalArgumentException("Funding notice is required");
        }

        String providerCode = normalizeRequired(notice.providerCode(), 40, "Provider code").toUpperCase(Locale.ROOT);
        String providerEventId = normalizeRequired(notice.providerEventId(), 160, "Provider event ID");

        CustomerFundingEvent existing = eventRepository
                .findByProviderCodeAndProviderEventId(providerCode, providerEventId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        String accountNumber = normalizeOptional(notice.accountNumber(), 32, "Account number");
        String providerAccountReference = normalizeOptional(
                notice.providerAccountReference(),
                120,
                "Provider account reference"
        );

        CustomerFundingAccount fundingAccount = resolveFundingAccount(
                providerCode,
                accountNumber,
                providerAccountReference
        );

        BigDecimal amount = normalizeAmount(notice.amount());
        String currency = normalizeCurrency(notice.currency());

        CustomerFundingEvent event = CustomerFundingEvent.builder()
                .fundingAccount(fundingAccount)
                .providerCode(providerCode)
                .providerEventId(providerEventId)
                .providerReference(normalizeOptional(notice.providerReference(), 160, "Provider reference"))
                .status(fundingAccount == null
                        ? CustomerFundingEventStatus.RECONCILIATION_REQUIRED
                        : CustomerFundingEventStatus.RECEIVED)
                .amount(amount)
                .currency(currency)
                .build();

        try {
            return eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicateRace) {
            return eventRepository
                    .findByProviderCodeAndProviderEventId(providerCode, providerEventId)
                    .orElseThrow(() -> duplicateRace);
        }
    }

    @Transactional
    public CustomerFundingEvent markVerified(UUID eventId) {
        CustomerFundingEvent event = locked(eventId);

        if (event.getStatus() == CustomerFundingEventStatus.REJECTED ||
                event.getStatus() == CustomerFundingEventStatus.REVERSED ||
                event.getStatus() == CustomerFundingEventStatus.CREDITED) {
            throw new IllegalStateException("Funding event is already in a terminal state");
        }

        if (event.getFundingAccount() == null) {
            event.setStatus(CustomerFundingEventStatus.RECONCILIATION_REQUIRED);
            return eventRepository.save(event);
        }

        event.setStatus(CustomerFundingEventStatus.VERIFIED);
        event.setVerifiedAt(LocalDateTime.now());
        return eventRepository.save(event);
    }

    @Transactional
    public CustomerFundingEvent markReconciliationRequired(UUID eventId) {
        CustomerFundingEvent event = locked(eventId);
        if (event.getStatus() == CustomerFundingEventStatus.CREDITED ||
                event.getStatus() == CustomerFundingEventStatus.REVERSED) {
            throw new IllegalStateException("Settled funding event cannot be moved back to reconciliation");
        }
        event.setStatus(CustomerFundingEventStatus.RECONCILIATION_REQUIRED);
        return eventRepository.save(event);
    }

    @Transactional
    public CustomerFundingEvent markRejected(UUID eventId) {
        CustomerFundingEvent event = locked(eventId);
        if (event.getStatus() == CustomerFundingEventStatus.CREDITED) {
            throw new IllegalStateException("Credited funding event cannot be rejected");
        }
        event.setStatus(CustomerFundingEventStatus.REJECTED);
        return eventRepository.save(event);
    }

    private CustomerFundingAccount resolveFundingAccount(
            String providerCode,
            String accountNumber,
            String providerAccountReference
    ) {
        if (accountNumber != null) {
            CustomerFundingAccount byNumber = fundingAccountRepository
                    .findByProviderCodeAndAccountNumber(providerCode, accountNumber)
                    .orElse(null);
            if (byNumber != null) {
                return byNumber;
            }
        }

        if (providerAccountReference != null) {
            return fundingAccountRepository
                    .findByProviderCodeAndProviderAccountReference(providerCode, providerAccountReference)
                    .orElse(null);
        }

        return null;
    }

    private CustomerFundingEvent locked(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Funding event ID is required");
        }
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Funding event not found"));
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Funding amount must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Funding amount must not contain more than 2 decimal places");
        }
    }

    private String normalizeCurrency(String value) {
        String normalized = normalizeRequired(value, 3, "Currency").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Invalid currency");
        }
        return normalized;
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = normalizeOptional(value, maxLength, label);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String label) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    public record ReceivedFundingNotice(
            String providerCode,
            String providerEventId,
            String providerReference,
            String providerAccountReference,
            String accountNumber,
            BigDecimal amount,
            String currency
    ) {
    }
}
