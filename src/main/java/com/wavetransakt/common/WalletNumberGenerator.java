package com.wavetransakt.common;

import java.security.SecureRandom;

public class WalletNumberGenerator {

    private static final SecureRandom random = new SecureRandom();

    public static String generate() {

        StringBuilder sb = new StringBuilder("78");

        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }
}