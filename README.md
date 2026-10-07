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
- **Multi-Agent Collaboration**：在单次任务内固定协作 `Planner -> Retriever -> Evaluator -> Reviewer`，通过共享 memory 和 `AgentArtifact` 中间结果协议沉淀计划、证据、评估与复核结论。
- **Agent 可观测性**：前端展示路由决策、Plan-Executor、工具调用状态、RAG 命中、token 估算、短期记忆和 multi-agent 协作链路。
- **Agent 任务持久化**：为同步和 SSE 执行创建任务与步骤记录，保存状态、结果、错误和耗时，并提供任务查询 API；MySQL 不可用时保留本地演示降级。
- **Hybrid RAG 与重排边界**：向量和关键词并行召回，使用 RRF 融合去重，并通过可插拔 `Reranker` 接口统一二阶段排序；当前默认使用 `rrf_fallback`，外部 reranker 尚未接入。
- **可恢复多轮面试**：持久化面试会话和轮次，支持回答、评价和动态追问；旧 `/api/interviews/simulate` 接口保持兼容。
- **用户画像建议闭环**：记录技能证据和来源，模型或规则只产生待审核建议，用户确认后才写入技能画像。
- **Markdown 回答渲染**：聊天回答支持标题、列表、粗体、行内代码和代码块渲染，避免直接暴露 `###` 等 Markdown 标记。
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
- SSE 事件包括 `skill`、`route`、`plan`、`tools`、`metrics`、`memory`、`collaboration`、`references`、`message`、`usage`。
- 支持会话列表、历史消息、删除对话、工具调用轨迹、RAG 引用来源、token 估算和短期记忆展示。
- 支持任务路由：简历类、岗位类、面试类、学习计划类、知识问答类、复盘类和通用兜底。
- 支持 multi-agent 协作可视化：`Planner` 负责拆解任务，`Retriever` 负责检索证据，`Evaluator` 负责评估命中率/工具成功率/置信度，`Reviewer` 负责检查最终回答是否引用中间结果。

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

### 4. Multi-Agent Collaboration

v1 采用确定性顺序协作，不额外启动多个进程，子 Agent 以服务和协议层形式协同：

- `PlannerAgent`：根据 `SkillRouter` 的分类和 skill 计划生成任务拆解。
- `RetrieverAgent`：把 RAG 检索结果转成可引用的 `AgentArtifact`。
- `EvaluatorAgent`：统计工具成功率、RAG 命中率、平均分和综合置信度。
- `ReviewerAgent`：检查回答约束，要求最终回答引用 Planner、Retriever、Evaluator、Reviewer 的关键中间结果。
- `SharedAgentMemory`：在本轮任务内共享用户问题、短期记忆、工具结果、RAG 证据和 artifacts。
- `CollaborationTrace`：返回给前端并随历史消息保存，用于恢复协作链路。

### 5. 岗位中心

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

### 6. 简历匹配、模拟面试、复盘

- 根据岗位 JD 与简历内容生成匹配报告。
- 面试会使用所选岗位的 JD 快照、面试类型/难度和本次会话问答历史生成问题；当前不会自动以简历或用户画像为面试依据。
- 根据面试复盘内容生成补强计划。
- 相关结果支持自然语言总结和结构化输出解析。

## 目录结构

