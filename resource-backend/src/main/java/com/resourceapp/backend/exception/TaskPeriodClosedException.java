package com.resourceapp.backend.exception;

/**
 * Thrown when a resource person tries to add or delete a saved task entry outside the
 * date range the admin allocated for that task (Task.fromDate .. Task.toDate).
 * Viewing existing entries is never affected - only add/delete are blocked.
 */
public class TaskPeriodClosedException extends RuntimeException {
    public TaskPeriodClosedException(String message) {
        super(message);
    }
}
