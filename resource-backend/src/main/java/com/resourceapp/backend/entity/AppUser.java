package com.resourceapp.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "APP_USERS")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @NotBlank(message = "Username is required")
    @Column(name = "USERNAME", nullable = false, length = 100, unique = true)
    private String username; // no format restrictions - any non-blank value is accepted

    @NotBlank(message = "Password is required")
    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    // "ADMIN" or "USER". USER accounts are now created automatically, one per Resource,
    // the moment an admin adds that resource (see ResourceController); ADMIN is seeded
    // once at startup (see DataSeeder) so nobody can create their own way into the admin account.
    @Column(name = "ROLE", nullable = false, length = 20)
    private String role;

    // Which Resource this login belongs to (null for the ADMIN account). This is what lets
    // the sidebar greet a resource user by name and the Assigned Task / Report Task pages
    // resolve "who am I" straight from the logged-in session, with no manual resource picker.
    @Column(name = "RESOURCE_ID")
    private Long resourceId;

    public AppUser() {
    }

    public AppUser(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public AppUser(String username, String passwordHash, String role, Long resourceId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.resourceId = resourceId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }
}
