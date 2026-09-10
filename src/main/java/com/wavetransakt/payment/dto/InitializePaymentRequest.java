package com.wavetransakt.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitializePaymentRequest {

    /*
     * Amount is supplied by Wave Transakt clients in NAIRA.
     *
     * Example:
     *
     * 100.00 = ₦100
     *
     * Conversion to Paystack kobo happens only on the backend.
     */
    @NotNull
    @DecimalMin(value = "100.00")
    private BigDecimal amount;
}