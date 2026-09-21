package com.resourceapp.backend.service;

import com.resourceapp.backend.entity.Assignment;
import com.resourceapp.backend.entity.Resource;
import com.resourceapp.backend.entity.Task;
import com.resourceapp.backend.exception.EntryTooOldException;
import com.resourceapp.backend.exception.TaskNotAllocatedException;
import com.resourceapp.backend.exception.TaskPeriodClosedException;
import com.resourceapp.backend.repository.ResourceRepository;
import com.resourceapp.backend.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Reads and writes Assignment rows directly through JDBC, always against the one
 * resource person's own database - never a table shared across everyone.
 */
@Service
public class AssignmentJdbcService {

    private final ResourceRepository resourceRepository;
    private final PersonDatabaseService personDatabaseService;
    private final TaskRepository taskRepository;

    public AssignmentJdbcService(ResourceRepository resourceRepository, PersonDatabaseService personDatabaseService,
                                  TaskRepository taskRepository) {
        this.resourceRepository = resourceRepository;
        this.personDatabaseService = personDatabaseService;
        this.taskRepository = taskRepository;
    }

    // Add/delete are only allowed while (a) today falls within the task's admin-allocated
    // [fromDate, toDate] window, and (b) the resource person doing the add/delete is the
    // one the admin actually allocated this task to. If the task can't be found at all
    // (shouldn't normally happen since the dropdown is built from the same Task Master),
    // we fail safe and block the write rather than silently allowing an unrestricted task.
    // Returns the Task so callers (e.g. save()) can run further checks against it without
    // a second lookup.
    private Task requireWithinAllocatedPeriod(String taskCode, Long resourceId, String action) {
        Task task = taskRepository.findByTaskCode(taskCode).orElse(null);
        if (task == null) {
            throw new TaskPeriodClosedException(
                    "This task is no longer in the Task Master, so it can't be " + action + ".");
        }
        if (task.getAssignedResourceId() != null && !task.getAssignedResourceId().equals(resourceId)) {
            throw new TaskNotAllocatedException(
                    "This task is allocated to a different resource person, so it can't be " + action + " here.");
        }
        LocalDate today = LocalDate.now();
        if (task.getFromDate() != null && today.isBefore(task.getFromDate())) {
            throw new TaskPeriodClosedException(
                    "This task's allocated period hasn't started yet, so entries can't be " + action + " until "
                            + task.getFromDate() + ".");
        }
        if (task.getToDate() != null && today.isAfter(task.getToDate())) {
            throw new TaskPeriodClosedException(
                    "This task's allocated period ended on " + task.getToDate()
                            + ", so entries can no longer be " + action + ".");
        }
        return task;
    }

    // On top of the checks above, an add must log a date that actually falls inside the
    // task's allocated window - the admin's from/to range isn't just "when you can click
    // save", it's the only period the entry itself is allowed to be dated within.
    private void requireEntryDateWithinRange(Task task, String entryDateText) {
        if (entryDateText == null || entryDateText.isBlank()) {
            return; // Assignment's own @NotBlank validation handles a missing date
        }
        LocalDate entryDate;
        try {
            entryDate = LocalDate.parse(entryDateText);
        } catch (Exception e) {
            return; // malformed date - let normal binding/validation surface that separately
        }
        if (task.getFromDate() != null && entryDate.isBefore(task.getFromDate())) {
            throw new TaskPeriodClosedException(
                    "The entry date must be on or after " + task.getFromDate() + " for this task.");
        }
        if (task.getToDate() != null && entryDate.isAfter(task.getToDate())) {
            throw new TaskPeriodClosedException(
                    "The entry date must be on or before " + task.getToDate() + " for this task.");
        }
        if (task.isDisabledDate(entryDate)) {
            throw new TaskPeriodClosedException(
                    entryDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                            ? "Sundays are not reportable for this task."
                            : "The admin has disabled " + entryDate + " for this task, so it can't be reported on.");
        }
    }

    private Resource requireResource(Long resourceId) {
        Long requiredResourceId = Objects.requireNonNull(resourceId, "resourceId");
        Resource resource = resourceRepository.findById(requiredResourceId)
                .orElseThrow(() -> new NoSuchElementException("No resource with id " + resourceId));

        if (resource.getDbName() == null || resource.getDbName().isBlank()) {
            resource.setDbName(personDatabaseService.buildDbNameFromNameId(resource.getNameId()));
            resource = resourceRepository.save(resource);
        }

        // Self-healing: CREATE DATABASE/TABLE IF NOT EXISTS is cheap and safe to repeat, so we
        // just make sure this resource's database and ASSIGNMENTS table are really there before
        // every read/write. This also fixes resources whose one-time provisioning at creation
        // time failed partway through, without needing any manual SQL.
        personDatabaseService.provisionDatabaseFor(resource.getDbName());
        return resource;
    }

