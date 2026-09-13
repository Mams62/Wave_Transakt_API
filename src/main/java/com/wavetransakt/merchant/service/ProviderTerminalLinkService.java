package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalStatus;
import com.wavetransakt.merchant.repository.PosTerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderTerminalLinkService {

    private final PosTerminalRepository posTerminalRepository;

    @Transactional
    public PosTerminal applyProviderAssignment(ProviderTerminalAssignment assignment) {
        if (assignment == null || assignment.terminalId() == null) {
            throw new IllegalArgumentException("Terminal assignment is required");
        }

        String providerCode = normalizeRequired(assignment.providerCode(), "Provider code")
                .toUpperCase(Locale.ROOT);
        String providerTerminalId = normalizeRequired(assignment.providerTerminalId(), "Provider terminal ID");

        PosTerminal terminal = posTerminalRepository.findById(assignment.terminalId())
                .orElseThrow(() -> new IllegalArgumentException("POS terminal not found"));

        if (terminal.getStatus() == PosTerminalStatus.RETIRED) {
            throw new IllegalStateException("Retired POS terminal cannot be provider linked");
        }

        if (hasText(terminal.getProviderCode()) || hasText(terminal.getProviderTerminalId())) {
            boolean sameAssignment = providerCode.equals(terminal.getProviderCode())
                    && providerTerminalId.equals(terminal.getProviderTerminalId());
            if (!sameAssignment) {
                throw new IllegalStateException("POS terminal is already linked to a different provider assignment");
            }
        }

        boolean cardApproved = assignment.cardAcceptanceApproved();
        boolean contactlessApproved = cardApproved && assignment.contactlessApproved();

        terminal.setProviderCode(providerCode);
        terminal.setProviderTerminalId(providerTerminalId);
        terminal.setSupportsCard(cardApproved);
        terminal.setSupportsNfc(contactlessApproved);
        terminal.setStatus(PosTerminalStatus.ACTIVE);

        return posTerminalRepository.save(terminal);
    }

    @Transactional
    public PosTerminal suspend(UUID terminalId) {
        PosTerminal terminal = requireTerminal(terminalId);
        if (terminal.getStatus() == PosTerminalStatus.RETIRED) {
            throw new IllegalStateException("Retired POS terminal cannot be suspended");
        }
        terminal.setStatus(PosTerminalStatus.SUSPENDED);
        return posTerminalRepository.save(terminal);
    }

    @Transactional
    public PosTerminal retire(UUID terminalId) {
        PosTerminal terminal = requireTerminal(terminalId);
        terminal.setStatus(PosTerminalStatus.RETIRED);
        terminal.setSupportsCard(false);
        terminal.setSupportsNfc(false);
        return posTerminalRepository.save(terminal);
    }

    private PosTerminal requireTerminal(UUID terminalId) {
        if (terminalId == null) throw new IllegalArgumentException("Terminal ID is required");
        return posTerminalRepository.findById(terminalId)
                .orElseThrow(() -> new IllegalArgumentException("POS terminal not found"));
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProviderTerminalAssignment(
            UUID terminalId,
            String providerCode,
            String providerTerminalId,
            boolean cardAcceptanceApproved,
            boolean contactlessApproved
    ) {
    }
}
