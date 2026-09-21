package com.resourceapp.backend.controller;

import com.resourceapp.backend.entity.Assignment;
import com.resourceapp.backend.service.AssignmentJdbcService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/assignments")
@CrossOrigin(origins = {"http://localhost:4200"})
public class AssignmentController {

    private final AssignmentJdbcService assignmentJdbcService;

    public AssignmentController(AssignmentJdbcService assignmentJdbcService) {
        this.assignmentJdbcService = assignmentJdbcService;
    }

    // GET /api/assignments/by-resource/{resourceId} -> only this resource person's saved
    // tasks, read straight out of their own database. This is what the User page uses so
    // one person's data never shows while another person is selected.
    @GetMapping("/by-resource/{resourceId}")
    public ResponseEntity<List<Assignment>> getAssignmentsByResource(@PathVariable Long resourceId) {
        try {
            return ResponseEntity.ok(assignmentJdbcService.findAllForResource(resourceId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // POST /api/assignments/{resourceId} -> saves into that resource's own database
    @PostMapping("/{resourceId}")
    public ResponseEntity<Assignment> createAssignment(@PathVariable Long resourceId,
                                                         @Valid @RequestBody Assignment assignment) {
        try {
            Assignment saved = assignmentJdbcService.save(resourceId, assignment);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE /api/assignments/{resourceId}/{id} -> the resource ID is required because
    // each person's assignment IDs live in a separate database and are not globally unique.
    @DeleteMapping("/{resourceId}/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long resourceId, @PathVariable Long id) {
        try {
            boolean deleted = assignmentJdbcService.delete(resourceId, id);
            return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