```text
ZYagent
├─ backend
│  ├─ src/main/java/com/zyagent
│  │  ├─ agent          # Agent 编排、计划、重规划、多子 Agent 协作、token/metrics
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
│  │  ├─ chat           # SSE 解析、Markdown 渲染、上传快捷动作
│  │  └─ components     # 业务页面与聊天 trace 可视化组件
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
ZYAGENT_TASK_OWNER_ID=local-user
ZYAGENT_TASK_MAX_PLAN_STEPS=12
ZYAGENT_TASK_MAX_TOOL_CALLS=8
ZYAGENT_TASK_TIMEOUT_MS=120000
ZYAGENT_TASK_TOOL_TIMEOUT_MS=15000
ZYAGENT_TASK_MAX_RETRIES=1
ZYAGENT_TASK_STRICT_PERSISTENCE=false
ZYAGENT_RETRIEVAL_VECTOR_TOP_K=20
ZYAGENT_RETRIEVAL_KEYWORD_TOP_K=20
ZYAGENT_RETRIEVAL_FUSE_LIMIT=30
ZYAGENT_RETRIEVAL_RANK_CONSTANT=60
ZYAGENT_RETRIEVAL_RERANK_LIMIT=8
ZYAGENT_RERANK_ENABLED=false
ZYAGENT_RERANK_ENDPOINT=
ZYAGENT_RERANK_TIMEOUT_MS=1500
ZYAGENT_RERANK_MAX_CANDIDATES=30
ZYAGENT_RERANK_MAX_CONTENT_CHARS=600
ZYAGENT_INTERVIEW_PROVIDER=llm
ZYAGENT_INTERVIEW_MAX_TURNS=8
ZYAGENT_INTERVIEW_MAX_FOLLOW_UPS=1
ZYAGENT_INTERVIEW_MODEL_TIMEOUT_MS=8000
ZYAGENT_MCP_ENABLED=true
STRUCTURED_OUTPUT_ENABLED=true
```

说明：

