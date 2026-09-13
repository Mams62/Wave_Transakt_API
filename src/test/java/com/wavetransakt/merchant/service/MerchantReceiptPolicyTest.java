package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.MerchantPaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MerchantReceiptPolicyTest {

    private final MerchantReceiptPolicy policy = new MerchantReceiptPolicy();

    @Test
    void onlyProviderConfirmedSuccessCanIssueFinalReceipt() {
        assertTrue(policy.canIssueFinalReceipt(MerchantPaymentStatus.SUCCEEDED));
        assertFalse(policy.canIssueFinalReceipt(MerchantPaymentStatus.CREATED));
        assertFalse(policy.canIssueFinalReceipt(MerchantPaymentStatus.PENDING_PROVIDER));
        assertFalse(policy.canIssueFinalReceipt(MerchantPaymentStatus.AUTHORIZED));
        assertFalse(policy.canIssueFinalReceipt(MerchantPaymentStatus.FAILED));
        assertFalse(policy.canIssueFinalReceipt(MerchantPaymentStatus.REVERSED));
    }

    @Test
    void rejectsFinalReceiptForNonSuccessfulState() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.requireFinalReceiptAllowed(MerchantPaymentStatus.AUTHORIZED)
        );
    }
}
