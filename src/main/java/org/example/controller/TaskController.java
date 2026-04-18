package org.example.controller;

import org.example.entity.EntityTask;
import org.example.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/cp")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, String>> createTask(
            @RequestParam("file") MultipartFile file) {
        UUID taskId = taskService.createFile(file);
        Map<String, String> response = new HashMap<>();
        response.put("task_id", taskId.toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<EntityTask> getTask(@PathVariable UUID taskId) {
        EntityTask task = taskService.getTask(taskId);
        return ResponseEntity.ok(task);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Object[]>> searchSuppliers(
            @RequestParam("q") String query) {
        List<Object[]> suppliers = taskService.findTopSuppliers(query);
        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

}