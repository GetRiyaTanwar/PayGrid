package com.paygrid.exception;

public class PayGridException extends RuntimeException {
    public PayGridException(String message) {
        super(message);
    }

    public PayGridException(String message, Throwable cause) {
        super(message, cause);
    }
}

