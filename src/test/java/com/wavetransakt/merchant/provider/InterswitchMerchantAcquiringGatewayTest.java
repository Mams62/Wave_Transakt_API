package com.wavetransakt.merchant.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterswitchMerchantAcquiringGatewayTest {

    private InterswitchMerchantAcquiringGateway disabledGateway() {
        return new InterswitchMerchantAcquiringGateway(
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    @Test
    void merchantProvisioningFailsClosedWhenDisabled() {
        MerchantAcquiringOperationException error = assertThrows(
                MerchantAcquiringOperationException.class,
                () -> disabledGateway().provisionMerchant(
                        new MerchantAcquiringGateway.MerchantProvisioningCommand(
                                "WTM-123",
                                "Example Store",
                                "RETAIL",
                                "08000000000",
                                "merchant@example.com"
                        )
                )
        );

        assertTrue(error.getMessage().contains("not enabled"));
    }

    @Test
    void terminalLinkFailsClosedWhenDisabled() {
        assertThrows(
                MerchantAcquiringOperationException.class,
                () -> disabledGateway().linkTerminal(
                        new MerchantAcquiringGateway.TerminalLinkCommand(
                                "WTM-123",
                                "WTPOS-123",
                                "SERIAL-123"
                        )
                )
        );
    }

    @Test
    void transactionInquiryFailsClosedWhenDisabled() {
        assertThrows(
                MerchantAcquiringOperationException.class,
                () -> disabledGateway().queryTransaction(
                        new MerchantAcquiringGateway.TransactionInquiryCommand(
                                "PROVIDER-REF",
                                "WAVE-REF"
                        )
                )
        );
    }

    @Test
    void settlementInquiryFailsClosedWhenDisabled() {
        assertThrows(
                MerchantAcquiringOperationException.class,
                () -> disabledGateway().querySettlement(
                        new MerchantAcquiringGateway.SettlementInquiryCommand(
                                "SETTLEMENT-REF",
                                "MERCHANT-ID"
                        )
                )
        );
    }

    @Test
    void webhookVerificationFailsClosedWhenDisabled() {
        assertThrows(
                MerchantAcquiringOperationException.class,
                () -> disabledGateway().verifyProviderEvent(
                        new MerchantAcquiringGateway.ProviderEventVerificationCommand(
                                "EVENT-1",
                                "TRANSACTION.COMPLETED",
                                "signature",
                                "timestamp",
                                new byte[]{1, 2, 3}
                        )
                )
        );
    }
}
