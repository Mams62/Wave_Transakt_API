package com.wavetransakt.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyPaymentRequest {

    @NotBlank(message = "Payment reference is required")
    private String reference;
}