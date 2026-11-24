package com.aiworkbench.repository;
import com.aiworkbench.entity.ExpectedTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository public interface ExpectedTaskRepo extends JpaRepository<ExpectedTask, String> {
    List<ExpectedTask> findByTaskKeyLike(String pattern);
}
