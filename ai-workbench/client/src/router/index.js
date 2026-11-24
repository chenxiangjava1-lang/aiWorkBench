import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '总览仪表盘' } },
  { path: '/tasks', name: 'TaskList', component: () => import('../views/TaskList.vue'), meta: { title: '任务列表' } },
  { path: '/tasks/:taskKey', name: 'TaskDetail', component: () => import('../views/TaskDetail.vue'), meta: { title: '任务详情', hideInMenu: true } },
  { path: '/audit', name: 'Audit', component: () => import('../views/Audit.vue'), meta: { title: '巡检与修复' } },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
