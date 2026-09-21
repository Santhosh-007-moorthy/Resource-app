package com.resourceapp.backend.controller;

import com.resourceapp.backend.dto.AuthDtos.AuthRequest;
import com.resourceapp.backend.dto.AuthDtos.AuthResponse;
import com.resourceapp.backend.dto.AuthDtos.ErrorResponse;
import com.resourceapp.backend.entity.AppUser;
import com.resourceapp.backend.entity.Resource;
import com.resourceapp.backend.repository.AppUserRepository;
import com.resourceapp.backend.repository.ResourceRepository;
import com.resourceapp.backend.util.PasswordUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:4200"})
public class AuthController {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private final AppUserRepository appUserRepository;
    private final ResourceRepository resourceRepository;

    public AuthController(AppUserRepository appUserRepository, ResourceRepository resourceRepository) {
        this.appUserRepository = appUserRepository;
        this.resourceRepository = resourceRepository;
    }

    // POST /api/auth/login -> works for both the seeded admin account and every resource
    // person's account. There is no public self-registration anymore: a USER account is
    // created automatically the moment an admin adds a Resource (see ResourceController),
    // with the resource's Name ID as the username and one shared password for everyone.
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null || !PasswordUtil.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password."));
        }

        Long resourceId = user.getResourceId();
        if (resourceId != null) {
            Resource resource = resourceRepository.findById(java.util.Objects.requireNonNull(resourceId)).orElse(null);
            if (resource != null) {
                return ResponseEntity.ok(new AuthResponse(
                        user.getUsername(), user.getRole(),
                        resource.getId(), resource.getNameId(), resource.getName()));
            }
        }
        return ResponseEntity.ok(new AuthResponse(user.getUsername(), user.getRole()));
    }
}
