package com.aiworkbench.controller;

import com.aiworkbench.entity.*;
import com.aiworkbench.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import com.aiworkbench.config.TimeUtil;

@RestController @RequestMapping("/api")
public class DashboardController {

    @Autowired ExpectedTaskRepo taskRepo;
    @Autowired TaskAttemptRepo attemptRepo;
    @Autowired CollectionRecordRepo recordRepo;
    @Autowired AnalysisResultRepo resultRepo;
    @Autowired AuditFindingRepo findingRepo;
    @Autowired AssetRepo assetRepo;
    @Autowired ConnectorRepo connectorRepo;
    @Autowired IntentRepo intentRepo;

    private final ObjectMapper om = new ObjectMapper();

    @GetMapping("/dashboard/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestParam(required = false) String brand_id,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String business_date) {

        // 空字符串统一跳过筛选（前端可能传 ?brand_id=&platform=）
        String bid = (brand_id == null || brand_id.isBlank()) ? null : brand_id;
        String plt = (platform == null || platform.isBlank()) ? null : platform;
        String bdate = (business_date == null || business_date.isBlank()) ? null : business_date;

        List<ExpectedTask> all = taskRepo.findAll();
        List<ExpectedTask> filtered = all.stream().filter(t ->
            (bid == null || bid.equals(t.getBrandId()))
            && (plt == null || plt.equals(t.getPlatform()))
            && (bdate == null || bdate.equals(t.getBusinessDate()))
        ).toList();

        int expected = filtered.size();
        Set<String> keys = filtered.stream().map(ExpectedTask::getTaskKey).collect(Collectors.toSet());

        int attempted = (int) attemptRepo.findAll().stream()
            .filter(a -> keys.contains(a.getTaskKey())).map(TaskAttempt::getTaskKey).distinct().count();
        int collected = (int) recordRepo.findAll().stream()
            .filter(r -> keys.contains(r.getTaskKey())).map(CollectionRecord::getTaskKey).distinct().count();
        int analyzed = (int) resultRepo.findAll().stream()
            .filter(r -> keys.contains(r.getTaskKey())).map(AnalysisResult::getTaskKey).distinct().count();
        int valid = (int) resultRepo.findAll().stream()
            .filter(r -> keys.contains(r.getTaskKey()) && r.getIsValid() != null && r.getIsValid() == 1)
            .map(AnalysisResult::getTaskKey).distinct().count();
        int anomaly = (int) findingRepo.findByResolved(0).stream()
            .filter(f -> keys.contains(f.getTaskKey())).map(AuditFinding::getTaskKey).distinct().count();

        // 提及率 / 推荐率
        List<AnalysisResult> matchedResults = resultRepo.findAll().stream()
            .filter(r -> keys.contains(r.getTaskKey())).toList();
        long mentioned = matchedResults.stream().filter(r -> "yes".equals(r.getBrandMentioned())).count();
        long recommended = matchedResults.stream().filter(r -> "yes".equals(r.getRecommended())).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expected", expected);
        data.put("valid", valid);
        data.put("attempted", attempted);
        data.put("collected", collected);
        data.put("analyzed", analyzed);
        data.put("anomaly", anomaly);
        data.put("completion_rate", expected > 0 ? Math.round(valid * 10000.0 / expected) / 100.0 : 0);
        data.put("mention_rate", matchedResults.size() > 0 ? Math.round(mentioned * 10000.0 / matchedResults.size()) / 100.0 : 0);
        data.put("recommend_rate", matchedResults.size() > 0 ? Math.round(recommended * 10000.0 / matchedResults.size()) / 100.0 : 0);
        return ResponseEntity.ok(Map.of("ok", true, "data", data));
    }

    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> filters() {
        List<Map<String, String>> brands = assetRepo.findAll().stream()
            .filter(a -> a.getBrandId() != null && !a.getBrandId().isBlank())
            .map(a -> Map.of("brand_id", a.getBrandId(), "brand_name", a.getBrandName() != null ? a.getBrandName() : ""))
            .distinct().toList();
        List<Map<String, String>> platforms = connectorRepo.findAll().stream()
            .map(c -> Map.of("platform", c.getPlatform())).distinct().toList();
        List<Map<String, String>> dates = taskRepo.findAll().stream()
            .map(t -> Map.of("business_date", t.getBusinessDate())).distinct().toList();
        return ResponseEntity.ok(Map.of("ok", true, "brands", brands, "platforms", platforms, "dates", dates));
    }

