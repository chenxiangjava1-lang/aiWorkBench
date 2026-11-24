package com.aiworkbench.controller;

import com.aiworkbench.service.AnalyzeService;
import com.aiworkbench.service.SeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired SeedService seedService;
    @Autowired AnalyzeService analyzeService;
    @Autowired DataSource dataSource;

    @Value("${spring.datasource.url:}") String datasourceUrl;

    // ====== Health ======
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("backend", "Spring Boot (Java 17)");
        r.put("datasource", datasourceUrl);
        try (Connection c = dataSource.getConnection()) {
            r.put("db_ok", true);
            r.put("db_product", c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            r.put("db_ok", false);
            r.put("db_error", e.getMessage());
        }
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("live_available", analyzeService.canUseLive());
        llm.put("endpoint", System.getenv("LLM_ENDPOINT") != null ? System.getenv("LLM_ENDPOINT") : "(未配置)");
        llm.put("model", System.getenv().getOrDefault("LLM_MODEL", "gpt-4o-mini"));
        llm.put("note", analyzeService.canUseLive()
            ? "Live LLM 已就绪"
            : "未配置 LLM_ENDPOINT/LLM_API_KEY，所有分析走 Mock 模式；设环境变量即可开启 Live");
        r.put("llm", llm);
        return ResponseEntity.ok(r);
    }

    // ====== Seed ======
    @PostMapping("/seed/import")
    public ResponseEntity<?> importDefault() {
        try {
            Map<String, Object> stats = seedService.importDefault();
            return ResponseEntity.ok(Map.of("ok", true, "stats", stats));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/seed/upload")
    public ResponseEntity<?> uploadSeed(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> stats = seedService.importBytes(file.getBytes());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", true);
            r.put("stats", stats);
            r.put("file_name", file.getOriginalFilename());
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
