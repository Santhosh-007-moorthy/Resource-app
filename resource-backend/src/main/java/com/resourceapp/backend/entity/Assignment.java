package com.resourceapp.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Plain data holder for one saved assignment row. This is intentionally NOT a JPA
 * @Entity: each resource person's assignments live in that person's own physical
 * MySQL database (see PersonDatabaseService / AssignmentJdbcService), so there is no
 * single shared ASSIGNMENTS table for Hibernate to manage anymore.
 */
public class Assignment {

    private Long id;

    // Which resource this row belongs to. Set by the server from the URL path,
    // not persisted as a column (the database itself is what scopes it to one person).
    private Long resourceId;

    private String resourceNameId; // resource's Name ID, filled in from the Resource lookup
    private String resourceName;   // resource's Name, filled in from the Resource lookup

    @NotBlank(message = "Task is required")
    private String taskName;

    @NotBlank(message = "Task code is required")
    private String taskCode;

    private String date;

    @NotBlank(message = "Month is required")
    private String month;

    @NotNull(message = "Year is required")
    private Integer year;

    private Integer hours;

    private String remarks;

    public Assignment() {
    }

    public Assignment(Long resourceId, String resourceName, String taskName, String taskCode, String month, Integer year) {
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.taskName = taskName;
        this.taskCode = taskCode;
        this.month = month;
        this.year = year;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceNameId() {
        return resourceNameId;
    }

    public void setResourceNameId(String resourceNameId) {
        this.resourceNameId = resourceNameId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getHours() {
        return hours;
    }

    public void setHours(Integer hours) {
        this.hours = hours;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
