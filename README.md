# AI 数据采集与分析工作台

对 AI 平台回答进行**实时采集**、**结构化分析**与**质量巡检**

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Spring Data JPA · MySQL 8 · JDK 17 · RestTemplate |
| 前端 | Vue 3 · Element Plus · Vite 5 |
| LLM | 火山引擎 Ark（kimi-k3-1-pro-261024 推理模型） |
| 数据库 | MySQL Commercial 26.7.0（root / 123456） |

## 目录结构

```
F:\aiProject\1\
├── start.ps1                     # 一键启动（MySQL + Spring Boot + Vue）
├── stop.ps1                      # 一键停止（kill java + node）
├── clean_all.sql                 # 清空所有表数据
├── ai-workbench-java\            # 后端
│   ├── src\main\java\com\aiworkbench\
│   │   ├── config\               # CorsConfig / TimeUtil
│   │   ├── controller\           # ApiController / DashboardController / ActionController
│   │   ├── entity\               # 9 个 JPA 实体
│   │   ├── repository\           # 9 个 Repo
│   │   └── service\              # AnalyzeService / AuditService / RepairService / SeedService
│   └── src\main\resources\
│       ├── application.yml       # MySQL / Jackson SNAKE_CASE / Ark 配置
│       └── logback.xml           # UTF-8 日志
└── ai-workbench\
    └── client\                   # 前端 Vue 3
        ├── src\views\
        │   ├── Dashboard.vue     # 概览（指标卡 + 筛选 + 种子导入）
        │   ├── TaskList.vue      # 任务列表
        │   ├── TaskDetail.vue    # 任务详情（采集记录 / AI 分析 / 执行历史 / 巡检）
        │   └── Audit.vue         # 巡检中心
        ├── src\api\index.js      # axios 封装
        ├── src\utils\format.js   # 时间 / task_key 格式化
        └── vite.config.js        # 代理 /api → :8080
```

## 快速启动

### 1. 确保 MySQL 在跑

```powershell
# Windows Service
Get-Service MySQLWorkbench | Start-Service
# 或直接启动
& "F:\aiSoft\mysql-commercial-26.7.0-winx64\bin\mysqld.exe"
```

### 2. 一键启动三服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\aiProject\1\start.ps1
```

```
[0/4] kill old processes...              ✅
[1/4] MySQL (Windows Service)...         ✅ PID 3260
[2/4] Spring Boot (port 8080)...         ✅ PID 2172  (WMI detached)
[3/4] Vue frontend (port 5173)...        ✅ PID 13132 (WMI detached)
[4/4] verify...
  Health ok:   True
  Dashboard:   http://localhost:5173
```

### 3. 一键停止

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\aiProject\1\stop.ps1
```

## 手动重启 Spring Boot

