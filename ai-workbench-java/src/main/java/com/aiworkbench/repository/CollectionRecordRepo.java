package com.aiworkbench.repository;
import com.aiworkbench.entity.CollectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository public interface CollectionRecordRepo extends JpaRepository<CollectionRecord, String> {
    List<CollectionRecord> findByTaskKey(String taskKey);
    CollectionRecord findTopByTaskKey(String taskKey);
}
