package com.wavetransakt.qr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentQrRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "1.00",
            message = "Amount must be at least 1.00"
    )
    private BigDecimal amount;

    private String description;
}