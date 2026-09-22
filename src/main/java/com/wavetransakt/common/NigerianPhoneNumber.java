package com.wavetransakt.common;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Canonical Nigerian phone identity used by Wave Transakt.
 *
 * Customer-facing Wave account numbers use the local 11-digit form:
 * 0XXXXXXXXXX. Provider-issued bank/DVA account numbers are separate.
 */
public final class NigerianPhoneNumber {

    private static final Pattern SAFE_INPUT =
            Pattern.compile("^[+0-9()\\-\\s]+$");

    private NigerianPhoneNumber() {
    }

    public static String toLocal(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        String raw = value.trim();
        if (!SAFE_INPUT.matcher(raw).matches()) {
            throw invalid();
        }

        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.matches("0[0-9]{10}")) {
            return digits;
        }

        if (digits.matches("234[0-9]{10}")) {
            return "0" + digits.substring(3);
        }

        if (digits.matches("[0-9]{10}")) {
            return "0" + digits;
        }

        throw invalid();
    }

    public static Optional<String> tryToLocal(String value) {
        try {
            return Optional.of(toLocal(value));
        } catch (IllegalArgumentException invalidPhone) {
            return Optional.empty();
        }
    }

    public static String toInternationalDigits(String value) {
        String local = toLocal(value);
        return "234" + local.substring(1);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Phone number must be a valid Nigerian number"
        );
    }
}
