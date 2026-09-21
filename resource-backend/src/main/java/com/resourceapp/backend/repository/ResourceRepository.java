package com.resourceapp.backend.repository;

import com.resourceapp.backend.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    // findAll(), findById(), save(), deleteById() come for free from JpaRepository

    Optional<Resource> findByNameId(String nameId);

    boolean existsByNameId(String nameId);
}
