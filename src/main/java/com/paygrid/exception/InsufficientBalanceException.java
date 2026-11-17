package com.paygrid.exception;

public class InsufficientBalanceException extends PayGridException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

