# zyagent

zyagent 是一个面向 Java 后端求职与个人知识成长的 AI Agent 平台。项目围绕“资料沉淀、知识检索、岗位分析、简历优化、模拟面试、复盘补强”构建完整闭环，适合作为 Spring AI + RAG + Agent 工程化实践项目。

```text
学习资料 / 简历 / 项目文档 / JD
        -> 文档解析与知识库沉淀
        -> RAG 检索与 Agent 工具调用
        -> 岗位匹配 / 简历建议 / 面试追问 / 学习计划
        -> 对话记忆与复盘补强
```

## 项目亮点

- **Spring AI DeepSeek 原生接入**：使用 Spring AI DeepSeek starter，支持同步回答和 SSE 流式输出。
- **本地 fallback**：未配置 `DEEPSEEK_API_KEY` 时仍可返回本地演示回答，保证项目可启动、可演示。
- **RAG 知识库**：支持文件上传、Apache Tika 文本解析、文档切片、MySQL 元数据存储、Milvus 向量检索。
- **Agent 工具调用**：把知识库搜索、JD 解析、简历建议、面试题生成、学习计划生成等能力封装为工具。
- **Chat Memory**：基于已有会话和消息表，为同一 `sessionId` 注入最近多轮上下文。
- **Advisors 思路分层**：统一处理 RAG 上下文、对话记忆、工具调用日志、耗时/token 记录等横切能力。
- **结构化输出**：对学习计划、岗位分析、面试评分等场景提供 DTO/record 解析与 fallback。
- **MCP Server**：接入 Spring AI MCP Server WebMVC starter，预留标准 MCP tools 暴露能力。
- **Plan-Execute-Replan**：Agent 计划步骤支持状态、工具名、错误信息、耗时和重规划展示。
- **岗位自动采集**：支持每日定时采集、搜索 URL 模板、列表页进入详情页二次抓取、采集日志。
- **前端完整交互**：Vue 3 + Element Plus 实现工作台、知识库、智能对话、岗位中心、简历匹配、模拟面试和复盘报告。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.15、Spring MVC、JDBC |
| AI | Spring AI 1.1.8、DeepSeek Chat、ChatClient、Tool Calling、MCP Server |
| RAG | Apache Tika、TextChunker、EmbeddingService、Milvus、MySQL fallback |
| 存储 | MySQL 8、Redis 7、Milvus 2.4 |
| 岗位采集 | Jsoup、搜索页模板、详情页二次抓取、定时任务 |
| 前端 | Vue 3、Vite、Element Plus |
| 测试 | JUnit 5、Node test |

## 功能模块

### 1. 智能对话

- `POST /api/chat/complete`：同步对话。
- `POST /api/chat/stream`：SSE 流式对话。
- SSE 事件保持为 `skill`、`plan`、`tools`、`references`、`message`。
- 支持会话列表、历史消息、删除对话、工具调用轨迹、RAG 引用来源展示。

### 2. 知识库

- 上传简历、项目文档、学习资料、岗位 JD、复盘记录。
- Apache Tika 解析 PDF、Word、Markdown、TXT 等文件。
- 文档切片后写入 MySQL，并尝试写入 Milvus 向量库。
- 删除文档时同步删除 MySQL 文档、chunk，并调用 Milvus 按 `document_id` 删除向量。
- 检索降级链路：
  - `milvus`：Milvus 语义检索。
  - `keyword_fallback`：MySQL 关键词检索。
  - `memory_fallback`：内存检索。

### 3. Agent 工具

首批工具包括：

- `search_personal_knowledge`：搜索个人知识库。
- `parse_job_description`：解析岗位 JD。
- `match_resume_job`：匹配简历与岗位。
- `generate_interview_questions`：生成面试问题。
- `generate_resume_suggestion`：生成简历优化建议。

这些工具同时服务于聊天 Agent、前端 ToolTrace 和 MCP Server 暴露。

### 4. 岗位中心

- 手动导入 JD。
- 每日定时采集公开招聘页面。
- 支持单个来源采集和全部来源采集。
- 支持采集源配置：
  - `STATIC_HTML`：静态页面抓取。
  - `SEARCH_PAGE`：按 `{keyword}`、`{page}`、`{city}` 渲染搜索 URL。
  - 列表项 CSS 选择器。
  - 详情链接 CSS 选择器。
  - 是否进入详情页。
  - 每个来源详情页抓取上限。
- 采集日志展示新增、更新、跳过、失败、候选数、详情抓取数和失败原因。

### 5. 简历匹配、模拟面试、复盘

- 根据岗位 JD 与简历内容生成匹配报告。
- 基于岗位和个人资料生成模拟面试问题。
- 根据面试复盘内容生成补强计划。
- 相关结果支持自然语言总结和结构化输出解析。

## 目录结构

