package com.auth.service.exception;

public class VerificationTokenException extends RuntimeException {
    public VerificationTokenException(String message) {
        super(message);
    }
}
