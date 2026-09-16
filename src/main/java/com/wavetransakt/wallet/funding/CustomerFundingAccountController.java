package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet/funding-accounts")
public class CustomerFundingAccountController {

    private final CustomerFundingAccountService service;

    public CustomerFundingAccountController(CustomerFundingAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FundingAccountResponse>> list(Authentication authentication) {
        User user = authenticatedUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                service.listForUser(user.getId()).stream()
                        .map(FundingAccountResponse::from)
                        .toList()
        );
    }

    @GetMapping("/readiness")
    public ResponseEntity<ReadinessResponse> readiness(Authentication authentication) {
        if (authenticatedUser(authentication) == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new ReadinessResponse(service.isProvisioningEnabled()));
    }

    @PostMapping("/provision")
    public ResponseEntity<FundingAccountResponse> provision(
            Authentication authentication,
            @RequestBody ProvisionRequest request
    ) {
        User user = authenticatedUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                FundingAccountResponse.from(service.provision(user.getId(), request.provider()))
        );
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }

    public record ProvisionRequest(String provider) {
    }

    public record ReadinessResponse(boolean provisioningEnabled) {
    }

    public record FundingAccountResponse(
            String provider,
            CustomerFundingAccountStatus status,
            String accountNumber,
            String bankCode,
            String bankName,
            String accountName,
            LocalDateTime activatedAt
    ) {
        static FundingAccountResponse from(CustomerFundingAccount account) {
            return new FundingAccountResponse(
                    account.getProviderCode(),
                    account.getStatus(),
                    account.getAccountNumber(),
                    account.getBankCode(),
                    account.getBankName(),
                    account.getAccountName(),
                    account.getActivatedAt()
            );
        }
    }
}
