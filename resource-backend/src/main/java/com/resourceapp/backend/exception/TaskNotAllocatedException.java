package com.resourceapp.backend.exception;

/**
 * Thrown when a resource person tries to add or delete a saved entry for a task that the
 * admin allocated to a different resource person. Viewing is unaffected - this only
 * blocks add/delete.
 */
public class TaskNotAllocatedException extends RuntimeException {
    public TaskNotAllocatedException(String message) {
        super(message);
    }
}