```text
ZYagent
├─ backend
│  ├─ src/main/java/com/zyagent
│  │  ├─ agent          # Agent 编排、计划、重规划
│  │  ├─ ai             # Spring AI DeepSeek 适配
│  │  ├─ chat           # 对话接口、SSE
│  │  ├─ document       # 文档上传、解析、检索
│  │  ├─ job            # 岗位导入、采集、解析
│  │  ├─ storage        # MySQL Repository 与表初始化
│  │  ├─ tool           # 工具定义与 Tool Calling 适配
│  │  ├─ vector         # Embedding 与 Milvus VectorStore
│  │  └─ structured     # 结构化输出 DTO
│  └─ pom.xml
├─ frontend
│  ├─ src
│  │  ├─ App.vue
│  │  ├─ api.js
│  │  ├─ chat
│  │  └─ components
│  └─ package.json
├─ docs
│  ├─ daily-job-collection-plan.md
│  └─ demo-data
├─ docker-compose.yml
├─ .env.example
└─ README.md
```

## 环境要求

- JDK 17+，当前项目已验证 Java 21 可运行。
- Node.js 18+。
- Docker Desktop，可选但推荐用于启动 MySQL、Redis、Milvus。
- DeepSeek API Key，可选；不配置时走本地 fallback。

## 配置

复制 `.env.example` 为 `.env`：

```powershell
copy .env.example .env
```

核心配置：

```env
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_CHAT_MODEL=deepseek-v4-pro

MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=zyagent
MYSQL_USERNAME=root
MYSQL_PASSWORD=zyagent

REDIS_HOST=localhost
REDIS_PORT=6379

MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_COLLECTION=zyagent_documents

EMBEDDING_PROVIDER=hash
EMBEDDING_DIMENSION=128

JOB_COLLECT_ENABLED=true
JOB_COLLECT_CRON=0 0 8 * * ?
JOB_COLLECT_TIMEOUT_SECONDS=8
JOB_COLLECT_MAX_PAGES_PER_SOURCE=3
JOB_COLLECT_KEYWORDS=Java,后端,Spring Boot,Redis,MySQL,校招,实习,agent,大模型

BOSS_COLLECT_ENABLED=true
BOSS_COLLECT_CITY=北京
BOSS_COLLECT_KEYWORDS=Java 后端 实习,AI Agent 后端 实习
BOSS_COLLECT_MAX_PAGES=3
BOSS_COLLECT_MAX_JOBS=60
BOSS_BROWSER_PROFILE_DIR=./data/browser/boss-profile
BOSS_BROWSER_HEADLESS=false

CHAT_MEMORY_MAX_MESSAGES=6
AGENT_REPLAN_MAX_ATTEMPTS=1
ZYAGENT_MCP_ENABLED=true
STRUCTURED_OUTPUT_ENABLED=true
```

说明：

- `DEEPSEEK_API_KEY` 为空时，系统不会中断启动，会使用本地 fallback。
- 当前默认聊天模型是 `deepseek-v4-pro`。DeepSeek 官方仍保留 `deepseek-chat` 兼容名，但该旧名会路由到 `deepseek-v4-flash` 非思考模式，并计划下线；本项目不再默认使用 `deepseek-chat`。
- `EMBEDDING_PROVIDER=hash` 表示使用本地 hash embedding，方便无外部 embedding 服务时演示。
- 如果使用 `docker-compose.yml` 默认 MySQL，`MYSQL_PASSWORD` 应为 `zyagent`。

## 启动基础设施

```powershell
docker compose up -d mysql redis milvus
```

Milvus 依赖 `etcd` 和 `minio`，执行上面的命令时 Docker Compose 会自动启动依赖服务。

## 启动后端

在项目根目录执行：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -f backend\pom.xml spring-boot:run
```

后端地址：

```text
http://127.0.0.1:8080
```

健康检查：

```text
GET http://127.0.0.1:8080/api/tools
```

## 启动前端

```powershell
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

前端地址：

```text
http://127.0.0.1:5173
```

## 一键启动命令

在项目根目录执行：

```powershell
Start-Process ".\.tools\apache-maven-3.9.9\bin\mvn.cmd" "-q -f backend\pom.xml spring-boot:run"
Start-Process "npm.cmd" "run dev -- --host 127.0.0.1" -WorkingDirectory ".\frontend"
```

如果需要查看日志，建议手动分两个终端启动，排查会更直接。

