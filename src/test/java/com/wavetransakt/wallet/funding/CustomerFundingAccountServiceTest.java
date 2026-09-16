package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerFundingAccountServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private CustomerFundingAccountRepository fundingAccountRepository;

    @Test
    void provisioningIsFailClosedBeforeAnyProviderOrDatabaseCall() {
        CustomerFundingAccountService service = new CustomerFundingAccountService(
                walletRepository,
                fundingAccountRepository,
                List.of(),
                false
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.provision(UUID.randomUUID(), "PAYSTACK")
        );

        assertEquals("Customer funding account provisioning is disabled", exception.getMessage());
        verifyNoInteractions(walletRepository, fundingAccountRepository);
    }

    @Test
    void approvedProviderCanProvisionThroughProviderNeutralBoundaryWhenExplicitlyEnabled() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .firstName("Wave")
                .lastName("Tester")
                .email("wave-tester@example.test")
                .phone("08000000000")
                .password("not-used")
                .bvnVerified(true)
                .ninVerified(true)
                .build();

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .walletNumber("9000000001")
                .build();

        CustomerFundingAccountProvider provider = new CustomerFundingAccountProvider() {
            @Override
            public String code() {
                return "PAYSTACK";
            }

            @Override
            public ProvisioningReadiness readiness() {
                return new ProvisioningReadiness(true, "staging ready");
            }

            @Override
            public ProvisioningResult provision(ProvisioningRequest request) {
                assertEquals(userId, request.userId());
                assertEquals(walletId, request.walletId());
                assertTrue(request.bvnVerified());
                assertTrue(request.ninVerified());
                return new ProvisioningResult(
                        "provider-account-ref",
                        "0123456789",
                        "999",
                        "Sandbox Bank",
                        "Wave Tester",
                        "created in sandbox"
                );
            }
        };

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(fundingAccountRepository.findByWalletIdAndProviderCode(walletId, "PAYSTACK"))
                .thenReturn(Optional.empty());
        when(fundingAccountRepository.save(any(CustomerFundingAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerFundingAccountService service = new CustomerFundingAccountService(
                walletRepository,
                fundingAccountRepository,
                List.of(provider),
                true
        );

        CustomerFundingAccount account = service.provision(userId, "paystack");

        assertEquals(CustomerFundingAccountStatus.ACTIVE, account.getStatus());
        assertEquals("PAYSTACK", account.getProviderCode());
        assertEquals("0123456789", account.getAccountNumber());
        assertEquals("Sandbox Bank", account.getBankName());
        assertNotNull(account.getActivatedAt());
        verify(fundingAccountRepository, times(2)).save(any(CustomerFundingAccount.class));
    }
}