- `DEEPSEEK_API_KEY` 为空时，系统不会中断启动；通用聊天仍可使用本地 fallback，LLM 面试官则会报告不可用，面试评价明确标记为不可用。
- 当前默认聊天模型是 `deepseek-v4-pro`。DeepSeek 官方仍保留 `deepseek-chat` 兼容名，但该旧名会路由到 `deepseek-v4-flash` 非思考模式，并计划下线；本项目不再默认使用 `deepseek-chat`。
- `EMBEDDING_PROVIDER=hash` 表示使用本地 hash embedding，方便无外部 embedding 服务时演示。
- 如果使用 `docker-compose.yml` 默认 MySQL，`MYSQL_PASSWORD` 应为 `zyagent`。
- `ZYAGENT_TASK_*` 控制 Agent 任务的执行上限和归属者：`MAX_PLAN_STEPS`/`MAX_TOOL_CALLS` 限制单次执行的步骤与工具调用数，`TOOL_TIMEOUT_MS` 是单工具超时，`MAX_RETRIES` 只对幂等只读工具生效。`STRICT_PERSISTENCE=false`（默认）时数据库不可用会降级为本地演示并在 SSE `status` 事件中标记 `persistence=memory`；设为 `true` 则任务无法落库时直接报错。
- `ZYAGENT_RETRIEVAL_*` 控制 Hybrid 检索：`VECTOR_TOP_K`/`KEYWORD_TOP_K` 是两路各自召回数（默认 20），`FUSE_LIMIT` 是 RRF 融合后的候选上限（默认 30），`RERANK_LIMIT` 是最终注入提示词的引用数（默认 8）。
- `ZYAGENT_RERANK_*` 是**可插拔**的二阶段重排：默认 `ENABLED=false`，此时使用本地 RRF 顺序并在检索追踪中标注 `rerank=rrf_fallback`（不声称 cross-encoder 已运行）。启用后需配置 `ENDPOINT`（外部 cross-encoder 服务），并受 `TIMEOUT_MS`/`MAX_CANDIDATES`/`MAX_CONTENT_CHARS` 约束；调用超时或异常会**自动降级**为 `rrf_fallback` 且标记 `status=failed`，不阻断回答。
- `ZYAGENT_INTERVIEW_PROVIDER=llm|rule` 选择面试官，默认 `llm`。LLM 实现通过 UTF-8 classpath prompt resources 调用 DeepSeek，并把 JD 快照、面试类型/难度、问题与历史问答作为上下文；评价要求四项整数评分和回答原文的逐字证据，并由服务端严格校验 JSON 结构、评分范围、追问字段及证据引用。
- `ZYAGENT_INTERVIEW_*` 还控制模拟面试上限：`MAX_TURNS` 是单会话最大轮数、`MAX_FOLLOW_UPS` 是每题最大追问数、`MODEL_TIMEOUT_MS` 是面试官模型调用超时。缺少 API key、模型调用失败/超时或评价输出不符合契约时，评价会保存为显式 `usable=false` fallback（分数不可信，附带安全说明）；这类评价不会计入有效评价平均分，也不会生成画像建议。有效评价仍按正常规则汇总。
- 将 `ZYAGENT_INTERVIEW_PROVIDER` 设为 `rule` 会启用确定性的 **规则演示模式** `规则演示模式`；其分数由本地规则启发式产生，不是 AI 评分，仅适合离线演示。`llm` 与 `rule` 的运行状态可通过 `GET /api/interviews/capabilities` 查看；前端分别显示检测中、已配置/演示、未配置或状态未知，并在状态未知时提供重试。
- 有效面试评价完成后会生成**待审核**的画像建议（`sourceType=INTERVIEW`，`sourceId=会话#轮次`），不会自动写入画像；不可用评价不产生建议。
- 画像的熟练度（`level`）与**系统置信度**（`confidence`）分开记录；每次写入都会追加一条证据（`profile_skill_evidence`）与一条版本历史（`profile_skill_history`），支持拒绝、更正与删除。
- 手动更正技能时可带 `expectedVersion` 做乐观并发校验，版本不一致返回 409，避免覆盖他人修改。
- 岗位匹配会读取**已确认画像技能**并输出 `profileEvidence`（技能 ← 来源#引用，置信度）；画像不可用时退化为纯简历匹配。
- 当前为单用户 demo：owner 由 `ZYAGENT_TASK_OWNER_ID` 经 `CurrentUserProvider` 提供，**不代表已具备多租户隔离**。
- **任务状态是一等状态**：`chat_session` 保存 `task_type / active_skill / current_day`（`ChatTaskState`），它是路由 Active Skill 的**权威来源**；聊天记录里助手消息的 Skill 只在任务状态缺失时兜底。`DayN` 是任务进度（`currentDay`），不是新的 Skill。
- **Skill 路由**采用「会话级 Active Skill + 三态决策」：每轮先判断当前 Active Skill 能否处理本轮请求 —— 能则 `CONTINUE`；命中其他 Skill 触发词或出现定义型新问题则 `SWITCH`；请求指代不明且无可继承任务则 `CLARIFY`（向用户确认，而不是硬选）。决策顺序：显式模式 → 元反馈（`chat_skill`）→ **执行已有计划某天（学习导师，任务继续）** → **学习计划创建/覆盖请求（规则优先，避免被判成复盘类任务）** → Active Skill 判断 → LLM 意图分类（置信度 ≥ 0.72）→ 上下文启发式 → 声明式触发词表 → 澄清 → 兜底。
- Skill 切换时**上下文按 Skill 作用域隔离**：只把当前任务那次对话以来的消息注入提示词，避免上一个 Skill 的长输出污染新任务；澄清场景保留更宽上下文。前端「路由决策」面板显示本次是沿用、切换还是澄清。

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
| `GET` | `/api/interviews/capabilities` | 查询当前面试官提供方与可用状态 |
| `POST` | `/api/interviews` | 创建面试会话并生成首题 |
| `POST` | `/api/interviews/{sessionId}/turns` | 提交当前轮回答（幂等 requestId） |
| `POST` | `/api/interviews/{sessionId}/abort` | 中止面试会话 |
| `GET` | `/api/interviews` | 分页查询面试历史 |
| `POST` | `/api/interviews/simulate` | 模拟面试（兼容旧接口） |
| `POST` | `/api/interviews/{sessionId}/answer` | 提交面试回答（兼容旧接口） |
| `GET` | `/api/interviews/{sessionId}` | 查询面试会话与轮次 |
| `POST` | `/api/interviews/{sessionId}/complete` | 结束面试会话 |
| `GET` | `/api/agent/tasks/{taskId}` | 查询 Agent 任务状态 |
| `GET` | `/api/agent/tasks/{taskId}/steps` | 查询 Agent 任务步骤 |
| `POST` | `/api/agent/tasks/{taskId}/retry` | 显式重试已失败/中断的 Agent 任务 |
| `GET` | `/api/profile` | 查询画像视图（技能 + 证据） |
| `GET` | `/api/profile/skills` | 查询已确认技能画像 |
| `PUT` | `/api/profile/skills/{skillKey}` | 手动新增/更正技能（可带 expectedVersion 防并发覆盖） |
| `DELETE` | `/api/profile/skills/{skillKey}` | 删除技能 |
| `GET` | `/api/profile/skills/{skillKey}/evidence` | 查询技能证据条目 |
| `GET` | `/api/profile/skills/{skillKey}/history` | 查询技能版本/审计历史 |
| `GET` | `/api/profile/suggestions` | 查询画像建议（可按 state 过滤） |
| `POST` | `/api/profile/suggestions` | 创建技能画像更新建议 |
| `PUT` | `/api/profile/suggestions/{id}` | 更正待审核建议 |
| `POST` | `/api/profile/suggestions/{id}/approve` | 审核并确认技能画像建议 |
| `POST` | `/api/profile/suggestions/{id}/reject` | 拒绝技能画像建议 |
| `POST` | `/api/reviews` | 生成复盘报告 |

