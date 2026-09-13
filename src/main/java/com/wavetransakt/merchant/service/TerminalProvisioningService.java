package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalStatus;
import com.wavetransakt.merchant.provider.MerchantAcquiringGateway;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Backend-only orchestrator for provider terminal provisioning.
 *
 * No public controller invokes this service. A provider-specific integration may
 * call it only after Wave has the approved product contract/credentials. The
 * gateway contract is treated as fail-closed: provider failures must throw and
 * only a successful TerminalLinkResult containing a real provider terminal ID
 * is eligible to update the Wave terminal record.
 */
@Service
@RequiredArgsConstructor
public class TerminalProvisioningService {

    private final List<MerchantAcquiringGateway> gateways;
    private final PosTerminalLookupRepository terminalLookupRepository;
    private final ProviderTerminalLinkService providerTerminalLinkService;

    @Transactional
    public ProvisioningOutcome provision(
            String providerCode,
            String waveTerminalCode,
            String terminalSerialNumber
    ) {
        String normalizedProvider = normalizeRequired(providerCode, "Provider code").toUpperCase(Locale.ROOT);
        String normalizedTerminalCode = normalizeRequired(waveTerminalCode, "Wave terminal code");
        String normalizedSerial = normalizeRequired(terminalSerialNumber, "Terminal serial number");

        PosTerminal terminal = terminalLookupRepository.findByTerminalCode(normalizedTerminalCode)
                .orElseThrow(() -> new IllegalArgumentException("POS terminal not found"));

        if (terminal.getStatus() != PosTerminalStatus.PENDING_PROVIDER_LINK) {
            throw new IllegalStateException("POS terminal is not awaiting provider link");
        }
        if (terminal.getMerchant() == null || terminal.getMerchant().getMerchantCode() == null) {
            throw new IllegalStateException("POS terminal has no Wave merchant assignment");
        }

        MerchantAcquiringGateway gateway = gateways.stream()
                .filter(candidate -> normalizedProvider.equalsIgnoreCase(candidate.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported acquiring provider"));

        MerchantAcquiringGateway.TerminalLinkResult result = gateway.linkTerminal(
                new MerchantAcquiringGateway.TerminalLinkCommand(
                        terminal.getMerchant().getMerchantCode(),
                        terminal.getTerminalCode(),
                        normalizedSerial
                )
        );

        if (result == null) {
            throw new IllegalStateException("Provider returned no terminal link result");
        }
        if (!normalizedProvider.equalsIgnoreCase(result.provider())) {
            throw new IllegalStateException("Provider terminal link result does not match requested provider");
        }
        if (result.providerTerminalId() == null || result.providerTerminalId().isBlank()) {
            throw new IllegalStateException("Provider terminal link result has no terminal ID");
        }

        PosTerminal linked = providerTerminalLinkService.applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminal.getId(),
                        normalizedProvider,
                        result.providerTerminalId(),
                        result.cardAcceptanceEnabled(),
                        result.contactlessEnabled()
                )
        );

        return new ProvisioningOutcome(
                linked.getTerminalCode(),
                linked.getProviderCode(),
                linked.getProviderTerminalId(),
                linked.isSupportsCard(),
                linked.isSupportsNfc(),
                linked.getStatus().name()
        );
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    public record ProvisioningOutcome(
            String terminalCode,
            String providerCode,
            String providerTerminalId,
            boolean cardEnabled,
            boolean contactlessEnabled,
            String terminalStatus
    ) {
    }
}