    public List<Assignment> findAllForResource(Long resourceId) {
        Resource resource = requireResource(resourceId);
        List<Assignment> results = new ArrayList<>();

        String sql = "SELECT ID, TASK_NAME, TASK_CODE, ASSIGNMENT_DATE, MONTH, YEAR, HOURS, REMARKS " +
                "FROM ASSIGNMENTS ORDER BY ID";

        try (Connection conn = personDatabaseService.getConnectionFor(resource.getDbName());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs, resource));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read assignments for resource " + resourceId, e);
        }

        return results;
    }

    public Assignment save(Long resourceId, Assignment assignment) {
        Resource resource = requireResource(resourceId);
        Task task = requireWithinAllocatedPeriod(assignment.getTaskCode(), resourceId, "added");
        requireEntryDateWithinRange(task, assignment.getDate());

        String sql = "INSERT INTO ASSIGNMENTS (TASK_NAME, TASK_CODE, ASSIGNMENT_DATE, MONTH, YEAR, HOURS, REMARKS) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = personDatabaseService.getConnectionFor(resource.getDbName());
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, assignment.getTaskName());
            ps.setString(2, assignment.getTaskCode());
            ps.setString(3, assignment.getDate());
            ps.setString(4, assignment.getMonth());
            ps.setInt(5, assignment.getYear());
            if (assignment.getHours() != null) {
                ps.setInt(6, assignment.getHours());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setString(7, assignment.getRemarks());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    assignment.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save assignment for resource " + resourceId, e);
        }

        assignment.setResourceId(resourceId);
        assignment.setResourceNameId(resource.getNameId());
        assignment.setResourceName(resource.getName());
        return assignment;
    }

    /** Returns true if a row was actually deleted. */
    public boolean delete(Long resourceId, Long assignmentId) {
        Resource resource = requireResource(resourceId);

        // Need this row's task code (and its own date) first, to check whether that
        // task's allocated period still covers today, and whether the entry itself is
        // recent enough to delete, before letting the delete through.
        String taskCode = null;
        String entryDate = null;
        try (Connection conn = personDatabaseService.getConnectionFor(resource.getDbName());
             PreparedStatement ps = conn.prepareStatement("SELECT TASK_CODE, ASSIGNMENT_DATE FROM ASSIGNMENTS WHERE ID = ?")) {
            ps.setLong(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    taskCode = rs.getString("TASK_CODE");
                    entryDate = rs.getString("ASSIGNMENT_DATE");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up assignment " + assignmentId + " for resource " + resourceId, e);
        }

        if (taskCode == null) {
            return false; // row doesn't exist - nothing to delete, nothing to check
        }
        requireWithinAllocatedPeriod(taskCode, resourceId, "deleted");
        requireWithinDeletableWindow(entryDate);

        try (Connection conn = personDatabaseService.getConnectionFor(resource.getDbName());
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ASSIGNMENTS WHERE ID = ?")) {
            ps.setLong(1, assignmentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete assignment " + assignmentId + " for resource " + resourceId, e);
        }
    }

    // Deleting is only allowed for entries dated in the current calendar month or the
    // one before it - older entries can still be viewed but never removed.
    private void requireWithinDeletableWindow(String entryDateText) {
        if (entryDateText == null || entryDateText.isBlank()) {
            return; // no date on the row - nothing to check against
        }
        LocalDate entryDate;
        try {
            entryDate = LocalDate.parse(entryDateText);
        } catch (Exception e) {
            return; // malformed date - don't block on something we can't parse
        }
        LocalDate today = LocalDate.now();
        LocalDate previousMonthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate nextMonthStart = today.plusMonths(1).withDayOfMonth(1);
        if (entryDate.isBefore(previousMonthStart) || !entryDate.isBefore(nextMonthStart)) {
            throw new EntryTooOldException("Only entries from the current month or the previous month can be deleted.");
        }
    }

    private Assignment mapRow(ResultSet rs, Resource resource) throws SQLException {
        Assignment a = new Assignment();
        a.setId(rs.getLong("ID"));
        a.setResourceId(resource.getId());
        a.setResourceNameId(resource.getNameId());
        a.setResourceName(resource.getName());
        a.setTaskName(rs.getString("TASK_NAME"));
        a.setTaskCode(rs.getString("TASK_CODE"));
        a.setDate(rs.getString("ASSIGNMENT_DATE"));
        a.setMonth(rs.getString("MONTH"));
        a.setYear(rs.getInt("YEAR"));
        int hours = rs.getInt("HOURS");
        a.setHours(rs.wasNull() ? null : hours);
        a.setRemarks(rs.getString("REMARKS"));
        return a;
    }
}
