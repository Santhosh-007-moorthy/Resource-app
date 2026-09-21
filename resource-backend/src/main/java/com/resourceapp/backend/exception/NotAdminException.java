package com.resourceapp.backend.exception;

/** Thrown when a request tries to modify admin-only data without valid admin credentials. */
public class NotAdminException extends RuntimeException {
    public NotAdminException(String message) {
        super(message);
    }
}
