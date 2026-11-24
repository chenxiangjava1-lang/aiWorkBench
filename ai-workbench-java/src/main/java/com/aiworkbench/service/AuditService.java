package com.aiworkbench.service;

import com.aiworkbench.entity.*;
import com.aiworkbench.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiworkbench.config.TimeUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 巡检 — 对应 Node 版 services.js 的 runAudit() */
@Service
public class AuditService {

    @Autowired ExpectedTaskRepo taskRepo;
    @Autowired TaskAttemptRepo attemptRepo;
    @Autowired CollectionRecordRepo recordRepo;
    @Autowired AnalysisResultRepo resultRepo;
    @Autowired AuditFindingRepo findingRepo;
    @Autowired MetricSnapshotRepo snapRepo;

    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public Map<String, Object> run() {
        findingRepo.deleteAllInBatch();
        String now = TimeUtil.now();
        Map<String, Long> counts = new LinkedHashMap<>();

        for (ExpectedTask t : taskRepo.findAll()) {
            String tk = t.getTaskKey();
            List<TaskAttempt> attempts = attemptRepo.findByTaskKey(tk);
            List<CollectionRecord> records = recordRepo.findByTaskKey(tk);
            List<AnalysisResult> results = resultRepo.findByTaskKey(tk);

            if (attempts.isEmpty()) {
                addFinding(tk, "missing_task", "应有任务但无任何执行记录", now, counts);
                continue;
            }
            if (records.isEmpty()) {
                addFinding(tk, "collection_failed", "有执行但没采集到原始回答", now, counts);
            }
            if (results.isEmpty()) {
                addFinding(tk, "analysis_failed", "有原始回答但没分析结果", now, counts);
            }

            // running_stale / queued_stale
            Instant nowI = Instant.now();
            for (TaskAttempt a : attempts) {
                if (a.getStartedAt() == null) continue;
                Instant started = safeParse(a.getStartedAt());
                if (started == null) continue;
                long mins = ChronoUnit.MINUTES.between(started, nowI);
                if ("running".equals(a.getStatus()) && mins > 15) {
                    addFinding(tk, "running_stale", "attempt " + a.getAttemptId() + " 运行超15分钟", now, counts);
                }
                if ("queued".equals(a.getStatus()) && mins > 30) {
                    addFinding(tk, "queued_stale", "attempt " + a.getAttemptId() + " 排队超30分钟", now, counts);
                }
            }

            // duplicate_attempt
            long completedCount = attempts.stream().filter(a -> "completed".equals(a.getStatus())).count();
            if (completedCount > 1) {
                addFinding(tk, "duplicate_attempt", "有 " + completedCount + " 次 completed attempt", now, counts);
            }

            // invalid_evidence
            for (AnalysisResult ar : results) {
                if (ar.getIsValid() != null && ar.getIsValid() == 0) {
                    addFinding(tk, "invalid_evidence", "analysis_result 标记为无效", now, counts);
                    break;
                }
                if (ar.getEvidenceText() != null && !ar.getEvidenceText().isBlank()) {
                    String raw = records.stream()
                        .filter(r -> ar.getRecordId() != null && ar.getRecordId().equals(r.getRecordId()))
                        .map(CollectionRecord::getRawAnswer).filter(Objects::nonNull).findFirst().orElse("");
                    if (!raw.isBlank() && !raw.contains(ar.getEvidenceText())) {
                        addFinding(tk, "invalid_evidence", "evidence_text 在 raw_answer 中找不到", now, counts);
                        break;
                    }
                }
            }
        }

        // aggregate_inconsistency
        for (MetricSnapshot s : snapRepo.findAll()) {
            List<AnalysisResult> matchedResults = new ArrayList<>();
            for (ExpectedTask t : taskRepo.findAll()) {
                if (Objects.equals(t.getBrandId(), s.getBrandId())
                    && Objects.equals(t.getPlatform(), s.getPlatform())
                    && Objects.equals(t.getBusinessDate(), s.getBusinessDate())) {
                    matchedResults.addAll(resultRepo.findByTaskKey(t.getTaskKey()));
                }
            }
            long actualMentioned = matchedResults.stream()
                .filter(r -> "yes".equals(r.getBrandMentioned())).count();
            if (s.getMentionedCount() != null && s.getMentionedCount() != actualMentioned) {
                ExpectedTask sample = taskRepo.findAll().stream()
                    .filter(t -> Objects.equals(t.getBrandId(), s.getBrandId())
                              && Objects.equals(t.getPlatform(), s.getPlatform())
                              && Objects.equals(t.getBusinessDate(), s.getBusinessDate()))
                    .findFirst().orElse(null);
                addFinding(
                    sample != null ? sample.getTaskKey()
                      : "AGG-" + s.getBrandId() + "|" + s.getPlatform() + "|" + s.getBusinessDate(),
                    "aggregate_inconsistency",
                    "snapshot mentioned_count=" + s.getMentionedCount() + " 但实际=" + actualMentioned,
                    now, counts);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("counts", counts);
        resp.put("total_findings", findingRepo.count());
        return resp;
    }

    private void addFinding(String tk, String type, String desc, String now, Map<String, Long> counts) {
        AuditFinding f = new AuditFinding();
        f.setTaskKey(tk);
        f.setAnomalyType(type);
        f.setDescription(desc);
        f.setDetectedAt(now);
        findingRepo.save(f);
        counts.merge(type, 1L, Long::sum);
    }

    /** 安全解析时间字符串（支持 yyyy-MM-dd HH:mm:ss 和 ISO 8601） */
    private Instant safeParse(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            String v = s.trim();
            // yyyy-MM-dd HH:mm:ss → 转成 ISO
            if (v.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                v = v.replace(" ", "T") + "Z";
                return Instant.parse(v);
            }
            // 无时区补 Z
            if (v.length() > 19 && !v.matches(".*[Zz+\\-].*")) {
                v = v + "Z";
            } else if (v.length() == 19 && v.charAt(19) != 'Z' && v.charAt(19) != '+' && v.charAt(19) != '-') {
                v = v + "Z";
            }
            return Instant.parse(v);
        } catch (Exception e) {
            return null;
        }
    }
}
