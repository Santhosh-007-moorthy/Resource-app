package com.resourceapp.backend.repository;

import com.resourceapp.backend.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByRole(String role);

    // Looks up the login account tied to a given Resource, so ResourceController can keep
    // that account's username in sync (or remove it) when the resource is edited/deleted.
    Optional<AppUser> findByResourceId(Long resourceId);
}
