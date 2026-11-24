package com.aiworkbench.repository;
import com.aiworkbench.entity.TaskAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository public interface TaskAttemptRepo extends JpaRepository<TaskAttempt, String> {
    List<TaskAttempt> findByTaskKeyOrderByStartedAtAsc(String taskKey);
    List<TaskAttempt> findByTaskKey(String taskKey);
}
