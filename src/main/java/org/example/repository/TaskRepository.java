package org.example.repository;

import org.example.entity.EntityTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<EntityTask, UUID> {
}