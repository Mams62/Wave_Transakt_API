package com.wavetransakt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record InitializePaymentRequest(
        @NotNull
        @DecimalMin(value = "100.00", message = "Minimum funding amount is ₦100")
        BigDecimal amount
) {}
