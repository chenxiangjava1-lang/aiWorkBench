import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.response.use(
  r => r.data,
  err => Promise.reject(err?.response?.data || err.message)
)

// 种子数据
export const seed = {
  import: () => http.post('/seed/import', { use_default: true }),
  reset: () => http.post('/seed/reset'),
}

// 上传 JSON 文件
export function uploadSeed(file) {
  const fd = new FormData()
  fd.append('file', file)
  return http.post('/seed/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000,
  })
}

// 总览
export const overview = (params = {}) => http.get('/dashboard/overview', { params })

// 筛选选项
export const getFilters = () => http.get('/filters')

// 任务列表
export const listTasks = (params) => http.get('/tasks', { params })

// 新增任务
export const createTask = (payload) => http.post('/tasks', payload)

// 任务详情（用 query param 避免 | 在 path 里被 Tomcat 拦）
export const getTask = (taskKey) => http.get('/tasks/detail', { params: { task_key: taskKey } })

// AI 分析（同上）
export const analyzeTask = (taskKey) => http.post('/analyze', null, { params: { task_key: taskKey }, timeout: 120000 })

// 巡检
export const runAudit = () => http.post('/audit/run')
export const getFindings = (params = {}) => http.get('/audit/findings', { params })

// 修复（内部会调 Kimi analyze，需要时间）
export const repairTask = (taskKey) => http.post('/repair', null, {
  params: { task_key: taskKey },
  timeout: 120000,
})

// 🤖 调 Kimi 拿真实回答
export const collectRealAnswer = (taskKey) => http.post('/collect/real-answer', null, {
  params: { task_key: taskKey },
  timeout: 60000, // Kimi 推理需要时间
})
