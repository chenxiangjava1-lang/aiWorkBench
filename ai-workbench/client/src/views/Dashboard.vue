<template>
  <div class="dashboard">
    <!-- 顶部操作栏 -->
    <el-card class="top-bar">
      <div class="filters">
        <el-select v-model="filters.brand_id" placeholder="全部品牌" clearable style="width:160px">
          <el-option v-for="b in brands" :key="b.brand_id" :label="b.brand_name" :value="b.brand_id" />
        </el-select>
        <el-select v-model="filters.platform" placeholder="全部平台" clearable style="width:140px">
          <el-option v-for="p in platforms" :key="p.platform" :label="p.platform" :value="p.platform" />
        </el-select>
        <el-select v-model="filters.business_date" placeholder="全部日期" clearable style="width:150px">
          <el-option v-for="d in dates" :key="d.business_date" :label="d.business_date" :value="d.business_date" />
        </el-select>
        <el-button type="primary" @click="loadOverview">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="actions">
        <input ref="fileInput" type="file" accept=".json" style="display:none" @change="onFileSelected" />
        <el-button type="primary" plain :loading="loadingSeed" @click="doImport">
          <el-icon><Refresh /></el-icon> 一键导入默认数据
        </el-button>
        <el-button type="warning" :loading="loadingUpload" @click="$refs.fileInput.click()">
          <el-icon><UploadFilled /></el-icon> 选择文件导入
        </el-button>
        <el-tag type="info">后端：localhost:3000</el-tag>
      </div>
    </el-card>

    <!-- 指标卡片 -->
    <el-row :gutter="16" class="metrics">
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-num">{{ data.expected }}</div>
          <div class="metric-label">应有任务数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card success">
          <div class="metric-num">{{ data.valid }}</div>
          <div class="metric-label">有效完成</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card warning">
          <div class="metric-num">{{ data.anomaly }}</div>
          <div class="metric-label">异常任务数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card primary">
          <div class="metric-num">{{ data.completion_rate }}%</div>
          <div class="metric-label">完成率</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-num">{{ data.mention_rate }}%</div>
          <div class="metric-label">提及率</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-num">{{ data.recommend_rate }}%</div>
          <div class="metric-label">推荐率</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 过程指标 -->
    <el-card class="progress-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务生命周期（T 日采集 → T+1 分析 → T+2 交付）</span>
        </div>
      </template>
      <div class="progress-grid">
        <div class="progress-item">
          <div class="label">已执行</div>
          <el-progress :percentage="pct(data.attempted, data.expected)" :stroke-width="18" />
          <div class="sub">{{ data.attempted }} / {{ data.expected }}</div>
        </div>
        <div class="progress-item">
          <div class="label">已采集</div>
          <el-progress :percentage="pct(data.collected, data.expected)" :stroke-width="18" color="#e6a23c" />
          <div class="sub">{{ data.collected }} / {{ data.expected }}</div>
        </div>
        <div class="progress-item">
          <div class="label">已分析</div>
          <el-progress :percentage="pct(data.analyzed, data.expected)" :stroke-width="18" color="#f56c6c" />
          <div class="sub">{{ data.analyzed }} / {{ data.expected }}</div>
        </div>
        <div class="progress-item">
          <div class="label">有效完成</div>
          <el-progress :percentage="pct(data.valid, data.expected)" :stroke-width="18" color="#67c23a" />
          <div class="sub">{{ data.valid }} / {{ data.expected }}</div>
        </div>
      </div>
    </el-card>

    <el-alert class="note" type="info" :closable="false" show-icon
      title="提示"
      description="先点右上角「加载测试数据」一键导入 seed_all.json，然后可以在任务列表里查看详情，或到巡检页面运行异常巡检。所有操作都调用后端 API，刷新页面数据依然存在。"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { overview, getFilters, seed, uploadSeed } from '../api'

const data = reactive({
  expected: 0, valid: 0, attempted: 0, collected: 0, analyzed: 0,
  anomaly: 0, completion_rate: 0, mention_rate: 0, recommend_rate: 0,
})
const filters = reactive({ brand_id: '', platform: '', business_date: '' })
const brands = ref([]); const platforms = ref([]); const dates = ref([])
const loadingSeed = ref(false); const loadingUpload = ref(false)
const fileInput = ref(null)

const pct = (a, b) => b > 0 ? Math.round(a / b * 100) : 0

async function loadOverview() {
  const res = await overview({ ...filters })
  Object.assign(data, res.data)
}
async function loadFilters() {
  const res = await getFilters()
  brands.value = res.brands; platforms.value = res.platforms; dates.value = res.dates
}
function resetFilters() {
  Object.assign(filters, { brand_id: '', platform: '', business_date: '' })
  loadOverview()
}
async function doImport() {
  loadingSeed.value = true
  try {
    const res = await seed.import()
    if (res.ok) {
      ElMessage.success(`导入成功！connectors=${res.stats.connectors}, tasks=${res.stats.expected_tasks}, results=${res.stats.analysis_results}`)
      await loadFilters()
      await loadOverview()
    }
  } finally { loadingSeed.value = false }
}

async function onFileSelected(e) {
  const file = e.target.files?.[0]
  if (!file) return
  loadingUpload.value = true
  try {
    const res = await uploadSeed(file)
    if (res.ok) {
      ElMessage.success(`从「${res.file_name || file.name}」导入成功！tasks=${res.stats.expected_tasks}, results=${res.stats.analysis_results}`)
      await loadFilters()
      await loadOverview()
    }
  } catch (err) {
    ElMessage.error(err?.error || '导入失败')
  } finally {
    loadingUpload.value = false
    e.target.value = ''  // 清空以便下次选同名文件
  }
}

onMounted(async () => {
  await loadFilters()
  await loadOverview()
})
</script>

<style scoped>
.top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 10px; align-items: center; }
.metrics { margin-bottom: 16px; }
.metric-card { text-align: center; }
.metric-card.success .metric-num { color: #67c23a; }
.metric-card.warning .metric-num { color: #e6a23c; }
.metric-card.primary .metric-num { color: #409eff; }
.metric-num { font-size: 30px; font-weight: 700; color: #303133; }
.metric-label { font-size: 13px; color: #909399; margin-top: 4px; }
.progress-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; padding: 12px 0; }
.progress-item .label { font-size: 14px; font-weight: 500; margin-bottom: 6px; color: #606266; }
.progress-item .sub { font-size: 12px; color: #909399; margin-top: 4px; }
.note { margin-top: 16px; }
.card-header { font-weight: 600; }
</style>
