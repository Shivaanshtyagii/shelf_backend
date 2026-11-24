package com.shelf.api.common.error;

import org.springframework.http.HttpStatus;

public final class DomainException extends RuntimeException {
    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }

    public static DomainException badRequest(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, message);
    }
    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, message);
    }
    public static DomainException missing(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, message);
    }
    public static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, message);
    }
    public static DomainException unavailable(String message) {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
