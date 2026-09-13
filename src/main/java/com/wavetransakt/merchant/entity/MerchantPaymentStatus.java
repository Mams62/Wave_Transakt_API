package com.wavetransakt.merchant.entity;

public enum MerchantPaymentStatus {
    CREATED,
    PENDING_PROVIDER,
    AUTHORIZED,
    SUCCEEDED,
    FAILED,
    REVERSED
}
