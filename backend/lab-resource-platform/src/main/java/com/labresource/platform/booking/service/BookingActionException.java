package com.labresource.platform.booking.service;

import org.springframework.http.HttpStatus;

public class BookingActionException extends RuntimeException {
    private final HttpStatus status;

    public BookingActionException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
