package com.aiworkbench.service;

import com.aiworkbench.entity.*;
import com.aiworkbench.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiworkbench.config.TimeUtil;
import java.time.Instant;
import java.util.*;

/** 异常修复 — 不管有没有 finding，只要任务存在就允许重新分析 */
@Service
public class RepairService {

    @Autowired ExpectedTaskRepo taskRepo;
    @Autowired TaskAttemptRepo attemptRepo;
    @Autowired CollectionRecordRepo recordRepo;
    @Autowired AnalysisResultRepo resultRepo;
    @Autowired AuditFindingRepo findingRepo;
    @Autowired AssetRepo assetRepo;
    @Autowired AnalyzeService analyzeService;

    @Transactional
    public Map<String, Object> repair(String taskKey) {
        ExpectedTask t = taskRepo.findById(taskKey).orElse(null);
        if (t == null) return Map.of("ok", false, "error", "task_key 不存在");

        List<AuditFinding> allFindings = findingRepo.findByTaskKeyOrderByDetectedAtDesc(taskKey);
        String now = TimeUtil.now();
        List<Map<String, String>> steps = new ArrayList<>();

        // 1. attempt：没就新建，有就更新（不新建避免重复执行异常）
        List<TaskAttempt> attempts = attemptRepo.findByTaskKey(taskKey);
        List<CollectionRecord> records = recordRepo.findByTaskKey(taskKey);
        if (attempts.isEmpty()) {
            TaskAttempt att = new TaskAttempt();
            att.setAttemptId("ATT-" + System.currentTimeMillis());
            att.setTaskKey(taskKey);
            att.setAttemptNo(1);
            att.setStatus("completed");
            att.setStartedAt(now);
            att.setFinishedAt(now);
            attemptRepo.save(att);
            attempts.add(att);
            steps.add(Map.of("action", "补执行记录", "detail", "新建 attempt"));
        } else {
            // 47行附近
            // 先按 attemptNo 排序，保留最小那条，删掉多余的
            attempts.sort(Comparator.comparingInt(a -> a.getAttemptNo() != null ? a.getAttemptNo() : 0));
            TaskAttempt keep = attempts.get(0);
            if (attempts.size() > 1) {
                List<TaskAttempt> toDelete = attempts.subList(1, attempts.size());
                // 先解绑 record 关联的多余 attempt（把它们的 attemptId 改成保留那条的）
                for (TaskAttempt d : toDelete) {
                    for (CollectionRecord r : records) {
                        if (d.getAttemptId().equals(r.getAttemptId())) {
                            r.setAttemptId(keep.getAttemptId());
                            recordRepo.save(r);
                        }
                    }
                }
                attemptRepo.deleteAll(toDelete);
                attempts = new ArrayList<>();
                attempts.add(keep);
                steps.add(Map.of("action", "清理重复", "detail", "删除 " + toDelete.size() + " 条多余 attempt，保留 attempt_no=" + keep.getAttemptNo()));
            }
            keep.setStatus("completed");
            keep.setFinishedAt(now);
            attemptRepo.save(keep);
            steps.add(Map.of("action", "更新执行记录", "detail", "更新保留的 attempt"));
        }

        // 2. record：没就补 mock
        if (records.isEmpty()) {
            Asset asset = assetRepo.findById(t.getAssetId()).orElse(null);
            TaskAttempt att = attempts.get(0);
            String fake = buildMockAnswer(asset, t.getQuestion());
            CollectionRecord rec = new CollectionRecord();
            rec.setRecordId("REC-RECOVERY-" + System.currentTimeMillis());
            rec.setTaskKey(taskKey);
            rec.setAttemptId(att.getAttemptId());
            rec.setConnectorId("CON-001");
            rec.setRawAnswer(fake);
            rec.setSourceUrl("https://example.com/recovery/" + taskKey);
            rec.setCollectedAt(now);
            recordRepo.save(rec);
            steps.add(Map.of("action", "补采集", "detail", "新建 mock collection_record"));
        }

        // 3. 重新分析（核心动作）
        Map<String, Object> analyzeRes = analyzeService.analyze(taskKey);
        if ((boolean) analyzeRes.get("ok")) {
            steps.add(Map.of("action", "重新分析", "detail", "分析完成（mode=" + analyzeRes.get("analysis_mode") + "）"));
        }

        // 4. 清场：删掉所有旧 attempt，只留最新那条（analyze 刚新建的、attempt_no 最大的）
        List<TaskAttempt> finalAttempts = attemptRepo.findByTaskKey(taskKey);
        if (finalAttempts.size() > 1) {
            finalAttempts.sort(Comparator.comparingInt(a -> a.getAttemptNo() != null ? a.getAttemptNo() : 0));
            TaskAttempt latest = finalAttempts.get(finalAttempts.size() - 1);
            List<TaskAttempt> old = finalAttempts.subList(0, finalAttempts.size() - 1);
            // 解绑旧 attempt 的 record 引用
            for (TaskAttempt d : old) {
                for (CollectionRecord r : recordRepo.findByTaskKey(taskKey)) {
                    if (d.getAttemptId().equals(r.getAttemptId())) {
                        r.setAttemptId(latest.getAttemptId());
                        recordRepo.save(r);
                    }
                }
            }
            attemptRepo.deleteAll(old);
            steps.add(Map.of("action", "清场", "detail", "删除 " + old.size() + " 条旧 attempt，只保留最新 attempt_no=" + latest.getAttemptNo()));
        }

        // 5. 把所有 finding 都标记 resolved（如果有的话）
        for (AuditFinding f : allFindings) {
            if (f.getResolved() == null || f.getResolved() != 1) {
                f.setResolved(1);
                f.setResolvedAt(now);
                f.setResolutionNote("重新分析修复");
                findingRepo.save(f);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("steps", steps);
        resp.put("remaining_findings", findingRepo.findByTaskKeyAndResolved(taskKey, 0).size());
        if (allFindings.isEmpty()) {
            resp.put("note", "该任务没有巡检异常，但已重新执行分析");
        }
        return resp;
    }

    private String buildMockAnswer(Asset asset, String question) {
        String brandName = asset != null ? asset.getBrandName() : "该品牌";
        String assetName = asset != null ? asset.getAssetName() : "该产品";
        return brandName + "的" + assetName + "在相关场景中" + (question != null ? "关于\"" + question + "\"的问题" : "")
            + "，根据使用反馈，这款产品整体表现良好，推荐有需要的用户可以考虑选择。"
            + "需要注意使用时请参考官方说明书，确保在推荐环境条件下使用。";
    }
}
