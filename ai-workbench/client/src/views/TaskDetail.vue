<template>
  <div v-loading="loading">
    <el-page-header @back="goBack" :content="fmtTaskKey(detail?.task?.task_key) || '加载中...'" class="page-header" />

    <template v-if="detail">
      <!-- 基本信息 -->
      <el-card class="section">
        <template #header><b>📋 任务信息</b></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="业务键">{{ fmtTaskKey(detail.task.task_key) }}</el-descriptions-item>
          <el-descriptions-item label="资产">{{ detail.task.asset_name }}（{{ detail.task.asset_type }}）</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ detail.task.brand_name }}</el-descriptions-item>
          <el-descriptions-item label="平台">{{ detail.task.platform }}</el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ detail.task.business_date }}</el-descriptions-item>
          <el-descriptions-item label="分析截止">{{ fmtTime(detail.task.analysis_due_at) }}</el-descriptions-item>
          <el-descriptions-item label="用户提问" :span="2">{{ detail.task.question }}</el-descriptions-item>
          <el-descriptions-item label="意图类型">{{ detail.task.intent_type || '-' }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ detail.task.scenario || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 原始回答 -->
      <el-card class="section">
        <template #header>
          <div class="card-head">
            <b>💬 原始采集回答</b>
            <div>
              <el-button type="success" size="small" :loading="collecting" @click="doCollect">
                🤖 调 Kimi 拿真实回答
              </el-button>
              <el-tag v-if="detail.records.length === 0" type="danger" style="margin-left:8px">无采集记录</el-tag>
              <el-tag v-else type="success" style="margin-left:8px">{{ detail.records.length }} 条</el-tag>
            </div>
          </div>
        </template>
        <el-collapse v-if="detail.records.length" v-model="activeRecords">
          <el-collapse-item v-for="(r, idx) in detail.records" :key="r.record_id" :name="idx" :title="`采集 #${idx+1} · ${r.record_id} · ${fmtTime(r.collected_at)}`">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="采集源URL">
                <a v-if="r.source_url" :href="r.source_url" target="_blank">{{ r.source_url }}</a>
                <span v-else class="muted">（无）</span>
              </el-descriptions-item>
              <el-descriptions-item label="原始回答">
                <pre class="raw-answer">{{ r.raw_answer }}</pre>
              </el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="该任务没有原始回答" />
      </el-card>

      <!-- 分析结果 -->
      <el-card class="section">
        <template #header>
          <div class="card-head">
            <b>🧠 AI 结构化分析结果</b>
            <el-button type="primary" size="small" :loading="analyzing" @click="doAnalyze" :disabled="detail.records.length === 0">
              <el-icon><Refresh /></el-icon> 重新 AI 解析
            </el-button>
          </div>
        </template>
        <el-alert v-if="detail.results.length === 0" type="warning" :closable="false"
          title="该任务暂无分析结果，可以点右上角「重新 AI 解析」按钮触发 Mock 分析。" />
        <div v-for="ar in detail.results" :key="ar.result_id" class="result-item">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="分析结果" :span="2">
              {{ ar.result_id }}
              <el-tag size="small" style="margin-left:8px" :type="tagType(ar.is_valid)">{{ ar.is_valid ? '有效' : '无效' }}</el-tag>
              <el-tag size="small" style="margin-left:4px" type="info">{{ SOURCE_MAP[ar.analysis_source] || ar.analysis_source }}</el-tag>
              <span class="muted" style="margin-left:8px">分析时间: {{ fmtTime(ar.analyzed_at) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="品牌提及">
              <el-tag :type="ar.brand_mentioned === 'yes' ? 'success' : ar.brand_mentioned === 'partial' ? 'warning' : 'info'" size="small">
                {{ BRAND_MAP[ar.brand_mentioned] || ar.brand_mentioned }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="推荐态度">
              <el-tag :type="ar.recommended === 'yes' ? 'success' : ar.recommended === 'no' ? 'danger' : 'info'" size="small">
                {{ REC_MAP[ar.recommended] || ar.recommended }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分析摘要" :span="2">{{ ar.summary }}</el-descriptions-item>
            <el-descriptions-item label="证据原文" :span="2">
              <div class="evidence">{{ ar.evidence_text }}</div>
              <div v-if="ar.validation_note" class="muted" style="margin-top:4px">✓ {{ ar.validation_note }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="引用链接" :span="2">
              <div v-if="ar.citation_urls?.length">
                <a v-for="u in ar.citation_urls" :key="u" :href="u" target="_blank" style="display:block">{{ u }}</a>
              </div>
              <span v-else class="muted">（无）</span>
            </el-descriptions-item>
            <el-descriptions-item label="风险标签" :span="2">
              <el-tag v-for="f in (ar.risk_flags || [])" :key="f" type="warning" size="small" style="margin:2px">{{ RISK_MAP[f] || f }}</el-tag>
              <span v-if="!ar.risk_flags?.length" class="muted">无风险</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>

      <!-- 执行历史 -->
      <el-card class="section">
        <template #header><b>⏱ 执行历史（Attempts）</b></template>
        <el-table :data="detail.attempts" stripe border>
          <el-table-column prop="attempt_id" label="Attempt ID" width="180" />
          <el-table-column prop="attempt_no" label="#No" width="70" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="attemptStatusType(row.status)">{{ ATT_STATUS_MAP[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.started_at) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.finished_at) }}</template>
          </el-table-column>
          <el-table-column prop="error_reason" label="失败原因" />
          <el-table-column prop="error_code" label="错误码" width="120" />
        </el-table>
      </el-card>

      <!-- 异常 findings -->
      <el-card v-if="detail.findings?.length" class="section">
        <template #header><b>⚠️ 巡检异常（{{ detail.findings.length }}）</b></template>
        <el-table :data="detail.findings" border>
          <el-table-column label="类型" width="180">
            <template #default="{ row }">
              <el-tag :type="row.resolved ? 'success' : 'danger'">{{ row.anomaly_type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" />
          <el-table-column label="发现时间" width="180">
            <template #default="{ row }">{{ fmtTime(row.detected_at) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.resolved ? 'success' : 'warning'" size="small">{{ row.resolved ? '已修复' : '未修复' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTask, analyzeTask, collectRealAnswer } from '../api'
import { fmtTaskKey, fmtTime } from '../utils/format'

const route = useRoute(); const router = useRouter()
const detail = ref(null); const loading = ref(false); const analyzing = ref(false); const collecting = ref(false)
const activeRecords = ref([0])

async function load() {
  loading.value = true
  try {
    const res = await getTask(route.params.taskKey)
    detail.value = res.data
  } finally { loading.value = false }
}
async function doCollect() {
  collecting.value = true
  try {
    const res = await collectRealAnswer(route.params.taskKey)
    if (res.ok) {
      ElMessage.success(`✅ Kimi 返回了 ${res.raw_answer?.length || 0} 字回答！source=${res.source}`)
      await load()
      // 自动展开最后一条（最新采集的）
      activeRecords.value = [detail.value.records.length - 1]
    }
  } catch (e) {
    ElMessage.error(e?.error || '调 Kimi 失败')
  } finally { collecting.value = false }
}
async function doAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzeTask(route.params.taskKey)
    if (res.ok) {
      ElMessage.success(`分析完成！result_id=${res.result.result_id}`)
      await load()
    }
  } catch (e) {
    ElMessage.error(e?.error || '分析失败')
  } finally { analyzing.value = false }
}
function goBack() { router.push({ name: 'TaskList' }) }

function tagType(valid) { return valid ? 'success' : 'danger' }
function attemptStatusType(s) {
  const map = { completed: 'success', failed: 'danger', timeout: 'danger', running: 'warning', queued: 'info' }
  return map[s] || 'info'
}

// === 中文映射 ===
const BRAND_MAP = { yes: '明确提及', partial: '间接提及', no: '未提及' }
const REC_MAP = { yes: '推荐', no: '不推荐', neutral: '中性' }
const SOURCE_MAP = {
  'ark-direct': 'Ark 直连',
  'mock-fallback': 'Mock 降级',
  'mock': 'Mock',
  'seed_mock': '种子 Mock',
}
const RISK_MAP = {
  simulated_answer: '模拟回答',
  lacks_specifics: '缺乏具体信息',
  no_citation: '无引用来源',
  conditional_recommendation: '有条件推荐',
  negative_sentiment: '负面/保留意见',
  brand_mentioned_not_recommended: '提及品牌但未推荐',
  no_major_risks: '无明显风险',
  citation_not_in_source: '引用与来源不符',
}
const ATT_STATUS_MAP = { completed: '完成', failed: '失败', timeout: '超时', running: '运行中', queued: '排队中' }

onMounted(load)
</script>

<style scoped>
.page-header { margin-bottom: 12px; }
.section { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.result-item { margin-bottom: 12px; }
.result-item:last-child { margin-bottom: 0; }
.raw-answer { background: #f5f7fa; padding: 10px; border-radius: 4px; white-space: pre-wrap; font-size: 13px; margin: 0; max-height: 280px; overflow: auto; }
.evidence { background: #ecf5ff; border-left: 3px solid #409eff; padding: 8px 10px; border-radius: 2px; font-size: 13px; }
.muted { color: #909399; font-size: 12px; }
</style>
