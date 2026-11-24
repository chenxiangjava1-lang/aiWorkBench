package com.aiworkbench.repository;
import com.aiworkbench.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository public interface AnalysisResultRepo extends JpaRepository<AnalysisResult, String> {
    List<AnalysisResult> findByTaskKeyOrderByAnalyzedAtDesc(String taskKey);
    List<AnalysisResult> findByTaskKey(String taskKey);
    @Modifying @Query("DELETE FROM AnalysisResult WHERE taskKey = :taskKey")
    void deleteByTaskKey(String taskKey);
}
