package com.wavetransakt.wallet.transfer;

import com.wavetransakt.wallet.transfer.BankTransferGateway.Bank;
import com.wavetransakt.wallet.transfer.BankTransferGateway.NameEnquiry;
import com.wavetransakt.wallet.transfer.BankTransferGateway.Readiness;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Safe pre-transfer endpoints for the Interswitch Send Money integration.
 *
 * No money-movement endpoint is exposed here until Wave has an approved
 * Interswitch transfer product and funding configuration.
 */
@RestController
@RequestMapping("/api/v1/transfers/interswitch")
@RequiredArgsConstructor
public class InterswitchTransferController {

    private final InterswitchBankTransferGateway gateway;

    @GetMapping("/readiness")
    public ResponseEntity<Readiness> readiness() {
        return ResponseEntity.ok(gateway.readiness());
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
}