## 常用接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/tools` | 查看工具列表 |
| `GET` | `/api/tools/definitions` | 查看工具定义 |
| `GET` | `/api/chat/sessions` | 查询对话会话 |
| `POST` | `/api/chat/sessions` | 创建对话会话 |
| `DELETE` | `/api/chat/sessions/{sessionId}` | 删除对话 |
| `GET` | `/api/chat/sessions/{sessionId}/messages` | 查询历史消息 |
| `POST` | `/api/chat/complete` | 同步对话 |
| `POST` | `/api/chat/stream` | SSE 流式对话 |
| `POST` | `/api/files/upload` | 上传知识库文件 |
| `GET` | `/api/documents` | 查询文档 |
| `DELETE` | `/api/documents/{documentId}` | 删除文档 |
| `GET` | `/api/jobs` | 查询岗位 |
| `POST` | `/api/jobs/import-text` | 手动导入 JD |
| `POST` | `/api/jobs/import-url` | 从 URL 导入 JD |
| `POST` | `/api/jobs/collect` | 采集全部岗位源 |
| `DELETE` | `/api/jobs/invalid-collected` | 清理无效自动采集岗位 |
| `GET` | `/api/jobs/collect/logs` | 查询采集日志 |
| `GET` | `/api/job-sources` | 查询采集源 |
| `POST` | `/api/job-sources` | 新增采集源 |
| `PUT` | `/api/job-sources/{id}` | 更新采集源 |
| `POST` | `/api/job-sources/{id}/collect` | 采集单个来源 |
| `POST` | `/api/jobs/{jobId}/match-resume` | 简历岗位匹配 |
| `POST` | `/api/interviews/simulate` | 模拟面试 |
| `POST` | `/api/reviews` | 生成复盘报告 |

## 验证命令

后端测试：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -f backend\pom.xml test
```

前端测试：

```powershell
cd frontend
node --test src\chat\chatStream.test.mjs src\chat\uploadActions.test.mjs
```

前端构建：

```powershell
cd frontend
npm run build
```

## 演示流程

1. 启动 MySQL、Redis、Milvus。
2. 启动后端和前端。
3. 进入“知识库”，上传 `docs/demo-data` 中的简历、项目、学习笔记、JD。
4. 进入“智能对话”，提问：

```text
结合我上传的简历和字节 JD，分析我的匹配优势和短板。
```

```text
根据我的项目文档，提炼 3 个 Java 后端面试亮点。
```

```text
请作为面试官，围绕订单项目连续追问。
```

5. 进入“岗位中心”，点击“立即采集全部”，或为单个采集源配置搜索模板后采集。
6. 进入“简历匹配”，选择岗位并生成匹配报告。

## 岗位采集说明

当前采集策略分两类：官网公开页采集和 BOSS 直聘本地浏览器采集。

官网公开页采集遵循低频、公开页面、可配置原则：

- 不绕登录。
- 不处理验证码。
- 不做高频请求。
- 优先支持公开 HTML、搜索 URL 模板和详情页二次抓取。

如果某个招聘站首页是强 JS 应用壳，建议在前端“采集源配置”里设置该站点真实搜索 URL 模板，例如：

```text
https://example.com/jobs/search?keyword={keyword}&page={page}
```

列表页只有岗位入口而没有完整 JD 时，打开“进入详情页”，系统会抓取详情页正文后再解析。

### BOSS 直聘浏览器模式

BOSS 采集使用 Playwright Java 打开本地可视化浏览器。首次使用前建议安装 Chromium 浏览器依赖：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -f backend\pom.xml exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

使用流程：

1. 启动后端和前端。
2. 打开“岗位中心”。
3. 点击“打开 BOSS 登录窗口”。
4. 在打开的浏览器里手动完成 BOSS 登录或验证。
5. 回到岗位中心，点击“采集 BOSS 岗位”。

默认范围：

```text
城市：北京
关键词：Java 后端 实习, AI Agent 后端 实习
页数：3
最多岗位：60
```

说明：

- 项目不保存 BOSS 账号密码。
- 项目不自动处理验证码或风控。
- 登录态保存在 `./data/browser/boss-profile`，用于本机后续复用。
- 如果 BOSS 页面结构变化导致解析失败，采集日志会记录失败原因，不影响手动导入和官网采集。

## 简历项目描述

zyagent 是一个基于 Spring Boot、Spring AI DeepSeek、Milvus、Redis、MySQL 和 Vue 3 构建的个人知识成长与求职 Agent 平台。项目实现了 RAG 知识库、Chat Memory、Tool Calling、结构化输出、MCP Server、Plan-Execute-Replan 和 SSE 流式对话能力，支持学习资料问答、简历优化、岗位 JD 解析、岗位匹配、模拟面试、复盘报告和每日岗位采集。系统通过 MySQL 持久化会话、文档、岗位和工具调用记录，通过 Milvus 提供语义检索，并在前端展示可追溯的工具轨迹和 RAG 引用来源。

## 注意事项

- 真实 DeepSeek 调用需要配置 `DEEPSEEK_API_KEY`。
- Milvus 首次启动较慢，后端启动时可能等待连接初始化。
- 前端 Vite 默认代理后端接口，建议前后端分别使用 `5173` 和 `8080`。
- 如果端口被占用，先关闭旧的 Java 或 Node 进程后再启动。
