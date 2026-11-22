package com.shelf.common;

import org.springframework.http.HttpStatus;

public final class ApiFault extends RuntimeException {
    private final HttpStatus status;

    public ApiFault(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }

    public static ApiFault badRequest(String message) { return new ApiFault(HttpStatus.BAD_REQUEST, message); }
    public static ApiFault forbidden(String message) { return new ApiFault(HttpStatus.FORBIDDEN, message); }
    public static ApiFault missing(String message) { return new ApiFault(HttpStatus.NOT_FOUND, message); }
    public static ApiFault conflict(String message) { return new ApiFault(HttpStatus.CONFLICT, message); }
    public static ApiFault unavailable(String message) { return new ApiFault(HttpStatus.SERVICE_UNAVAILABLE, message); }
}
