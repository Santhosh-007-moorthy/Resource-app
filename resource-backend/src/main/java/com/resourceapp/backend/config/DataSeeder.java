package com.resourceapp.backend.config;

import com.resourceapp.backend.controller.AuthController;
import com.resourceapp.backend.entity.AppUser;
import com.resourceapp.backend.entity.Resource;
import com.resourceapp.backend.repository.AppUserRepository;
import com.resourceapp.backend.repository.ResourceRepository;
import com.resourceapp.backend.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the single admin account the first time the app starts against a fresh database,
 * so there is always exactly one ADMIN row and it is never created through the public
 * registration endpoint. Change the default username/password below (or override via the
 * app.admin.username / app.admin.password properties) before handing this app to real users.
 */
@Configuration
public class DataSeeder {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    // The one shared login password every resource person's account should have. Kept in
    // sync with the same property ResourceController reads when creating a brand-new
    // resource, so both places always agree on the current password.
    @Value("${app.resource.user.password:Resource@123}")
    private String resourceUserPassword;

    @Bean
    CommandLineRunner seedAdminUser(AppUserRepository appUserRepository) {
        return args -> {
            boolean adminAlreadyExists = appUserRepository.findByRole(AuthController.ROLE_ADMIN).isPresent();
            if (!adminAlreadyExists) {
                AppUser admin = new AppUser(adminUsername, PasswordUtil.hash(adminPassword), AuthController.ROLE_ADMIN);
                appUserRepository.save(admin);
                System.out.println("Seeded default admin account -> username: " + adminUsername
                        + " (change app.admin.username / app.admin.password in application.properties)");
            }
        };
    }

    // Repairs resource-person login accounts on every startup. This matters most for
    // resources that already existed before login accounts were auto-created on the
    // Resource page (an upgrade from an older version of this app): without this, those
    // resources would have no properly-linked AppUser row, and login would fail for them
    // with "Invalid username or password" no matter what was typed. On every boot we:
    //   1) create the missing account for any Resource that doesn't have one yet,
    //   2) ADOPT an existing account whose username already matches this resource's Name ID
    //      but isn't (or isn't correctly) linked via resourceId - this is exactly what
    //      happens to old data that predates the resourceId column: the account row is
    //      there, but nothing ties it to this resource, so it never gets fixed just by
    //      looking it up via findByResourceId. We link it, mark it USER, and reset its
    //      password - unless that username actually belongs to the ADMIN account or to a
    //      different resource that still exists, in which case we log it and skip rather
    //      than silently overwriting a genuine conflict.
    //   3) for an already-linked account: fix the username if it's drifted out of sync
    //      with the resource's current Name ID, and reset the password hash to match the
    //      currently configured shared password, so changing app.resource.user.password
    //      and restarting actually takes effect for every resource person - old data
    //      included - not just newly-created ones.
    @Bean
    CommandLineRunner repairResourceUserAccounts(ResourceRepository resourceRepository, AppUserRepository appUserRepository) {
        return args -> {
            String expectedPasswordHash = PasswordUtil.hash(resourceUserPassword);
            int created = 0;
            int adopted = 0;
            int repaired = 0;
            int skipped = 0;

            for (Resource resource : resourceRepository.findAll()) {
                AppUser account = appUserRepository.findByResourceId(resource.getId()).orElse(null);

                if (account == null) {
                    AppUser existingByUsername = appUserRepository.findByUsername(resource.getNameId()).orElse(null);

                    if (existingByUsername != null) {
                        if (AuthController.ROLE_ADMIN.equals(existingByUsername.getRole())) {
                            System.out.println("Skipped resource '" + resource.getNameId()
                                    + "': that username belongs to the ADMIN account, not a resource login."
                                    + " Rename this resource's Name ID (or the admin account) to resolve.");
                            skipped++;
                            continue;
                        }
                        Long linkedResourceId = existingByUsername.getResourceId();
                        boolean linkedToDifferentLiveResource = linkedResourceId != null
                                && !linkedResourceId.equals(resource.getId())
                                && resourceRepository.existsById(java.util.Objects.requireNonNull(linkedResourceId));
                        if (linkedToDifferentLiveResource) {
                            System.out.println("Skipped resource '" + resource.getNameId()
                                    + "': that username is already linked to a different existing resource."
                                    + " Please resolve manually.");
                            skipped++;
                            continue;
                        }

                        // Old data with no (or a stale) resourceId link - adopt it rather than
                        // leaving it permanently broken. This is the common case for accounts
                        // that existed before the resourceId column was introduced.
                        existingByUsername.setRole(AuthController.ROLE_USER);
                        existingByUsername.setResourceId(resource.getId());
                        existingByUsername.setPasswordHash(expectedPasswordHash);
                        appUserRepository.save(existingByUsername);
                        adopted++;
                        continue;
                    }

                    account = new AppUser(resource.getNameId(), expectedPasswordHash,
                            AuthController.ROLE_USER, resource.getId());
                    appUserRepository.save(account);
                    created++;
                    continue;
                }

                boolean needsSave = false;
                if (!resource.getNameId().equals(account.getUsername())) {
                    account.setUsername(resource.getNameId());
                    needsSave = true;
                }
                if (!expectedPasswordHash.equals(account.getPasswordHash())) {
                    account.setPasswordHash(expectedPasswordHash);
                    needsSave = true;
                }
                if (needsSave) {
                    appUserRepository.save(account);
                    repaired++;
                }
            }

            if (created > 0 || adopted > 0 || repaired > 0 || skipped > 0) {
                System.out.println("Resource login accounts: created " + created + ", adopted " + adopted
                        + ", repaired " + repaired + ", skipped " + skipped
                        + ". Shared password for every resource person is currently: " + resourceUserPassword);
            }
        };
    }
}

