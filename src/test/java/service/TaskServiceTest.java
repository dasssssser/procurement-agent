package service;
import org.example.entity.EntityTask;
import org.example.repository.TaskRepository;
import org.example.service.TaskService;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void testGetTask_ShouldReturnTask() {
        UUID id = UUID.randomUUID();
        EntityTask task = new EntityTask();
        task.setId(id);
        task.setStatus(EntityTask.TaskStatus.PENDING);

        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        EntityTask result = taskService.getTask(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    void testGetTask_ShouldThrowException() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> taskService.getTask(id));
    }
}