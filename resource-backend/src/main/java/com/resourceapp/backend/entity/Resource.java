package com.resourceapp.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "RESOURCES")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @NotBlank(message = "Name ID is required")
    @Column(name = "NAME_ID", nullable = false, length = 50, unique = true)
    private String nameId; // business-facing unique ID for this resource person (e.g. "R1001")

    @Column(name = "DB_NAME", length = 80)
    private String dbName; // name of the physical MySQL database created just for this resource's assignments

    @NotBlank(message = "Name is required")
    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Role is required")
    @Column(name = "ROLE", nullable = false, length = 100)
    private String role;

    public Resource() {
    }

    public Resource(String nameId, String name, String role) {
        this.nameId = nameId;
        this.name = name;
        this.role = role;
    }

    public String getNameId() {
        return nameId;
    }

    public void setNameId(String nameId) {
        this.nameId = nameId;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
