package com.wavetransakt.wallet.transfer;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.transfer.BankTransferGateway.Bank;
import com.wavetransakt.wallet.transfer.BankTransferGateway.NameEnquiry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transfers/bank")
@RequiredArgsConstructor
public class ExternalBankTransferController {

    private final ExternalBankTransferOrchestrator orchestrator;
    private final InterswitchBankTransferGateway gateway;

    @GetMapping("/readiness")
    public ResponseEntity<ExternalBankTransferOrchestrator.ExecutionReadiness> readiness() {
        return ResponseEntity.ok(orchestrator.readiness());
    }

    @GetMapping("/banks")
    public ResponseEntity<List<Bank>> banks() {
        return ResponseEntity.ok(gateway.banks());
    }

    @GetMapping("/resolve")
    public ResponseEntity<NameEnquiry> resolve(
            @RequestParam String bankCode,
            @RequestParam String accountNumber
    ) {
        return ResponseEntity.ok(gateway.resolveAccount(bankCode, accountNumber));
    }

    @PostMapping
    public ResponseEntity<TransferResponse> initiate(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        User user = authenticatedUser(authentication);
        ExternalBankTransfer transfer = orchestrator.initiate(
                user.getId(),
                idempotencyKey,
                new ExternalBankTransferOrchestrator.InitiateRequest(
                        request.bankCode(),
                        request.accountNumber(),
                        request.amount(),
                        request.narration(),
                        request.transactionPin()
                )
        );
        return ResponseEntity.ok(toResponse(transfer));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<TransferResponse> get(
            Authentication authentication,
            @PathVariable String reference
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(toResponse(
                orchestrator.findOwned(user.getId(), reference)
        ));
    }

    @PostMapping("/{reference}/requery")
    public ResponseEntity<TransferResponse> requery(
            Authentication authentication,
            @PathVariable String reference
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(toResponse(
                orchestrator.requery(user.getId(), reference)
        ));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authentication required");
        }
        return user;
    }

    private TransferResponse toResponse(ExternalBankTransfer transfer) {
        return new TransferResponse(
                transfer.getReference(),
                transfer.getProvider(),
                transfer.getDestinationBankCode(),
                transfer.getDestinationAccountNumber(),
                transfer.getDestinationAccountName(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getCurrency(),
                transfer.getNarration(),
                transfer.getStatus().name(),
                transfer.getProviderResponseCode(),
                transfer.getProviderMessage(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    public record TransferRequest(
            @NotBlank @Size(max = 20) String bankCode,
            @NotBlank @Pattern(regexp = "^\\d{10}$") String accountNumber,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String narration,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String transactionPin
    ) {}

    public record TransferResponse(
            String reference,
            String provider,
            String destinationBankCode,
            String destinationAccountNumber,
            String destinationAccountName,
            BigDecimal amount,
            BigDecimal fee,
            String currency,
            String narration,
            String status,
            String providerResponseCode,
            String message,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
