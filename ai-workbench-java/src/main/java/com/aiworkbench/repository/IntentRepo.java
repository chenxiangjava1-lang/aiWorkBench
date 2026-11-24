package com.aiworkbench.repository;
import com.aiworkbench.entity.Intent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository public interface IntentRepo extends JpaRepository<Intent, String> {}
