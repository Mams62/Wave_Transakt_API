package com.wavetransakt.wallet.wema;

/**
 * Safe wrapper for failures returned by, or while connecting to, the Wema API.
 * No credentials or raw upstream payloads are exposed through this exception.
 */
public class WemaProviderException extends RuntimeException {

    private final String errorCode;
    private final Integer upstreamStatus;

    public WemaProviderException(
            String errorCode,
            String message,
            Integer upstreamStatus
    ) {
        super(message);
        this.errorCode = errorCode;
        this.upstreamStatus = upstreamStatus;
    }

    public WemaProviderException(
            String errorCode,
            String message,
            Integer upstreamStatus,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.upstreamStatus = upstreamStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }
}