## 验证命令

后端测试：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -f backend\pom.xml test
```

前端测试（必须在 `frontend\` 目录下运行：`markdownStyles.test.mjs` 以当前工作目录解析 `src\styles.css`）：

```powershell
cd frontend
node --test src\chat\chatStream.test.mjs src\chat\markdownRenderer.test.mjs src\chat\markdownStyles.test.mjs src\chat\chatSessionStore.test.mjs src\chat\uploadActions.test.mjs
```

检索评测（离线，无需 Milvus/MySQL）：

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -q -f backend\pom.xml test -Dtest=RagRetrievalEvaluationTest
```

该评测使用仓库内固定的候选排名 fixture，衡量给定召回结果后的融合/排序行为；它不代表真实语料的向量或 SQL 召回质量。未接入外部 reranker 时只评估 `rrf_fallback`，不代表 cross-encoder 已运行或效果已验证。

评测集与指标定义见 `docs/evaluation/README.md`。

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

zyagent 是一个基于 Spring Boot、Spring AI、MySQL、Milvus、Redis 和 Vue 3 构建的个人知识成长与求职 Agent 平台，覆盖资料沉淀、岗位解析、简历匹配、模拟面试、复盘和学习补强。系统以 `SkillRouter` 与 `AgentOrchestrator` 负责路由和 Plan-Execute-Replan，使用 `Planner`、`Retriever`、`Evaluator`、`Reviewer` 子 Agent 共享中间产物完成协作；以 MySQL 持久化会话、消息、任务、步骤和工具调用状态，支持任务重试、幂等键、超时与资源上限。RAG 采用向量与关键词并行召回、RRF 融合去重；当前默认使用 `rrf_fallback`，外部 reranker 尚未接入。多轮面试会持久化回答、评价、追问和复盘结果；可用评价会生成待审核的画像技能建议，而无效评价不会进入平均分或建议流程。已确认画像会参与岗位匹配，并为学习计划提供缺口输入。前端通过 SSE 展示路由、计划、工具步骤、任务状态、RAG 引用和 multi-agent 协作链路。

## 注意事项

- 真实 DeepSeek 调用需要配置 `DEEPSEEK_API_KEY`。
- Milvus 首次启动较慢，后端启动时可能等待连接初始化。
- 前端 Vite 默认代理后端接口，建议前后端分别使用 `5173` 和 `8080`。
- 如果端口被占用，先关闭旧的 Java 或 Node 进程后再启动。
