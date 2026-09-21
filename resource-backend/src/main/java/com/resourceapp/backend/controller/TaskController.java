package com.resourceapp.backend.controller;

import com.resourceapp.backend.entity.Task;
import com.resourceapp.backend.repository.TaskRepository;
import com.resourceapp.backend.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = {"http://localhost:4200"})
public class TaskController {

    private final TaskRepository taskRepository;
    private final AdminAuthService adminAuthService;

    public TaskController(TaskRepository taskRepository, AdminAuthService adminAuthService) {
        this.taskRepository = taskRepository;
        this.adminAuthService = adminAuthService;
    }

    // GET /api/tasks -> open to any logged-in user (admin or normal): populates the task
    // dropdown on the User page and the read-only Task Master list on the Resource page.
    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    // POST /api/tasks -> Task Master "Add Task". Admin-only: the caller must send the
    // logged-in admin's own username/password as headers, re-checked against the one
    // seeded ADMIN account on every call (see AdminAuthService).
    @PostMapping
    public ResponseEntity<?> createTask(@Valid @RequestBody Task task,
                                            @RequestHeader("X-Admin-Username") String adminUsername,
                                            @RequestHeader("X-Admin-Password") String adminPassword) {
        adminAuthService.requireAdmin(adminUsername, adminPassword);
        if (taskRepository.findByTaskCode(task.getTaskCode()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That Task ID is already used by another task. Please use a unique ID."));
        }
        Task saved = taskRepository.save(Objects.requireNonNull(task, "task"));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // PUT /api/tasks/{id} -> Task Master "Edit Task". Admin-only, same as above.
    // Deliberately does NOT touch `completed` - that's only ever changed through the
    // dedicated Mark Complete / Mark Pending button (see PATCH .../completion below), so
    // editing a task's other details can never accidentally reset its completion status.
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id,
                                            @Valid @RequestBody Task updated,
                                            @RequestHeader("X-Admin-Username") String adminUsername,
                                            @RequestHeader("X-Admin-Password") String adminPassword) {
        adminAuthService.requireAdmin(adminUsername, adminPassword);
        var duplicate = taskRepository.findByTaskCode(updated.getTaskCode());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That Task ID is already used by another task. Please use a unique ID."));
        }
        return taskRepository.findById(Objects.requireNonNull(id, "id"))
                .map(existing -> {
                    existing.setTaskName(updated.getTaskName());
                    existing.setTaskCode(updated.getTaskCode());
                    existing.setVehicle(updated.getVehicle());
                    existing.setWeatherRegulations(updated.getWeatherRegulations());
                    existing.setFromDate(updated.getFromDate());
                    existing.setToDate(updated.getToDate());
                    existing.setAssignedResourceId(updated.getAssignedResourceId());
                    existing.setAssignedResourceNameId(updated.getAssignedResourceNameId());
                    existing.setAssignedResourceName(updated.getAssignedResourceName());
                    existing.setDisabledDates(updated.getDisabledDates());
                    return ResponseEntity.ok(taskRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // PATCH /api/tasks/{id}/completion -> the Task Master "Mark Complete" / "Mark Pending"
    // button. Kept separate from the general edit endpoint above so toggling completion
    // never has to go through (or risk overwriting) the rest of a task's details, and vice
    // versa. The resulting Completed/Pending status is what the user's Report Task and
    // Assigned Task pages read to show their own work as completed or still pending.
    @PatchMapping("/{id}/completion")
    public ResponseEntity<?> setCompletion(@PathVariable Long id,
                                            @RequestBody Map<String, Boolean> body,
                                            @RequestHeader("X-Admin-Username") String adminUsername,
                                            @RequestHeader("X-Admin-Password") String adminPassword) {
        adminAuthService.requireAdmin(adminUsername, adminPassword);
        Boolean completed = body.get("completed");
        if (completed == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "completed (true/false) is required."));
        }
        return taskRepository.findById(Objects.requireNonNull(id, "id"))
                .map(existing -> {
                    existing.setCompleted(completed);
                    return ResponseEntity.ok(taskRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/tasks/{id} -> Task Master "Delete Task". Admin-only, same as above.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id,
                                            @RequestHeader("X-Admin-Username") String adminUsername,
                                            @RequestHeader("X-Admin-Password") String adminPassword) {
        adminAuthService.requireAdmin(adminUsername, adminPassword);
        Long requiredId = Objects.requireNonNull(id, "id");
        if (!taskRepository.existsById(requiredId)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(requiredId);
        return ResponseEntity.noContent().build();
    }
}
