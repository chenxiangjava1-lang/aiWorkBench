package com.aiworkbench.service;

import com.aiworkbench.entity.*;
import com.aiworkbench.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** 种子数据导入 — 对应 Node 版 services.js 的 importSeedData() */
@Service
public class SeedService {

    @Autowired ConnectorRepo connectorRepo;
    @Autowired AssetRepo assetRepo;
    @Autowired IntentRepo intentRepo;
    @Autowired ExpectedTaskRepo taskRepo;
    @Autowired TaskAttemptRepo attemptRepo;
    @Autowired CollectionRecordRepo recordRepo;
    @Autowired AnalysisResultRepo resultRepo;
    @Autowired MetricSnapshotRepo snapRepo;
    @Autowired AuditFindingRepo findingRepo;

    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public Map<String, Object> importDefault() {
        // 先清空所有表（对应 Node 版的 resetDb）
        findingRepo.deleteAllInBatch();
        snapRepo.deleteAllInBatch();
        resultRepo.deleteAllInBatch();
        recordRepo.deleteAllInBatch();
        attemptRepo.deleteAllInBatch();
        taskRepo.deleteAllInBatch();
        intentRepo.deleteAllInBatch();
        assetRepo.deleteAllInBatch();
        connectorRepo.deleteAllInBatch();

        Path p = Path.of("F:/aiProject/1/AI全栈笔试_模拟数据包/seed_all.json");
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return doImport(om.readTree(json));
        } catch (Exception e) {
            throw new RuntimeException("读种子文件失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> importBytes(byte[] bytes) {
        findingRepo.deleteAllInBatch();
        snapRepo.deleteAllInBatch();
        resultRepo.deleteAllInBatch();
        recordRepo.deleteAllInBatch();
        attemptRepo.deleteAllInBatch();
        taskRepo.deleteAllInBatch();
        intentRepo.deleteAllInBatch();
        assetRepo.deleteAllInBatch();
        connectorRepo.deleteAllInBatch();
        try {
            return doImport(om.readTree(new String(bytes, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("解析 JSON 失败: " + e.getMessage());
        }
    }

    private Map<String, Object> doImport(com.fasterxml.jackson.databind.JsonNode data) {
        // connectors
        if (data.has("connectors")) for (var n : data.get("connectors")) {
            var c = new Connector();
            c.setConnectorId(str(n, "connector_id"));
            c.setPlatform(str(n, "platform"));
            c.setConnectorType(str(n, "connector_type"));
            c.setCollectionMode(str(n, "collection_mode"));
            c.setEndpoint(str(n, "endpoint"));
            c.setAuthMode(str(n, "auth_mode"));
            c.setRateLimitPerMinute(intn(n, "rate_limit_per_minute"));
            c.setEnabled(n.has("enabled") ? n.get("enabled").asInt() : 1);
            c.setNote(str(n, "note"));
            connectorRepo.save(c);
        }

        // assets
        if (data.has("assets")) for (var n : data.get("assets")) {
            var a = new Asset();
            a.setAssetId(str(n, "asset_id"));
            a.setAssetType(str(n, "asset_type"));
            a.setAssetName(str(n, "asset_name"));
            a.setBrandId(str(n, "brand_id"));
            a.setBrandName(str(n, "brand_name"));
            a.setIndustry(str(n, "industry"));
            a.setOfficialDomain(str(n, "official_domain"));
            a.setReferenceClaim(str(n, "reference_claim"));
            a.setActive(n.has("active") ? n.get("active").asInt() : 1);
            a.setTenantId(str(n, "tenant_id"));
            assetRepo.save(a);
        }

        // intents
        if (data.has("intents")) for (var n : data.get("intents")) {
            var i = new Intent();
            i.setIntentId(str(n, "intent_id"));
            i.setAssetId(str(n, "asset_id"));
            i.setIntentType(str(n, "intent_type"));
            i.setQuestion(str(n, "question"));
            i.setScenario(str(n, "scenario"));
            i.setLanguage(str(n, "language"));
            intentRepo.save(i);
        }

        // expected_tasks
        if (data.has("expected_tasks")) for (var n : data.get("expected_tasks")) {
            var t = new ExpectedTask();
            t.setTaskKey(str(n, "task_key"));
            t.setAssetId(str(n, "asset_id"));
            t.setBrandId(str(n, "brand_id"));
            t.setTenantId(str(n, "tenant_id"));
            t.setIntentId(str(n, "intent_id"));
            t.setPlatform(str(n, "platform"));
            t.setBusinessDate(str(n, "business_date"));
            t.setQuestion(str(n, "question"));
            t.setAnalysisDueAt(str(n, "analysis_due_at"));
            t.setPriority(str(n, "priority"));
            taskRepo.save(t);
        }

        // task_attempts：字段映射 state→status
        if (data.has("task_attempts")) for (var n : data.get("task_attempts")) {
            var a = new TaskAttempt();
            a.setAttemptId(str(n, "attempt_id"));
            a.setTaskKey(str(n, "task_key"));
            a.setAttemptNo(intn(n, "attempt_no"));
            a.setStatus(str(n, "state"));
            a.setStartedAt(str(n, "queued_at"));
            a.setFinishedAt(str(n, "finished_at"));
            a.setErrorReason(null);
            a.setErrorCode(str(n, "error_code"));
            attemptRepo.save(a);
        }

        // collection_records：capture_id→record_id
        if (data.has("collection_records")) for (var n : data.get("collection_records")) {
            var r = new CollectionRecord();
            r.setRecordId(str(n, "capture_id"));
            r.setTaskKey(str(n, "task_key"));
            r.setAttemptId(null);
            r.setConnectorId(null);
            r.setRawAnswer(str(n, "raw_answer"));
            r.setSourceUrl(str(n, "source_url"));
            r.setCollectedAt(str(n, "captured_at"));
            recordRepo.save(r);
        }

        // analysis_results：boolean→字符串
        if (data.has("analysis_results")) for (var n : data.get("analysis_results")) {
            var r = new AnalysisResult();
            r.setResultId(str(n, "result_id"));
            r.setTaskKey(str(n, "task_key"));
            r.setRecordId(str(n, "capture_id"));
            r.setSummary(str(n, "summary"));
            r.setBrandMentioned(mapBoolToMention(n.get("brand_mentioned")));
            r.setRecommended(mapBoolToRec(n.get("recommended")));
            r.setEvidenceText(str(n, "evidence_text"));
            r.setCitationUrls(toJson(n.get("citation_urls")));
            r.setRiskFlags(toJson(n.get("risk_flags")));
            r.setAnalysisSource(str(n, "model_mode", "seed"));
            r.setAnalyzedAt(str(n, "analyzed_at"));
            r.setIsValid(1);
            r.setValidationNote(null);
            resultRepo.save(r);
        }

        // metric_snapshots
        if (data.has("metric_snapshots")) for (var n : data.get("metric_snapshots")) {
            var s = new MetricSnapshot();
            s.setBrandId(str(n, "brand_id"));
            s.setPlatform(str(n, "platform"));
            s.setBusinessDate(str(n, "business_date"));
            s.setMentionedCount(intn(n, "reported_completed"));
            s.setRecommendedCount(0);
            s.setTotalTasks(intn(n, "expected_tasks"));
            s.setSnapshotTakenAt(str(n, "generated_at"));
            snapRepo.save(s);
        }

        // 二次校验：evidence 不在 raw_answer 里 → is_valid=0
        resultRepo.findAll().forEach(ar -> {
            if (ar.getEvidenceText() != null && !ar.getEvidenceText().isEmpty()) {
                recordRepo.findById(ar.getRecordId()).ifPresent(cr -> {
                    if (cr.getRawAnswer() != null && !cr.getRawAnswer().contains(ar.getEvidenceText())) {
                        ar.setIsValid(0);
                        ar.setValidationNote("evidence_text 不可追溯");
                        resultRepo.save(ar);
                    }
                });
            }
        });

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("connectors", connectorRepo.count());
        stats.put("assets", assetRepo.count());
        stats.put("intents", intentRepo.count());
        stats.put("expected_tasks", taskRepo.count());
        stats.put("task_attempts", attemptRepo.count());
        stats.put("collection_records", recordRepo.count());
        stats.put("analysis_results", resultRepo.count());
        stats.put("metric_snapshots", snapRepo.count());
        return stats;
    }

    private String str(com.fasterxml.jackson.databind.JsonNode n, String key) {
        return str(n, key, null);
    }
    private String str(com.fasterxml.jackson.databind.JsonNode n, String key, String def) {
        if (!n.has(key) || n.get(key).isNull()) return def;
        return n.get(key).asText();
    }
    private Integer intn(com.fasterxml.jackson.databind.JsonNode n, String key) {
        if (!n.has(key) || n.get(key).isNull()) return null;
        return n.get(key).asInt();
    }
    private String toJson(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || n.isNull()) return null;
        try { return om.writeValueAsString(n); } catch (Exception e) { return null; }
    }
    private String mapBoolToMention(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || n.isNull()) return "no";
        if (n.isBoolean()) return n.asBoolean() ? "yes" : "no";
        return n.asText();
    }
    private String mapBoolToRec(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || n.isNull()) return "neutral";
        if (n.isBoolean()) return n.asBoolean() ? "yes" : "no";
        return n.asText();
    }
}
