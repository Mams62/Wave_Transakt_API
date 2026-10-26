package com.wavetransakt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Never expose email, SMS or recovery OTPs in JSON responses.
 * This fails startup if a legacy demo switch is accidentally enabled in any
 * environment, including staging. It does not change OTP delivery or hashing.
 */
@Component
public class VerificationCodeExposureGuard {

    public VerificationCodeExposureGuard(
            @Value("${wave.demo.return-verification-code:false}") boolean exposeCodes
    ) {
        if (exposeCodes) {
            throw new IllegalStateException(
                    "WAVE_RETURN_VERIFICATION_CODE must remain false: " +
                    "verification codes must only reach users through approved delivery providers."
            );
        }
    }
}
