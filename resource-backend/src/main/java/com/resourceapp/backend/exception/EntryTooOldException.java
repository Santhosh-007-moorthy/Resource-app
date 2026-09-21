package com.resourceapp.backend.exception;

/**
 * Thrown when a resource person tries to delete a saved entry dated further back than
 * the current or previous calendar month. Viewing older entries is unaffected - only
 * deleting them is blocked.
 */
public class EntryTooOldException extends RuntimeException {
    public EntryTooOldException(String message) {
        super(message);
    }
}
