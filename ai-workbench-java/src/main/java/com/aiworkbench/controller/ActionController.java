package com.aiworkbench.controller;

import com.aiworkbench.service.AnalyzeService;
import com.aiworkbench.service.AuditService;
import com.aiworkbench.service.RepairService;
import com.aiworkbench.entity.AuditFinding;
import com.aiworkbench.repository.AuditFindingRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController @RequestMapping("/api")
public class ActionController {

    @Autowired AnalyzeService analyzeService;
    @Autowired AuditService auditService;
    @Autowired RepairService repairService;
    @Autowired AuditFindingRepo findingRepo;

    @PostMapping({"/analyze/{taskKey}", "/analyze"})
    public ResponseEntity<?> analyze(
            @PathVariable(required = false) String taskKey,
            @RequestParam(required = false) String task_key) {
        if (taskKey == null || taskKey.isBlank()) taskKey = task_key;
        try {
            Map<String, Object> r = analyzeService.analyze(taskKey);
            if ((boolean) r.get("ok")) return ResponseEntity.ok(r);
            return ResponseEntity.status(400).body(r);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/audit/run")
    public ResponseEntity<?> runAudit() {
        try { return ResponseEntity.ok(auditService.run()); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage())); }
    }

    @GetMapping("/audit/findings")
    public ResponseEntity<?> findings(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) String anomaly_type) {
        List<AuditFinding> all = findingRepo.findAll();
        List<AuditFinding> filtered = all.stream()
            .filter(f -> resolved == null || Objects.equals(f.getResolved(), resolved ? 1 : 0))
            .filter(f -> anomaly_type == null || anomaly_type.equals(f.getAnomalyType()))
            .sorted(Comparator.comparing(AuditFinding::getDetectedAt).reversed())
            .toList();
        return ResponseEntity.ok(Map.of("ok", true, "rows", filtered));
    }

    @PostMapping({"/repair/{taskKey}", "/repair"})
    public ResponseEntity<?> repair(
            @PathVariable(required = false) String taskKey,
            @RequestParam(required = false) String task_key) {
        if (taskKey == null || taskKey.isBlank()) taskKey = task_key;
        try { return ResponseEntity.ok(repairService.repair(taskKey)); }
        catch (Exception e) { return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage())); }
    }

    @PostMapping({"/collect/real-answer/{taskKey}", "/collect/real-answer"})
    public ResponseEntity<?> collectRealAnswer(
            @PathVariable(required = false) String taskKey,
            @RequestParam(required = false) String task_key) {
        if (taskKey == null || taskKey.isBlank()) taskKey = task_key;
        try {
            Map<String, Object> r = analyzeService.collectRealAnswer(taskKey);
            if ((boolean) r.get("ok")) return ResponseEntity.ok(r);
            return ResponseEntity.status(400).body(r);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
