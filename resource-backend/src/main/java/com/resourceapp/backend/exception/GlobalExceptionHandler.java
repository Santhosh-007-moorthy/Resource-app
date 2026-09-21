package com.resourceapp.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Turns @Valid failures (e.g. blank name/role) into a clean JSON error body
    // instead of a raw Spring stack trace.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // A Task Master write missing its X-Admin-Username/X-Admin-Password headers - treated
    // the same as any other "you're not allowed to do this" case.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> handleMissingAdminHeaders(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Only the admin account can perform this action."));
    }

    // Someone without valid admin credentials tried to modify the Task Master.
    @ExceptionHandler(NotAdminException.class)
    public ResponseEntity<Map<String, String>> handleNotAdmin(NotAdminException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    // An add/delete on a saved task entry outside the task's admin-allocated date range.
    @ExceptionHandler(TaskPeriodClosedException.class)
    public ResponseEntity<Map<String, String>> handleTaskPeriodClosed(TaskPeriodClosedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    // An add/delete on a task allocated to a different resource person.
    @ExceptionHandler(TaskNotAllocatedException.class)
    public ResponseEntity<Map<String, String>> handleTaskNotAllocated(TaskNotAllocatedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    // A delete attempted on an entry older than the current/previous-month window.
    @ExceptionHandler(EntryTooOldException.class)
    public ResponseEntity<Map<String, String>> handleEntryTooOld(EntryTooOldException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }
}
