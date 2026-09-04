package com.wavetransakt.qr.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletQrResponse {

    private String qrType;

    private String walletNumber;

    private String accountName;

    private String currency;

    private String payload;

    private String paymentEndpoint;
}