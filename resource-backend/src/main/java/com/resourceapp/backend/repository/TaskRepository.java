package com.resourceapp.backend.repository;

import com.resourceapp.backend.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByTaskCode(String taskCode);
}
