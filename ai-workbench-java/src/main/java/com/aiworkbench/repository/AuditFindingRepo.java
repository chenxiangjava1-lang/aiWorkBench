package com.aiworkbench.repository;
import com.aiworkbench.entity.AuditFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository public interface AuditFindingRepo extends JpaRepository<AuditFinding, Integer> {
    List<AuditFinding> findByResolved(Integer resolved);
    List<AuditFinding> findByTaskKeyOrderByDetectedAtDesc(String taskKey);
    List<AuditFinding> findByTaskKeyAndResolved(String taskKey, Integer resolved);
    long countByResolved(Integer resolved);
}
