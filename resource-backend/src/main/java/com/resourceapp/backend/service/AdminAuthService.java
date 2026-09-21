package com.resourceapp.backend.service;

import com.resourceapp.backend.controller.AuthController;
import com.resourceapp.backend.entity.AppUser;
import com.resourceapp.backend.exception.NotAdminException;
import com.resourceapp.backend.repository.AppUserRepository;
import com.resourceapp.backend.util.PasswordUtil;
import org.springframework.stereotype.Service;

/**
 * Re-checks admin credentials on the server for every admin-only write, instead of trusting
 * the Angular app to simply hide the buttons. The frontend sends the logged-in admin's
 * username/password as request headers (X-Admin-Username / X-Admin-Password) on every
 * Task Master create/update/delete call; this service confirms those actually belong to
 * the one seeded ADMIN account before the write is allowed to proceed.
 */
@Service
public class AdminAuthService {

    private final AppUserRepository appUserRepository;

    public AdminAuthService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public void requireAdmin(String username, String password) {
        if (username == null || password == null) {
            throw new NotAdminException("Admin credentials are required for this action.");
        }
        AppUser user = appUserRepository.findByUsername(username.trim()).orElse(null);
        if (user == null
                || !AuthController.ROLE_ADMIN.equals(user.getRole())
                || !PasswordUtil.matches(password, user.getPasswordHash())) {
            throw new NotAdminException("Only the admin account can modify the Task Master.");
        }
    }
}
