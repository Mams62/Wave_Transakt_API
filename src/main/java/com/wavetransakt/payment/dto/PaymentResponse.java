package com.wavetransakt.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentResponse {

    private String reference;

    /*
     * Wave Transakt amounts are represented in naira.
     */
    private BigDecimal amount;

    /*
     * PENDING / SUCCESSFUL or provider state as appropriate.
     */
    private String status;

    /*
     * Returned only during initialization.
     */
    private String authorizationUrl;

    /*
     * Returned only during initialization.
     *
     * Android passes this directly to Paystack PaymentSheet.
     */
    private String accessCode;

    /*
     * Optional projection returned after settlement.
     */
    private BigDecimal walletBalance;
}