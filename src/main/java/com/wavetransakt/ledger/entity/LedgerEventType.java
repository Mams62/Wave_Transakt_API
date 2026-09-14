package com.wavetransakt.ledger.entity;

public enum LedgerEventType {
    OPENING_BALANCE,
    WALLET_TRANSFER,
    PAYSTACK_FUNDING,
    BILL_PAYMENT,
    EXTERNAL_BANK_TRANSFER,
    REVERSAL,
    ADJUSTMENT
}
