package com.veritas.common;

import org.springframework.http.HttpStatus;

/** The concrete application errors. Codes are part of the API contract; do not rename them casually. */
public final class Errors {

    private Errors() {
    }

    public static class NotFound extends AppException {
        public NotFound(String what, Object id) {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found: " + id);
        }
    }

    public static class Conflict extends AppException {
        public Conflict(String code, String message) {
            super(HttpStatus.CONFLICT, code, message);
        }
    }

    public static class BadRequest extends AppException {
        public BadRequest(String code, String message) {
            super(HttpStatus.BAD_REQUEST, code, message);
        }
    }

    public static class Unprocessable extends AppException {
        public Unprocessable(String code, String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
        }
    }

    public static class Forbidden extends AppException {
        public Forbidden(String message) {
            super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }
}
