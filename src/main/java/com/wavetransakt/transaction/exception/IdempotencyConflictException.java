package com.wavetransakt.transaction.exception;

public class IdempotencyConflictException
        extends RuntimeException {

    public IdempotencyConflictException(
            String message
    ) {
        super(message);
    }
}