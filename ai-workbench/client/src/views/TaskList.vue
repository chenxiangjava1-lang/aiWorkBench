<template>
  <div>
    <!-- 筛选 + 新增按钮 -->
    <el-card class="filter-bar">
      <div class="filters">
        <el-select v-model="filters.brand_id" placeholder="品牌" clearable style="width:150px" @change="load">
          <el-option v-for="b in brands" :key="b.brand_id" :label="b.brand_name" :value="b.brand_id" />
        </el-select>
        <el-select v-model="filters.platform" placeholder="平台" clearable style="width:130px" @change="load">
          <el-option v-for="p in platforms" :key="p.platform" :label="p.platform" :value="p.platform" />
        </el-select>
        <el-select v-model="filters.business_date" placeholder="日期" clearable style="width:150px" @change="load">
          <el-option v-for="d in dates" :key="d.business_date" :label="d.business_date" :value="d.business_date" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索 task_key / question" clearable style="width:260px" @keyup.enter="load" @clear="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" @click="openCreate" style="margin-left:auto">
          <el-icon><Plus /></el-icon> 新增任务
        </el-button>
      </div>
    </el-card>

    <!-- 任务表 -->
    <el-card>
      <el-table :data="filteredRows" stripe border style="width:100%" v-loading="loading">
        <el-table-column label="任务键" prop="task_key" width="260">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.task_key)">{{ fmtTaskKey(row.task_key) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="品牌" prop="brand_name" width="100" />
        <el-table-column label="平台" prop="platform" width="110" />
        <el-table-column label="日期" prop="business_date" width="120" />
        <el-table-column label="提问" prop="question" min-width="280" show-overflow-tooltip />
        <el-table-column label="Attempts" width="90">
          <template #default="{ row }">
            <el-tag :type="row.attempt_count > 1 ? 'warning' : 'info'" size="small">{{ row.attempt_count }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原始回答" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.record_count > 0" type="success" size="small">✓ {{ row.record_count }}</el-tag>
            <el-tag v-else type="danger" size="small">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效分析" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.valid_result_count > 0" type="success" size="small">✓ {{ row.valid_result_count }}</el-tag>
            <el-tag v-else type="danger" size="small">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.open_findings > 0" type="danger" size="small">{{ row.open_findings }}</el-tag>
            <el-tag v-else size="small">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.task_key)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total" :page-size="page_size" :current-page="page"
          @current-change="p => { page = p; load() }"
        />
      </div>
    </el-card>

    <!-- 新增任务弹窗 -->
    <el-dialog v-model="createVisible" title="新增任务" width="520px">
      <el-form :model="createForm" label-width="80px" label-position="top">
        <el-form-item label="品牌 *">
          <el-select v-model="createForm.brand_id" placeholder="选择品牌" style="width:100%">
            <el-option v-for="b in brands" :key="b.brand_id" :label="b.brand_name + ' (' + b.brand_id + ')'" :value="b.brand_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台 *">
          <el-select v-model="createForm.platform" placeholder="选择平台" style="width:100%">
            <el-option v-for="p in platforms" :key="p.platform" :label="p.platform" :value="p.platform" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期 *">
          <el-date-picker v-model="createForm.business_date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="问题 *">
          <el-input v-model="createForm.question" type="textarea" :rows="3" placeholder="输入监测问题..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listTasks, getFilters, createTask } from '../api'
import { fmtTaskKey, fmtTime } from '../utils/format'

const router = useRouter()
const rows = ref([]); const total = ref(0)
const page = ref(1); const page_size = ref(20)
const loading = ref(false); const keyword = ref('')
const brands = ref([]); const platforms = ref([]); const dates = ref([])
const filters = reactive({ brand_id: '', platform: '', business_date: '' })

// 新增弹窗
const createVisible = ref(false); const createLoading = ref(false)
const createForm = reactive({ brand_id: '', platform: '', business_date: '', question: '' })

function openCreate() {
  createForm.brand_id = filters.brand_id || (brands.value[0]?.brand_id || '')
  createForm.platform = filters.platform || (platforms.value[0]?.platform || '')
  createForm.business_date = new Date().toISOString().slice(0, 10)
  createForm.question = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.brand_id || !createForm.platform || !createForm.business_date || !createForm.question) {
    ElMessage.warning('品牌/平台/日期/问题都必填')
    return
  }
  createLoading.value = true
  try {
    const res = await createTask({ ...createForm })
    if (res.ok) {
      ElMessage.success(`任务创建成功: ${res.task_key}`)
      createVisible.value = false
      await load()
    }
  } catch (err) {
    ElMessage.error(err?.error || err?.message || '创建失败')
  } finally { createLoading.value = false }
}

const filteredRows = computed(() => {
  if (!keyword.value) return rows.value
  const kw = keyword.value.toLowerCase()
  return rows.value.filter(r =>
    r.task_key.toLowerCase().includes(kw) ||
    (r.question || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    const res = await listTasks({ page: page.value, page_size: page_size.value, ...filters })
    rows.value = res.rows; total.value = res.total
  } finally { loading.value = false }
}
async function loadFilters() {
  const res = await getFilters()
  brands.value = res.brands; platforms.value = res.platforms; dates.value = res.dates
}
function goDetail(k) { router.push({ name: 'TaskDetail', params: { taskKey: k } }) }

onMounted(async () => { await loadFilters(); await load() })
</script>

<style scoped>
.filter-bar { margin-bottom: 12px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
