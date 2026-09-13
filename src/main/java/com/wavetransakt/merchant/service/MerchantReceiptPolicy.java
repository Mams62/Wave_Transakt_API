package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.MerchantPaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class MerchantReceiptPolicy {

    public boolean canIssueFinalReceipt(MerchantPaymentStatus status) {
        return status == MerchantPaymentStatus.SUCCEEDED;
    }

    public void requireFinalReceiptAllowed(MerchantPaymentStatus status) {
        if (!canIssueFinalReceipt(status)) {
            throw new IllegalStateException(
                    "Final receipt requires a provider-confirmed successful payment"
            );
        }
    }
}
