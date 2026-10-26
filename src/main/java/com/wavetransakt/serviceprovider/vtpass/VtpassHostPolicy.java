package com.wavetransakt.serviceprovider.vtpass;

public final class VtpassHostPolicy {
    private VtpassHostPolicy() {}

    public static String approvedBaseUrl(String value) {
        String configured = value == null ? "" : value.trim();
        if (configured.equals("https://sandbox.vtpass.com/api")
                || configured.equals("https://sandbox.vtpass.com/api/")) {
            return "https://sandbox.vtpass.com/api/";
        }
        if (configured.equals("https://vtpass.com/api")
                || configured.equals("https://vtpass.com/api/")) {
            return "https://vtpass.com/api/";
        }
        throw new IllegalStateException("Unsupported VTpass API origin");
    }
}
