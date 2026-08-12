package com.reconengine.common;

import org.springframework.http.HttpStatus;

/** Base of the typed error hierarchy; carries the HTTP status and a stable machine-readable code. */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
