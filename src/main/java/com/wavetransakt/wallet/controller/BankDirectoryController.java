package com.wavetransakt.wallet.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.wavetransakt.wallet.dto.WemaTransferDtos.Bank;
import com.wavetransakt.wallet.dto.WemaTransferDtos.BanksResponse;
import com.wavetransakt.wallet.dto.WemaTransferDtos.NameEnquiry;
import com.wavetransakt.wallet.wema.WemaTransferClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
public class BankDirectoryController {

    private final WemaTransferClient wemaTransferClient;

    @GetMapping
    public ResponseEntity<BanksResponse> listBanks() {
        JsonNode payload = wemaTransferClient.getAllBanks();

        Map<String, Bank> unique = new LinkedHashMap<>();
        collectBanks(payload, unique);

        List<Bank> banks = new ArrayList<>(unique.values());
        banks.sort(Comparator.comparing(Bank::bankName, String.CASE_INSENSITIVE_ORDER));

        if (banks.isEmpty()) {
            throw new IllegalStateException(
                    "The bank provider returned no supported banks"
            );
        }

        return ResponseEntity.ok(new BanksResponse(banks));
    }

    @GetMapping("/resolve")
    public ResponseEntity<NameEnquiry> resolveAccount(
            @RequestParam String bankCode,
            @RequestParam String accountNumber
    ) {
        JsonNode payload = wemaTransferClient.accountNameEnquiry(
                bankCode,
                accountNumber
        );

        String accountName = firstRecursiveText(
                payload,
                "accountName",
                "AccountName",
                "account_name",
                "beneficiaryName",
                "BeneficiaryName",
                "destinationAccountName"
        );

        String bankName = firstRecursiveText(
                payload,
                "bankName",
                "BankName",
                "bank_name",
                "destinationBankName"
        );

        String sessionId = firstRecursiveText(
                payload,
                "sessionId",
                "SessionId",
                "sessionID",
                "SessionID"
        );

        String message = firstRecursiveText(
                payload,
                "responseMessage",
                "ResponseMessage",
                "message",
                "Message",
                "errorMessage",
                "ErrorMessage"
        );

        if (accountName.isBlank()) {
            throw new IllegalArgumentException(
                    message.isBlank()
                            ? "Could not resolve account name. Check the selected bank and account number."
                            : message
            );
        }

        return ResponseEntity.ok(
                new NameEnquiry(
                        bankCode.trim(),
                        bankName,
                        accountNumber.trim(),
                        accountName,
                        sessionId,
                        message
                )
        );
    }

    private void collectBanks(JsonNode node, Map<String, Bank> result) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }

        if (node.isObject()) {
            String code = firstDirectText(
                    node,
                    "bankCode",
                    "BankCode",
                    "bank_code",
                    "code",
                    "Code"
            );
            String name = firstDirectText(
                    node,
                    "bankName",
                    "BankName",
                    "bank_name",
                    "name",
                    "Name"
            );

            if (!code.isBlank()
                    && !name.isBlank()
                    && code.matches("[A-Za-z0-9_-]{2,20}")
                    && name.length() <= 120) {
                result.putIfAbsent(code, new Bank(code, name));
            }

            node.elements().forEachRemaining(child -> collectBanks(child, result));
            return;
        }

        if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectBanks(child, result));
        }
    }

    private String firstRecursiveText(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }

        if (node.isObject()) {
            String direct = firstDirectText(node, fieldNames);
            if (!direct.isBlank()) {
                return direct;
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String nested = firstRecursiveText(entry.getValue(), fieldNames);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = firstRecursiveText(child, fieldNames);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }

        return "";
    }

    private String firstDirectText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && value.isValueNode()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
