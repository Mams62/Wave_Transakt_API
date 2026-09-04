package com.wavetransakt.qr.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrWalletResponse {

    private String qrType;

    private String qrCode;

    private String accountName;

    private String walletNumber;

    private String currency;

    private boolean active;
}
