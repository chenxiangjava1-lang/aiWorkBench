package com.aiworkbench.repository;
import com.aiworkbench.entity.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository public interface ConnectorRepo extends JpaRepository<Connector, String> {}
