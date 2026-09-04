package com.wavetransakt.qr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrPayloadRequest {

    @NotBlank(message = "QR payload is required")
    private String payload;
}