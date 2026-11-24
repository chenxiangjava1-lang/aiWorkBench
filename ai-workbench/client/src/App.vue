<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo">🤖 AI 数据采集与分析工作台</div>
    </el-header>
    <el-container>
      <el-aside width="200px" class="aside">
        <el-menu
          :default-active="$route.path"
          router
          background-color="#1f2d3d"
          text-color="#b0c4de"
          active-text-color="#ffd04b"
        >
          <el-menu-item index="/dashboard"><el-icon><DataAnalysis /></el-icon><span>总览仪表盘</span></el-menu-item>
          <el-menu-item index="/tasks"><el-icon><List /></el-icon><span>任务列表</span></el-menu-item>
          <el-menu-item index="/audit"><el-icon><Warning /></el-icon><span>巡检与修复</span></el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.layout { height: 100vh; }
.header { background: #1f2d3d; color: #fff; display: flex; align-items: center; padding: 0 20px; }
.logo { font-size: 18px; font-weight: 600; }
.aside { background: #1f2d3d; }
.aside .el-menu { border-right: none; }
.main { background: #f5f7fa; padding: 16px; overflow-y: auto; }
</style>
