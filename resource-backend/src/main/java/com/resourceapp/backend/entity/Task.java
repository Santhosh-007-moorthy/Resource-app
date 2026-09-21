package com.resourceapp.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "TASKS")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @NotBlank(message = "Task name is required")
    @Column(name = "TASK_NAME", nullable = false, length = 150)
    private String taskName;

    @NotBlank(message = "Task ID is required")
    @Column(name = "TASK_CODE", nullable = false, length = 50, unique = true)
    private String taskCode; // the business-facing "Task ID" the user types in, distinct from the DB id

    @Column(name = "VEHICLE", length = 150)
    private String vehicle;

    @Column(name = "WEATHER_REGULATIONS", length = 255)
    private String weatherRegulations;

    // The window during which resource persons are allowed to add/delete saved entries
    // for this task (see AssignmentJdbcService). Viewing existing entries is never blocked.
    @NotNull(message = "From date is required")
    @Column(name = "FROM_DATE", nullable = false)
    private LocalDate fromDate;

    @NotNull(message = "To date is required")
    @Column(name = "TO_DATE", nullable = false)
    private LocalDate toDate;

    // The one resource person this task is allocated to. Stored denormalized (id + the
    // display fields) the same way Assignment stores its resource info, rather than a
    // JPA relation, since each resource person's own data lives in a separate database.
    @NotNull(message = "A resource person must be assigned to this task")
    @Column(name = "ASSIGNED_RESOURCE_ID", nullable = false)
    private Long assignedResourceId;

    @Column(name = "ASSIGNED_RESOURCE_NAME_ID", length = 50)
    private String assignedResourceNameId;

    @Column(name = "ASSIGNED_RESOURCE_NAME", length = 150)
    private String assignedResourceName;

    // Set by the admin from the Task Master page (a dedicated Mark Complete / Mark Pending
    // button, not the edit form) once the actual work is done. This is independent of
    // withinAllocatedPeriod below: a task can be marked Completed before its date window
    // closes, or still be Pending after it closes - they answer different questions
    // ("is today inside the allocated window" vs "is the work actually finished").
    @Column(name = "COMPLETED", nullable = false)
    private boolean completed = false;

    // Comma-separated ISO dates (yyyy-MM-dd) the admin has manually blocked out for this
    // task on top of the always-blocked Sundays (see isDisabledDate below) - e.g. public
    // holidays or days the resource person is known to be unavailable. Exposed to JSON as
    // a String list via the getter/setter pair below, the same way withinAllocatedPeriod
    // is exposed as a derived boolean.
    @Column(name = "DISABLED_DATES", length = 2000)
    private String disabledDatesCsv;

    public java.util.List<String> getDisabledDates() {
        if (disabledDatesCsv == null || disabledDatesCsv.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(disabledDatesCsv.split(","))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    public void setDisabledDates(java.util.List<String> disabledDates) {
        this.disabledDatesCsv = (disabledDates == null || disabledDates.isEmpty())
                ? null
                : String.join(",", disabledDates);
    }

    public Task() {
    }

    public Task(String taskName, String taskCode) {
        this.taskName = taskName;
        this.taskCode = taskCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getWeatherRegulations() {
        return weatherRegulations;
    }

    public void setWeatherRegulations(String weatherRegulations) {
        this.weatherRegulations = weatherRegulations;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Long getAssignedResourceId() {
        return assignedResourceId;
    }

    public void setAssignedResourceId(Long assignedResourceId) {
        this.assignedResourceId = assignedResourceId;
    }

    public String getAssignedResourceNameId() {
        return assignedResourceNameId;
    }

    public void setAssignedResourceNameId(String assignedResourceNameId) {
        this.assignedResourceNameId = assignedResourceNameId;
    }

    public String getAssignedResourceName() {
        return assignedResourceName;
    }

    public void setAssignedResourceName(String assignedResourceName) {
        this.assignedResourceName = assignedResourceName;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /** True when today falls within [fromDate, toDate] inclusive - the window admins allocated for this task. */
    @Transient
    public boolean isWithinAllocatedPeriod() {
        LocalDate today = LocalDate.now();
        return fromDate != null && toDate != null
                && !today.isBefore(fromDate)
                && !today.isAfter(toDate);
    }

    /**
     * True when {@code date} can't be reported against - either it's a Sunday (blocked by
     * default for every task, not stored) or the admin manually added it to
     * {@link #getDisabledDates()}. Used server-side by AssignmentJdbcService so the "no
     * reporting on disabled dates" rule is enforced even if a request bypasses the UI.
     */
    public boolean isDisabledDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return true;
        }
        return getDisabledDates().contains(date.toString());
    }
}
