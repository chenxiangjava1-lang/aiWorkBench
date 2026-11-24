<template>
  <div>
    <!-- 顶部操作 -->
    <el-card class="action-bar">
      <div class="left">
        <el-button type="warning" @click="doRunAudit" :loading="auditing">
          <el-icon><Search /></el-icon> 🔍 运行巡检
        </el-button>
        <el-tag type="info" v-if="lastAuditCounts">上次巡检共发现 <b>{{ lastAuditCounts.total_findings }}</b> 条异常</el-tag>
      </div>
      <div class="right">
        <el-select v-model="filterType" placeholder="异常类型" clearable style="width:200px; margin-right:8px">
          <el-option v-for="(cnt, t) in counts" :key="t" :label="`${ATYPE_MAP[t] || t} (${cnt})`" :value="t" />
        </el-select>
        <el-switch v-model="onlyOpen" active-text="只看未修复" />
      </div>
    </el-card>

    <!-- 异常类型统计（横向 flex 排列） -->
    <div v-if="lastAuditCounts" class="stat-row">
      <div v-for="(cnt, t) in lastAuditCounts.counts" :key="t" class="stat-card">
        <div class="stat-num">{{ cnt }}</div>
        <div class="stat-label">{{ ATYPE_MAP[t] || t }}</div>
      </div>
    </div>

    <!-- Findings 列表 -->
    <el-card>
      <template #header><b>异常任务列表</b></template>
      <el-table :data="filteredFindings" border stripe v-loading="loadingFindings">
        <el-table-column label="异常类型" width="180">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'danger'">{{ ATYPE_MAP[row.anomaly_type] || row.anomaly_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="task_key" label="业务键" width="320">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.task_key)">{{ fmtTaskKey(row.task_key) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
        <el-table-column label="发现时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.detected_at) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'warning'" size="small">
              {{ row.resolved ? '已修复' : '未修复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row.task_key)">详情</el-button>
            <el-button link type="warning" size="small" @click="doRepair(row.task_key)" :disabled="row.resolved || repairing===row.task_key" :loading="repairing===row.task_key">
              {{ repairing===row.task_key ? '修复中...' : '修复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredFindings.length && !loadingFindings" description="暂无异常，先点「运行巡检」试试" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { runAudit, getFindings, repairTask } from '../api'
import { fmtTaskKey, fmtTime } from '../utils/format'

const router = useRouter()
const auditing = ref(false); const loadingFindings = ref(false); const repairing = ref('')
const findings = ref([])
const lastAuditCounts = ref(null)
const filterType = ref(''); const onlyOpen = ref(true)

// 异常类型中文映射
const ATYPE_MAP = {
  missing_task: '缺失执行记录',
  collection_failed: '采集失败',
  analysis_failed: '分析失败',
  running_stale: '运行超时',
  queued_stale: '排队超时',
  duplicate_attempt: '重复执行',
  invalid_evidence: '证据不可追溯',
  aggregate_inconsistency: '汇总数据不一致',
}

const counts = computed(() => {
  const m = {}
  for (const f of findings.value) m[f.anomaly_type] = (m[f.anomaly_type] || 0) + 1
  return m
})
const filteredFindings = computed(() => findings.value.filter(f =>
  (!filterType.value || f.anomaly_type === filterType.value) &&
  (!onlyOpen.value || !f.resolved)
))

async function doRunAudit() {
  auditing.value = true
  try {
    const res = await runAudit()
    lastAuditCounts.value = res
    ElMessage.success(`巡检完成！共发现 ${res.total_findings} 条异常`)
    await loadFindings()
  } finally { auditing.value = false }
}
async function loadFindings() {
  loadingFindings.value = true
  try {
    const res = await getFindings({ resolved: onlyOpen.value ? 'false' : undefined, anomaly_type: filterType.value || undefined })
    findings.value = res.rows
  } finally { loadingFindings.value = false }
}
async function doRepair(taskKey) {
  try {
    await ElMessageBox.confirm(`对 ${taskKey} 执行补采 + 重新分析修复？`, '确认修复', { type: 'warning' })
  } catch { return }
  repairing.value = taskKey  // 标记"正在修复这条"
  try {
    const res = await repairTask(taskKey)
    if (res.ok) {
      ElMessage.success(`修复成功！remaining_findings=${res.remaining_findings}`)
      await loadFindings()
      await doRunAudit()
    } else {
      ElMessage.error(res.error || '修复失败')
    }
  } catch (e) {
    ElMessage.error(e?.error || e?.message || '修复失败')
  } finally {
    repairing.value = ''
  }
}
function goDetail(k) { router.push({ name: 'TaskDetail', params: { taskKey: k } }) }

onMounted(async () => {
  await doRunAudit()
})
</script>

<style scoped>
.action-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.left, .right { display: flex; align-items: center; gap: 8px; }
/* 统计卡片：横向自动换行 */
.stat-row {
  display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px;
}
.stat-card {
  flex: 1 1 140px; min-width: 140px; max-width: 220px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px 16px; text-align: center;
  transition: all .2s;
}
.stat-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); transform: translateY(-2px); }
.stat-num { font-size: 26px; font-weight: 700; color: #e6a23c; line-height: 1.2; }
.stat-label { font-size: 13px; color: #606266; margin-top: 6px; }
</style>
