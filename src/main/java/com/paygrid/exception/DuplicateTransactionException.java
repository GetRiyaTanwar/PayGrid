package com.paygrid.exception;

public class DuplicateTransactionException extends PayGridException {
    public DuplicateTransactionException(String message) {
        super(message);
    }
}

