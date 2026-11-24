-- 清空所有表数据（9 张表，按依赖顺序清空）
-- 先删巡检和快照（依赖 analysis_results），再删 analysis 和 record，最后删核心 task
-- 小表（connectors / assets / intents）最后清空

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM audit_findings;
DELETE FROM metric_snapshots;
DELETE FROM analysis_results;
DELETE FROM collection_records;
DELETE FROM task_attempts;
DELETE FROM expected_tasks;
DELETE FROM intents;
DELETE FROM assets;
DELETE FROM connectors;

SET FOREIGN_KEY_CHECKS = 1;