    /** 新增任务（前端表单提交） */
    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, String> body) {
        String brandId = body.getOrDefault("brand_id", "").trim();
        String platform = body.getOrDefault("platform", "").trim();
        String businessDate = body.getOrDefault("business_date", "").trim();
        String question = body.getOrDefault("question", "").trim();
        String assetId = body.getOrDefault("asset_id", "").trim();

        if (brandId.isBlank() || platform.isBlank() || businessDate.isBlank() || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "品牌/平台/日期/问题都必填"));
        }

        // 如果没指定 asset_id，用该 brand 下第一个 asset
        if (assetId.isBlank()) {
            assetId = assetRepo.findAll().stream()
                .filter(a -> brandId.equals(a.getBrandId()))
                .map(Asset::getAssetId).findFirst().orElse("ASSET-" + brandId);
        }

        // 生成 task_key
        String taskKey = brandId + "|" + platform + "|" + businessDate;
        // 避免重复：追加序号
        int dup = taskRepo.findByTaskKeyLike(taskKey + "%").size();
        if (dup > 0) taskKey = taskKey + "|" + (dup + 1);

        ExpectedTask t = new ExpectedTask();
        t.setTaskKey(taskKey);
        t.setAssetId(assetId);
        t.setBrandId(brandId);
        t.setTenantId("TENANT-DEFAULT");
        t.setIntentId("INTENT-NEW-" + System.currentTimeMillis());
        t.setPlatform(platform);
        t.setBusinessDate(businessDate);
        t.setQuestion(question);
        t.setAnalysisDueAt(businessDate + "T10:00:00Z");
        t.setPriority("P2");
        taskRepo.save(t);

        // 自动建一条 attempt + 采集记录（模拟真实平台已返回回答）
        String now = TimeUtil.now();
        TaskAttempt att = new TaskAttempt();
        att.setAttemptId("ATT-" + System.currentTimeMillis());
        att.setTaskKey(taskKey);
        att.setAttemptNo(1);
        att.setStatus("completed");
        att.setStartedAt(now);
        att.setFinishedAt(now);
        attemptRepo.save(att);

        // 根据 asset 生成参考回答（让 AI 分析有东西可分析）
        String assetName = assetRepo.findById(assetId).map(a -> a.getAssetName()).orElse(brandId);
        String brandName = assetRepo.findById(assetId).map(a -> a.getBrandName()).orElse(brandId);
        String simulatedAnswer = "参考回答（" + platform + " · " + businessDate + "）\n"
            + "问题：" + question + "\n"
            + "回答侧重通用采购标准，同时对目标品牌进行评估：\n"
            + "1. 使用环境：需确认具体使用场景（户外/室内、温度范围等）；\n"
            + "2. 参数适配：核对规格是否满足需求；\n"
            + "3. " + brandName + " 的 " + assetName + " 在同类产品中具有一定参考价值，建议纳入对比清单；\n"
            + "4. 售后条款与保修范围需向原厂确认。\n"
            + "建议同时比较两至三家供应商，结合实际测试与用户评价综合决策。";

        CollectionRecord rec = new CollectionRecord();
        rec.setRecordId("REC-NEW-" + System.currentTimeMillis());
        rec.setTaskKey(taskKey);
        rec.setRawAnswer(simulatedAnswer);
        rec.setSourceUrl("https://" + platform.toLowerCase() + ".example/q/" + taskKey.replace("|", "-"));
        rec.setCollectedAt(now);
        recordRepo.save(rec);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("task_key", taskKey);
        resp.put("message", "任务创建成功，请到任务详情执行采集和分析");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> tasks(
            @RequestParam(required = false) String brand_id,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String business_date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int page_size) {

        // 空字符串统一跳过筛选
        String bid = (brand_id == null || brand_id.isBlank()) ? null : brand_id;
        String plt = (platform == null || platform.isBlank()) ? null : platform;
        String bdate = (business_date == null || business_date.isBlank()) ? null : business_date;

        List<ExpectedTask> all = taskRepo.findAll().stream().sorted(Comparator.comparing(ExpectedTask::getBusinessDate).reversed()
            .thenComparing(ExpectedTask::getPlatform).thenComparing(ExpectedTask::getAssetId)).toList();
        List<ExpectedTask> filtered = all.stream().filter(t ->
            (bid == null || bid.equals(t.getBrandId()))
            && (plt == null || plt.equals(t.getPlatform()))
            && (bdate == null || bdate.equals(t.getBusinessDate()))
        ).toList();

        int total = filtered.size();
        int fromIndex = (page - 1) * page_size;
        int toIndex = Math.min(fromIndex + page_size, total);
        List<ExpectedTask> pageData = fromIndex >= total ? List.of() : filtered.subList(fromIndex, toIndex);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpectedTask t : pageData) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("task_key", t.getTaskKey());
            row.put("asset_id", t.getAssetId());
            row.put("brand_id", t.getBrandId());
            row.put("brand_name", assetRepo.findById(t.getAssetId()).map(Asset::getBrandName).orElse(""));
            row.put("platform", t.getPlatform());
            row.put("business_date", t.getBusinessDate());
            row.put("question", t.getQuestion());
            row.put("attempt_count", attemptRepo.findByTaskKey(t.getTaskKey()).size());
            row.put("record_count", recordRepo.findByTaskKey(t.getTaskKey()).size());
            row.put("valid_result_count", resultRepo.findByTaskKey(t.getTaskKey()).stream()
                .filter(r -> r.getIsValid() != null && r.getIsValid() == 1).count());
            row.put("open_findings", findingRepo.findByTaskKeyAndResolved(t.getTaskKey(), 0).size());
            rows.add(row);
        }
        return ResponseEntity.ok(Map.of("ok", true, "total", total, "page", page, "page_size", page_size, "rows", rows));
    }

    @GetMapping({"/tasks/{taskKey}", "/tasks/detail"})
    public ResponseEntity<?> taskDetail(
            @PathVariable(required = false) String taskKey,
            @RequestParam(required = false) String task_key) {
        // 同时支持 path 变量和 query param（| 路径可能被 Tomcat 或浏览器截断）
        if (taskKey == null || taskKey.isBlank()) taskKey = task_key;
        ExpectedTask t = taskRepo.findById(taskKey).orElse(null);
        if (t == null) return ResponseEntity.status(404).body(Map.of("ok", false, "error", "任务不存在"));
        Asset a = assetRepo.findById(t.getAssetId()).orElse(null);
        Intent i = intentRepo.findById(t.getIntentId()).orElse(null);

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("task_key", t.getTaskKey());
        task.put("asset_id", t.getAssetId());
        task.put("asset_name", a != null ? a.getAssetName() : "");
        task.put("asset_type", a != null ? a.getAssetType() : "");
        task.put("brand_id", t.getBrandId());
        task.put("brand_name", a != null ? a.getBrandName() : "");
        task.put("platform", t.getPlatform());
        task.put("business_date", t.getBusinessDate());
        task.put("question", t.getQuestion());

        List<TaskAttempt> attempts = attemptRepo.findByTaskKeyOrderByStartedAtAsc(taskKey);
        List<CollectionRecord> records = recordRepo.findByTaskKey(taskKey);
        List<Map<String, Object>> results = new ArrayList<>();
        for (AnalysisResult r : resultRepo.findByTaskKey(taskKey)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("result_id", r.getResultId());
            m.put("brand_mentioned", r.getBrandMentioned());
            m.put("recommended", r.getRecommended());
            m.put("summary", r.getSummary());
            m.put("evidence_text", r.getEvidenceText());
            m.put("citation_urls", parseJson(r.getCitationUrls()));
            m.put("risk_flags", parseJson(r.getRiskFlags()));
            m.put("analysis_source", r.getAnalysisSource());
            m.put("analyzed_at", r.getAnalyzedAt());
            m.put("is_valid", r.getIsValid());
            m.put("validation_note", r.getValidationNote());
            results.add(m);
        }
        List<AuditFinding> findings = findingRepo.findByTaskKeyOrderByDetectedAtDesc(taskKey);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("data", Map.of("task", task, "attempts", attempts, "records", records, "results", results, "findings", findings));
        return ResponseEntity.ok(resp);
    }

    private List<String> parseJson(String s) {
        if (s == null || s.isBlank()) return List.of();
        try { return om.readValue(s, List.class); } catch (Exception e) { return List.of(s); }
    }
}