```powershell
# 杀旧进程
taskkill /F /IM java.exe; Start-Sleep 2

# 设环境 + 编译
$env:JAVA_HOME = "F:\aiSoft\jdk-17.0.20.1+1"
$env:PATH = "$($env:JAVA_HOME)\bin;F:\aiSoft\apache-maven-3.9.16\bin;$env:PATH"
cd F:\aiProject\1\ai-workbench-java
& mvn.cmd package -DskipTests -q

# WMI 后台启动（IDE 杀不掉）
$JDK = "F:\aiSoft\jdk-17.0.20.1+1"
$BACKEND = "F:\aiProject\1\ai-workbench-java"
$jarPath = Join-Path $BACKEND "target\ai-workbench-1.0.0.jar"
([WMICLASS]"\\.\ROOT\CIMV2:Win32_Process").Create("`"$JDK\bin\java.exe`" -jar `"$jarPath`"", $BACKEND, $null)
```

## 种子数据

### 默认种子（内置 jar）

```powershell
Invoke-RestMethod -Method POST "http://localhost:8080/api/seed/import?use_default=true"
```

### 自定义文件

前端 Dashboard → "选择种子文件" → `seed_all.json`（JSON 数组格式）

### 清空所有数据

DBeaver / MySQL 客户端执行 `clean_all.sql`，或：

```powershell
& "F:\aiSoft\mysql-commercial-26.7.0-winx64\bin\mysql.exe" -u root -p123456 < "F:\aiProject\1\clean_all.sql"
```

## 核心 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查（含 db_ok） |
| GET | `/api/dashboard/overview` | 概览指标（expected / valid / completion_rate） |
| GET | `/api/filters` | 品牌 / 平台 / 日期下拉数据 |
| GET | `/api/tasks` | 任务列表分页 |
| GET | `/api/tasks/detail?task_key=xxx` | 任务详情（含 records / attempts / findings） |
| POST | `/api/tasks` | 新建任务 |
| POST | `/api/collect/real-answer?task_key=xxx` | 调 Kimi 真采集（60s timeout） |
| POST | `/api/analyze?task_key=xxx` | Kimi AI 分析 raw_answer（120s timeout） |
| POST | `/api/audit/run` | 全量巡检（清空旧 findings 重建） |
| GET | `/api/audit/findings` | 巡检异常列表 |
| POST | `/api/repair?task_key=xxx` | 单任务修复（补 attempt + 重分析 + 清场） |
| POST | `/api/seed/import` | 默认种子导入 |
| POST | `/api/seed/upload` | 上传 JSON 种子文件 |

## 数据模型

### 表结构

| 表 | 说明 | 主键 |
|---|---|---|
| connectors | AI 平台接入配置 | connector_id |
| assets | 品牌 / 产品资产 | asset_id |
| intents | 监测意图 / 问题模板 | intent_id |
| **expected_tasks** | **核心：预期监测任务** | task_key（`asset_id\|platform\|business_date`） |
| **collection_records** | **AI 原始回答** | record_id |
| **analysis_results** | **结构化分析结果** | result_id |
| task_attempts | 执行尝试记录 | attempt_id |
| audit_findings | 巡检发现 | finding_id AUTO_INCREMENT |
| metric_snapshots | 仪表盘快照 | snapshot_id |

### ER 关系

```
connectors ──1:N──▶ collection_records ◀──N:1── expected_tasks ◀──N:1── assets
                          │                              │        │
                          ▼                              ▼        ▼
                    analysis_results                task_attempts  intents
                                                          │
                                                          ▼
                                                   audit_findings
```

### 关键字段说明

**task_key（业务唯一键）**：`B003|Kimi|2026-11-23` = 资产 ID + 平台 + 监测日期，三列组合天然唯一。

**risk_flags（风险标签）**：JSON 数组，至少 1 条
- `simulated_answer` 模拟回答（非真实采集）
- `conditional_recommendation` 有条件推荐（"值得考虑"类）
- `lacks_specifics` 缺乏具体型号 / 参数 / 实测数据
- `no_citation` 无引用来源
- `negative_sentiment` 负面 / 保留意见
- `brand_mentioned_not_recommended` 提及品牌但未推荐
- `no_major_risks` 无明显风险

### 数据流

```
种子导入 SeedService
  │
  └─ 145 条 expected_tasks + 140 task_attempts + 123 collection_records + 106 analysis_results

前端"新增任务"
  → DashboardController.createTask
  → 自动生成 mock 回答 + attempt + collection_record

"🤖 调 Kimi 拿真实回答"（AnalyzeService.collectRealAnswer）
  → POST Ark /chat/completions（prompt 要求真实回答 + 附参考链接）
  → 正则提取回答中的 URL → source_url 用第一个真实链接
  → 存 collection_record

"🤖 重新 AI 解析"（AnalyzeService.analyze）
  → 把 raw_answer 塞 prompt → Kimi 输出 {summary, brand_mentioned, recommended, evidence_text, citation_urls, risk_flags}
  → evidence 必须是 raw_answer 的原文子串（可追溯校验）
  → 每次新建 attempt（attempt_no+1）
  → 旧 analysis_results 先删再存

巡检（AuditService）
  → 遍历所有 expected_tasks
  → 检查：缺 attempt / 缺 record / 缺 result / attempt 超时 / 重复执行 / 证据不可追溯 / 汇总不一致
  → 全量清空 audit_findings 后重建

修复（RepairService）
  → 查 task 是否存在
  → 补 attempt（有就更新 status）
  → 补 record（没就补 mock）
  → 重新调 analyze → **新建 attempt**
  → **清场**：删掉所有旧 attempt，只留 analyze 刚新建的最新 1 条
  → 标记所有 finding resolved
