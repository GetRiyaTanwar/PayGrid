package com.paygrid.exception;

public class UserAlreadyExistsException extends PayGridException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

