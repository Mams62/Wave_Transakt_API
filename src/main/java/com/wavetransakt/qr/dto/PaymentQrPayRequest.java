package com.wavetransakt.qr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrPayRequest {

    @NotBlank(message = "QR payload is required")
    private String payload;

    @NotBlank(message = "Transaction PIN is required")
    @Pattern(regexp = "\\d{6}", message = "Transaction PIN must be exactly 6 digits")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String transactionPin;
}