```

## 巡检异常类型

| 类型 | 中文 | 含义 |
|---|---|---|
| `missing_task` | 缺失执行记录 | expected_tasks 有但 attempts 为空 |
| `collection_failed` | 采集失败 | 有 attempt 但无 record |
| `analysis_failed` | 分析失败 | 有 record 但无 result |
| `running_stale` | 运行超时 | attempt 状态=running 且 >15min |
| `queued_stale` | 排队超时 | attempt 状态=queued 且 >30min |
| `duplicate_attempt` | 重复执行 | 同一任务 ≥2 条 completed attempt |
| `invalid_evidence` | 证据不可追溯 | evidence_text 在 raw_answer 中找不到 |
| `aggregate_inconsistency` | 汇总数据不一致 | metric_snapshot 与实际 results 数对不上 |

## LLM 配置（火山引擎 Ark）

后端 `application.yml`：

```yaml
llm:
  endpoint: https://ark.cn-beijing.volces.com/api/v3
  api-key: ${LLM_API_KEY:}
  model: kimi-k3-1-pro-261024
  timeout-ms: 15000

spring.ai.openai:
  base-url: ${LLM_ENDPOINT:https://ark.cn-beijing.volces.com/api/v3}
  api-key: ${LLM_API_KEY:}
  chat.options.model: kimi-k3-1-pro-261024
  chat.options.temperature: 1.0  # kimi-k3 只允许 1.0
```

### LLM 路由

```
Spring AI ChatClient → callArkDirect (RestTemplate 直连 Ark) → Mock 降级
                  ✘                   ✅ 主路                    ✅ 兜底
```

**为什么跳过 Spring AI？** OpenAiApi 内部拼 `/v1/chat/completions`，配 `base-url: .../api/v3` 后变成 `/v3/v1/chat/completions` → 404。所以用 RestTemplate 直连 `/api/v3/chat/completions`。

### kimi-k3-1-pro 特性

- 推理模型：返回 `reasoning_content`（思维链）+ `content`（最终输出）
- analyze prompt 约 1200 tokens，思维链可能花 ~1800 tokens → **max_tokens 设 8000**
- content 空时 fallback 从 reasoning_content 提取 JSON 块
- 安全机制拒绝"冒充某平台" → prompt 改成"请直接回答用户问题"

## 常见坑

| 问题 | 根因 | 修 |
|---|---|---|
| IDE 管理下 java 进程被杀 | `Start-Process` 挂在 IDE shell 下 | **WMI Win32_Process.Create**（挂 wmiprvse 下） |
| Spring Boot health timeout | WMI 启动没设 JAVA_HOME | 启动前 `$env:JAVA_HOME = $JDK` |
| Ark 路径 404 | base-url `/api/v3` + Spring AI 内部 `/v1/...` → 双重前缀 | **跳过 Spring AI，RestTemplate 直连** `/api/v3/chat/completions` |
| Jackson 输出驼峰 | 实体用了 `rawAnswer`，前端模板写 `raw_answer` | `property-naming-strategy: SNAKE_CASE` |
| logback 日志乱码 | 默认 GBK | `logback.xml` 加 `<charset>UTF-8</charset>` |
| Kimi analyze 空 content | reasoning 花完 2000 tokens | **max_tokens 改成 8000** + content 空时 fallback reasoning |
| Kimi 拒绝冒充 ChatGPT | 安全机制 | prompt 改成"直接回答问题，不自我介绍" |
| 前端 analyze 15s 超时 | 全局 default timeout | `analyzeTask / repairTask / collectRealAnswer` 各自加 `timeout: 120000` |
| PowerShell 5 不支持 `&&` | 语法限制 | 每条命令单独执行 |
| Maven 编译报 BOM | `Set-Content -Encoding UTF8` 自动加 BOM | 用 `[IO.File]::WriteAllText($f, $c, [Text.UTF8Encoding]::new($false))` |
| `|` 在 URL 里被 Tomcat 拦 | Tomcat 10 strict | `server.tomcat.relaxed-path-chars: [\|]` |
| 前端 task_key 显示 `B003\|ChatGPT\|...` | 后端存的是 ISO 格式带竖线 | 前端 fmtTaskKey 原样显示（用户确认要保留竖线） |
| 时间戳 `2026-11-23T05:20:55.305327900Z` | `Instant.now().toString()` | 统一用 `TimeUtil.now()` → `yyyy-MM-dd HH:mm:ss` |

## 前端 Vite 代理

```js
// vite.config.js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  }
}
```

## DBeaver 连接

| 字段 | 值 |
|---|---|
| Host | localhost |
| Port | 3306 |
| Database | ai_workbench |
| Username | root |
| Password | 123456 |
| Driver | MySQL 8.x |
