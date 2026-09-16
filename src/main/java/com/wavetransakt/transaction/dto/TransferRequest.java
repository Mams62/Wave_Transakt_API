package com.wavetransakt.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Receiver wallet number is required")
    private String receiverWalletNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "1.00",
            message = "Amount must be at least 1.00"
    )
    private BigDecimal amount;

    private String description;

    @NotBlank(message = "Transaction PIN is required")
    @Pattern(regexp = "\\d{6}", message = "Transaction PIN must be exactly 6 digits")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String transactionPin;
}
