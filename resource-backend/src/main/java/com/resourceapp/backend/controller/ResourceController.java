package com.resourceapp.backend.controller;

import com.resourceapp.backend.entity.AppUser;
import com.resourceapp.backend.entity.Resource;
import com.resourceapp.backend.repository.AppUserRepository;
import com.resourceapp.backend.repository.ResourceRepository;
import com.resourceapp.backend.service.AdminAuthService;
import com.resourceapp.backend.service.PersonDatabaseService;
import com.resourceapp.backend.util.PasswordUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/resources")
@CrossOrigin(origins = {"http://localhost:4200"}) // Angular dev server
public class ResourceController {

    private final ResourceRepository resourceRepository;
    private final PersonDatabaseService personDatabaseService;
    private final AppUserRepository appUserRepository;
    private final AdminAuthService adminAuthService;

    // The one shared login password given to every resource person's auto-created account.
    // Override via app.resource.user.password in application.properties before real use.
    @Value("${app.resource.user.password:Resource@123}")
    private String resourceUserPassword;

    public ResourceController(ResourceRepository resourceRepository,
                               PersonDatabaseService personDatabaseService,
                               AppUserRepository appUserRepository,
                               AdminAuthService adminAuthService) {
        this.resourceRepository = resourceRepository;
        this.personDatabaseService = personDatabaseService;
        this.appUserRepository = appUserRepository;
        this.adminAuthService = adminAuthService;
    }

    // GET /api/resources/shared-login-password -> admin-only. Lets the Resource page show
    // the admin the *exact* current password (right down to case and punctuation) to hand
    // out to resource people, instead of the frontend guessing/hardcoding it separately
    // from whatever is actually configured in application.properties on the server.
    @GetMapping("/shared-login-password")
    public ResponseEntity<?> getSharedLoginPassword(@RequestHeader("X-Admin-Username") String adminUsername,
                                                     @RequestHeader("X-Admin-Password") String adminPassword) {
        adminAuthService.requireAdmin(adminUsername, adminPassword);
        return ResponseEntity.ok(Map.of("password", resourceUserPassword));
    }

    // GET /api/resources -> list everything, used to populate the resource table on page load
    @GetMapping
    public List<Resource> getAllResources() {
        return resourceRepository.findAll();
    }

    // GET /api/resources/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Resource> getResourceById(@PathVariable Long id) {
        return resourceRepository.findById(Objects.requireNonNull(id, "id"))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/resources/by-name-id/{nameId} -> look a resource up by its business-facing Name ID
    @GetMapping("/by-name-id/{nameId}")
    public ResponseEntity<Resource> getResourceByNameId(@PathVariable String nameId) {
        return resourceRepository.findByNameId(nameId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/resources -> called from the "Add Resource" popup's Save button.
    // Also provisions a brand-new physical database for this person's assignments, and
    // auto-creates their login account: username = the Name ID the admin just entered,
    // password = the one shared resource-user password (see resourceUserPassword above).
    @PostMapping
    public ResponseEntity<Resource> createResource(@Valid @RequestBody Resource resource) {
        if (resourceRepository.existsByNameId(resource.getNameId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        // The resource's Name ID becomes this person's login username, so it also has to be
        // free as a username - not just unique among resources - or login would be ambiguous.
        if (appUserRepository.existsByUsername(resource.getNameId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String dbName = personDatabaseService.buildDbNameFromNameId(resource.getNameId());
        resource.setDbName(dbName);

        Resource saved = resourceRepository.save(resource);

        try {
            personDatabaseService.provisionDatabaseFor(dbName);
        } catch (RuntimeException e) {
            // Don't leave a resource behind that points at a database/table that was never
            // actually created - roll it back so the user can just try adding it again.
            resourceRepository.deleteById(Objects.requireNonNull(saved.getId(), "saved.id"));
            throw new RuntimeException("Resource was not saved: could not set up its database.", e);
        }

        AppUser loginAccount = new AppUser(
                saved.getNameId(),
                PasswordUtil.hash(resourceUserPassword),
                AuthController.ROLE_USER,
                saved.getId());
        appUserRepository.save(loginAccount);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // PUT /api/resources/{id} -> optional, for editing an existing resource
    @PutMapping("/{id}")
    public ResponseEntity<Resource> updateResource(@PathVariable Long id, @Valid @RequestBody Resource updated) {
        return resourceRepository.findById(Objects.requireNonNull(id, "id"))
                .map(existing -> {
                    boolean nameIdChanged = !existing.getNameId().equals(updated.getNameId());
                    // If the Name ID changed, make sure it doesn't collide with another resource
                    // or with someone else's login username.
                    if (nameIdChanged && (resourceRepository.existsByNameId(updated.getNameId())
                            || appUserRepository.existsByUsername(updated.getNameId()))) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).<Resource>build();
                    }
                    // dbName is intentionally left as-is: renaming the Name ID does NOT move
                    // this person's existing assignment data to a new physical database.
                    existing.setNameId(updated.getNameId());
                    existing.setName(updated.getName());
                    existing.setRole(updated.getRole());
                    Resource saved = resourceRepository.save(existing);

                    // Keep this resource's login username (their Name ID) in sync so they can
                    // still log in after the admin renames their ID.
                    if (nameIdChanged) {
                        appUserRepository.findByResourceId(saved.getId()).ifPresent(account -> {
                            account.setUsername(saved.getNameId());
                            appUserRepository.save(account);
                        });
                    }
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/resources/{id} -> optional, for removing a resource. Also removes that
    // resource person's login account, so a deleted resource can no longer sign in.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable Long id) {
        Long requiredId = Objects.requireNonNull(id, "id");
        if (!resourceRepository.existsById(requiredId)) {
            return ResponseEntity.notFound().build();
        }
        appUserRepository.findByResourceId(requiredId).ifPresent(appUserRepository::delete);
        resourceRepository.deleteById(requiredId);
        return ResponseEntity.noContent().build();
    }
}
