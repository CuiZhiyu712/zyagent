# zyagent Agent、RAG、面试与画像优化实施计划

> **执行说明：** 本计划分阶段落地；每个阶段均可独立验收。执行代码任务时遵循项目现有 Java/Spring、JDBC、Vue 3 和测试组织方式，并采用测试先行。此文档是实施路线，不代表文中规划功能已经实现。

**目标：** 在保留现有求职与知识成长 Agent 的基础上，补齐可追踪的任务状态持久化、真正的 Hybrid RAG 与可插拔重排、多轮可恢复模拟面试，以及可解释且由用户确认的技能画像闭环。

**架构：** 后端继续以 Spring Boot 单体应用承载现有 AgentOrchestrator、DocumentService、InterviewController 和 JDBC Repository。新增的任务、检索、面试、画像能力以领域服务和 Repository 划分；MySQL 负责业务状态与证据元数据，Milvus 保持向量召回职责。SSE 继续用于实时呈现，任务查询 API 用于恢复历史状态；Reranker 通过接口隔离实现与模型服务，未配置时用融合排序结果降级并明确标注。

**技术栈：** Java 17、Spring Boot 3、Spring JDBC、MySQL 8、Spring AI/DeepSeek、Milvus、Vue 3、JUnit 5、Node test。

---

## 1. 项目现状与差距

| 领域 | 当前已有 | 差距与计划 |
| --- | --- | --- |
| Agent 执行 | `AgentOrchestrator` 路由 Skill、准备计划步骤、调用工具、Replan 标记；SSE 返回 route/plan/tools/metrics/memory/collaboration；聊天和工具记录已有 MySQL 支持 | 计划和运行结果主要存在内存/单次响应中，没有 agent task/step 业务表、状态查询、服务重启后的历史恢复、统一限制与任务失败终态 |
| RAG | Tika 解析、切片、Embedding、Milvus 语义检索；MySQL `LIKE` 关键词和内存检索作为降级；返回引用 | 目前向量与关键词为先后 fallback，并非并行混合召回；没有融合去重、专用 rerank adapter、可重复检索评测集 |
| 模拟面试 | `/api/interviews/simulate` 创建 UUID 并返回首轮结果；`/{sessionId}/answer` 可触发回答处理 | UUID 和轮次没有持久化；回答不与具体问题关联；没有会话状态、结束/恢复、逐轮评价与画像证据链 |
| 用户画像 | `ResumeProfile` 是简历匹配过程中的轻量数据结构，前端可粘贴简历和技能 | 没有可管理的画像领域模型、证据来源、建议审核、历史版本和跨岗位/学习/面试的统一读取 |
| 工程保障 | 项目已有单元测试、MySQL schema 初始化、前端 SSE 和追踪视图 | 需新增领域测试、数据库集成测试、RAG 对照评测与可观测指标；升级表结构要采用可重复的向前兼容 schema/migration 方式 |

## 2. 目标边界与关键设计决策

1. **渐进增强，不替换现有链路。** 保留 `/api/chat/*`、Agent 工具注册、现有文档与岗位功能；新接口优先向后兼容。现有可视化 multi-agent 是单进程确定性协作，不在本计划中引入分布式多 Agent 或消息队列。
2. **任务状态先做持久可查询，再做执行恢复。** 当前执行同步地准备 AgentRun 再生成回答。第一版保留同步/SSE 执行方式，在开始、每步完成、最终完成/失败时写入 MySQL。进程崩溃造成的 `RUNNING` 任务启动后标为 `INTERRUPTED`，不声称能从任意 LLM 调用中断点续跑。对纯读取工具可提供显式重试；每个步骤必须具备幂等键或被声明为不可自动重试。
3. **Hybrid RAG 是并行召回与融合，不是 fallback 链。** 向量和关键词两路独立查询；一侧故障时保留另一侧结果并记下召回状态。先用 RRF（Reciprocal Rank Fusion）做稳定融合、按 chunk ID 去重，再重排。
4. **Rerank 是可插拔、可观测的真实二阶段排序。** 定义 `Reranker` 端口，候选取较大的召回池（初始建议每路 top 20，融合后最多 30）再重排至上下文 top 5–8。生产质量路径可配置外部 cross-encoder rerank 服务；本地/离线模式以 RRF 作为降级，返回 `rerankMode=rrf_fallback`，不得把 RRF 称作 reranker 已成功运行。配置、超时和失败均不阻断回答。
5. **首版服务单用户，但数据模型可扩展。** profile 表含 `owner_id`，当前无认证时使用配置的本地默认 owner；不得假装已经实现多租户隔离。将来接入认证时再将 owner 来源切换为身份上下文。
6. **画像变更需可追溯和可确认。** LLM/规则只提出更新建议，不静默降低或提高技能熟练度。每个建议记录证据和来源，用户确认后写入画像版本；用户可拒绝、更正或删除。
7. **持久化失败策略明确。** chat task 的数据库不可用时，不应悄悄伪称已持久化：记录告警并按配置决定是否拒绝创建任务；默认严格模式阻止创建新的可追踪 Agent task。知识检索一侧故障则可降级；画像或面试写失败应返回可理解的 API 错误并避免返回虚假的成功状态。

## 3. 目标架构与主要数据流

```text
Chat/Interview API
       |
       +--> AgentTaskService --> AgentTaskRepository / AgentStepRepository
       |             |                  MySQL: task + step + events/trace
       |             +--> AgentOrchestrator --> tools / RAG / LLM
       |                                        |
       |                           SSE progress + final answer
       |
       +--> InterviewService --> InterviewSession/TurnRepository
                                      |
                    Agent interviewer + evaluator + evidence
                                      |
                              Profile suggestion
                                      |
                              User confirmation
                                      v
                              UserProfileService

Query --> parallel vector recall + keyword recall --> RRF/dedup
                                                    --> Reranker port
                                                    --> citations + retrieval trace
```

## 4. 文件与模块规划

以下为预期边界；实施前应重新核对最新 working tree，复用已存在类型，避免机械照单新建同名文件。

### Agent Task

- `backend/src/main/java/com/zyagent/agent/AgentOrchestrator.java`：保留编排职责；在明确的步骤回调/事件端口上报告状态，避免直接写 JDBC。
- `backend/src/main/java/com/zyagent/agent/task/`：新增 task 模型、状态枚举、生命周期服务、步骤快照/事件对象和校验规则。
- `backend/src/main/java/com/zyagent/storage/AgentTaskRepository.java`、`AgentTaskStepRepository.java`：JDBC 持久化、分页查询、状态更新与事务边界。
- `backend/src/main/java/com/zyagent/chat/ChatController.java`：任务生命周期和 SSE task 事件集成；保留当前事件兼容性。
- `backend/src/main/resources/schema.sql`：增加 task、step 表及索引；执行时评估当前初始化器的异常吞掉行为，不能让新表缺失后仍宣称持久化工作。
- `frontend/src/chat/chatStream.js`、`frontend/src/components/chat/ChatWorkspace.vue`、`frontend/src/components/chat/ObservabilityTrace.vue`：解析和展示 task 状态、失败终态及重新查询历史。

### Hybrid RAG 与 rerank

- `backend/src/main/java/com/zyagent/document/DocumentService.java`：改为协调并行检索、融合、重排、最终引用；将解析、检索、融合和重排清晰分层。
- `backend/src/main/java/com/zyagent/document/retrieval/`：召回接口、融合器（RRF）、chunk 去重、rerank 请求/响应与状态追踪。
- `backend/src/main/java/com/zyagent/storage/DocumentRepository.java`：补足关键词候选召回和相关性排序；保持 SQL 参数化与知识类型过滤。
- `backend/src/main/java/com/zyagent/vector/VectorStore.java`、`MilvusVectorStore.java`：复用现有 vector search port；只在确有需要时扩展搜索阈值/过滤能力。
- `backend/src/main/java/com/zyagent/config/` 与 `application.yml`、`.env.example`：rerank provider、endpoint、topK、超时等配置；密钥只从环境变量读取。
- `backend/src/test/java/com/zyagent/document/`：融合、故障降级、过滤、去重、评测样例测试；增加本地 rerank stub。

### 多轮面试

- `backend/src/main/java/com/zyagent/interview/InterviewController.java`：改为调用领域服务，不在 controller 内构造临时流程。
- `backend/src/main/java/com/zyagent/interview/`：会话/轮次模型、状态机、请求响应、问题生成/回答评估接口和面试服务。
- `backend/src/main/java/com/zyagent/storage/InterviewRepository.java`：会话与轮次事务持久化、并发轮次号分配。
- `backend/src/main/resources/schema.sql`：面试会话、轮次、评价维度/结构化结果字段及索引。
- `frontend/src/App.vue` 与拆分后的 `frontend/src/components/interview/`（若现有 App.vue 面板过大则仅按面试范围拆分）：创建会话、当前问题、回答输入、逐轮反馈、继续/结束及历史恢复。
- `frontend/src/api.js`：新增会话详情、回答、结束、历史查询方法。

### 用户技能画像闭环

- `backend/src/main/java/com/zyagent/profile/`：画像领域对象、技能水平、证据、更新建议、审核状态、服务与规则。
- `backend/src/main/java/com/zyagent/storage/ProfileRepository.java`：owner 范围查询、版本保存、建议审核和事务。
- `backend/src/main/resources/schema.sql`：profile、skill、evidence、update suggestion/history 表。
- `backend/src/main/java/com/zyagent/match/ResumeJobMatcher.java`、`backend/src/main/java/com/zyagent/interview/`、学习计划入口：只通过 Profile 查询端口消费已确认画像，不直接依赖其 JDBC 实现。
- `frontend/src/components/profile/` 与 `frontend/src/api.js`：画像浏览/编辑、证据追溯、建议确认/拒绝/修正。

### 评测与文档

- `backend/src/test/java/com/zyagent/`：领域单元测试和 Repository/Controller 集成测试，复用项目现有无外部 API 测试风格。
- `docs/evaluation/rag-retrieval-cases.jsonl`：脱敏固定查询、预期文档/chunk、知识类型。
- `docs/evaluation/README.md`：指标定义、运行方式、基线结果记录模板。
- `README.md`：仅在功能实现并验证后同步更新；保持“已实现”和“规划中”分明。

## 5. 分阶段实施与验收

### 阶段 0：基线审计与接口契约

1. 在开始编码时检查 git 状态并保留所有现存用户改动；本次审计已发现多个未提交文件变更，执行期不得 reset、checkout 或覆盖这些文件。
2. 确认当前 schema 初始化方式、Repository 可用性/fallback 约定、SSE 消息格式、UI 面试面板和 `application.yml` 配置结构。
3. 固化 task、RAG、interview、profile API DTO 和状态枚举；确认数据库启动失败是否会阻止新功能启动。
4. 记录现有检索与面试 API 的行为基线，形成兼容性清单。

**验收：** 新接口/表/事件有契约；旧 `/api/chat`、`/api/interviews/simulate` 调用方有迁移兼容方案；未提交改动已被识别且保护。

### 阶段 1：Agent Task 与步骤持久化

1. 建立 `agent_task` 和 `agent_task_step` 表。Task 至少包含 `id, owner_id, session_id, mode, status, request_text, result_text, error_code, error_message, created_at, started_at, finished_at, updated_at`；Step 至少包含 `id, task_id, step_no, name, tool_name, status, input_summary, output_summary, error_message, attempt, duration_ms, started_at, finished_at`。存储摘要，避免复制不必要的完整简历/个人敏感文本。
2. 定义状态转换：Task `PENDING -> RUNNING -> SUCCEEDED|FAILED|TIMED_OUT|CANCELLED|INTERRUPTED`；Step `PENDING -> RUNNING -> SUCCEEDED|FAILED|SKIPPED|RETRYING`。拒绝非法终态回退。
3. 将执行限制做成配置：最大 plan step、最大 tool call、task 总 timeout、单工具 timeout、有限重试次数；超限写入终态和原因。重试只允许对声明幂等的只读工具；禁止无限循环。
4. 在编排边界注入生命周期 listener/repository service：task 创建、状态变化、每个步骤开始/结束、工具结果、最终 answer 均保存；SSE 复用稳定 taskId 并发出 `task`/`step`/`status` 事件。
5. 增加 `GET /api/agent/tasks/{taskId}`、`GET /api/agent/tasks/{taskId}/steps`，并按 owner/session 权限约束读取。若暂时无认证，owner 取配置默认值并在接口层留出身份适配点。
6. 应用启动时将上次遗留的 `RUNNING` 更新为 `INTERRUPTED`，可显示诊断信息。第一版提供显式重试整个可重入 task，不尝试在 LLM 生成中途恢复；task 创建需支持 idempotency key，防止客户端重试重复执行。
7. 逐步将 MySQL 持久化故障从静默吞掉变为新 task 路径上的显式错误；旧聊天若保留无数据库本地 demo，则 UI/响应明确标记 `persistence=memory`。
8. 前端消费 task/step 事件、完成/失败状态，并允许通过 taskId 拉取运行状态与步骤历史。

**验收：** 正常完成、工具失败、超时、用户断流、进程遗留、重复请求均有确定状态；刷新页面可还原 task/step 历史；状态迁移及 repository 有测试。

### 阶段 2：Hybrid RAG、融合和 rerank

1. 定义 `RetrievalCandidate`，包含 chunk 唯一标识、document metadata、内容、召回通道、原始排名/分数；`RetrievalTrace` 包含每路状态、耗时、候选数、融合分数、rerank 状态和最终引用。
2. 实现并行召回：Milvus 向量召回候选和 MySQL 关键词召回候选分别尝试，默认各取 20 条；数据库过滤知识类型。只要任一侧成功就继续，不因单侧异常丢弃另一侧结果；两侧都失败时再走内存降级。
3. 关键词候选从单纯“按 created_at 最近”调整为相关性排序。先做 tokenizer/技术标识符分词和命中覆盖评分，避免未经验证地依赖 MySQL FULLTEXT 对中文的支持；SQL 仍对输入参数绑定，按得分、更新时间做稳定排序。
4. 用 RRF 融合 rank：`score = Σ 1/(k + rank)`，默认 `k=60`；按 `documentId + chunkIndex` 去重，保留各渠道 rank 和原始分数便于解释。空 query、无结果、重复 chunk、类型过滤均有明确定义。
5. 定义 `Reranker` 接口及配置 adapter。外部 cross-encoder endpoint 可关闭；必须配置连接/读取超时、候选长度/数量上限、异常捕获和降级。成功时重排候选到 5–8 个；失败或未启用时用 RRF 顺序并标 `rrf_fallback`。仅配置 DeepSeek chat model 时不得把 LLM 文本排序伪装为 cross-encoder 成功。
6. 返回的 `DocumentSearchResponse` 扩展为 hybrid/rerank 元信息，同时保持 `chunks()` 和现有引用显示兼容；提示词只注入最终 topK，并含来源元数据。
7. 建检索评测集，覆盖技术名词精确查询、自然语言语义查询、同义词、多个文档冲突、knowledge type 过滤、空结果、Milvus/MySQL 单侧故障。指标至少有 Recall@5、MRR@10、nDCG@10、两路候选覆盖率、rerank 前后排名变化和耗时；先记 baseline，再据结果调 topK/k。
8. UI 在引用详情中显示召回通道与重排/降级状态；失败诊断可观测，不把内部堆栈给用户。

**验收：** 可证明双路并行和 RRF 正常；任一路不可用仍有另一侧结果；reranker 超时/失败自动降级；同一评测集可重跑并输出前后指标及检索模式。

### 阶段 3：持久化多轮模拟面试

1. 定义面试状态：`CREATED -> IN_PROGRESS -> COMPLETED|ABORTED|TIMED_OUT`；轮次状态：`QUESTION_READY -> ANSWERED -> EVALUATED -> FOLLOW_UP_READY|NEXT_QUESTION_READY`。所有写操作校验状态及当前轮次。
2. 建表：`interview_session` 保存 owner、jobId/JD 快照标识、类型、难度、状态、当前轮次、上限、摘要和时间；`interview_turn` 保存问题、回答、评价 JSON、分项分数、追问/下一题、引用证据、taskId 和时间。对 `(session_id, turn_no)` 加唯一约束。
3. 提供 `POST /api/interviews` 创建并生成首题、`GET /api/interviews/{id}` 恢复详情、`POST /api/interviews/{id}/turns` 提交当前回答并生成评价和下一步、`POST /api/interviews/{id}/complete` 结束并生成总结、`GET /api/interviews` 分页历史。对原 `POST /simulate` 保留兼容适配或清楚版本迁移。
4. 通过 `InterviewAgentService` 分别生成单一问题、结构化评价、是否追问/追问内容。评价维度最少包括技术正确性、完整性、项目证据、表达结构；分数之外要有解释和证据，模型 JSON 解析失败时使用有标识的 fallback，不将无效评分伪装成有效值。
5. 动态决策规则先可控：优先追问未解释关键点；达到追问上限/总轮次时换题或结束；加入每会话最大轮数、每题最大追问数和模型调用 timeout。单轮提交以幂等 requestId 防止重复计分。
6. 前端展示题目和历史轮次、提交回答、逐项反馈、追问、完成总结；刷新页面由服务端状态恢复，而非 Vue 内存状态。
7. 评价完成后产生 `ProfileUpdateSuggestion` 草案，附 interview session/turn/回答片段引用；不自动写入画像。

**验收：** 可创建、逐轮回答、按回答追问、结束、刷新恢复；并发/重复提交不会重复增加轮次；评价失败能重试或标失败；完成报告和画像建议可追溯至具体轮次。

### 阶段 4：用户画像与成长闭环

1. 定义 `UserProfile` 与 `SkillEvidence`：技能名称/标准化 key、当前级别、置信度、更新时间、证据条目、来源类型与来源 ID。证据来源区分用户手动、简历解析、岗位匹配、面试回答/评价、学习复盘；存储来源摘要和引用，不默认长期复制完整原始文档。
2. 建议表保存旧值/建议值、依据、来源、模型/规则、状态 `PENDING|APPROVED|REJECTED|EDITED`、用户备注和时间。所有画像更新写版本/审计历史，允许纠正和删除。
3. 提供 `GET/PUT /api/profile`、`GET /api/profile/skills`、`GET /api/profile/suggestions`、`POST /api/profile/suggestions/{id}/approve|reject` 等 API；写操作校验 owner、合法级别、重复建议和版本冲突。
4. 初期画像级别使用项目统一定义（如 `AWARENESS / BASIC / WORKING / PROFICIENT`），不直接用一个模型分数覆盖用户判断；系统置信度单独记录，避免把“熟练度”和“证据可信度”混为一项。
5. 简历/JD匹配从画像读取已确认技能和证据，输出优势/差距及其出处；学习计划以差距作为输入；模拟面试评价产生待审核建议；用户确认后画像更新，下次岗位匹配能观察到变化。
6. 前端提供查看、编辑、证据展开、建议确认/拒绝/修正和历史变更；没有数据时引导从脱敏简历或面试记录生成候选建议。
7. 为 profile owner 与未来认证身份建立单一 `CurrentUserProvider` 适配点。单用户 demo 默认 owner 不能被说成已具备多租户安全。

**验收：** 用户能查看技能和每项证据；拒绝/确认/手动修正都有历史；匹配、学习计划和面试能读写画像建议闭环；跨 owner 数据读取测试在身份机制接入前至少通过服务层 owner 条件验证。

### 阶段 5：端到端整合、评测和项目表达

1. 串联端到端场景：上传简历/JD/项目资料 -> Hybrid RAG 引用 -> 创建 Agent task -> 画像/岗位差距 -> 持久化多轮面试 -> 审核技能建议 -> 重新匹配并制定计划。
2. 统一可观测字段：`taskId`, `sessionId`, `stepNo`, `toolName`, `retrievalMode`, `rerankMode`, `interviewId`, `profileSuggestionId`, duration/status/errorCode。避免写入敏感正文到普通日志。
3. 回归验证旧 chat、工具、文档上传/删除、岗位采集和简历匹配 API；新增数据库集成测试验证事务、唯一键、owner filter、启动恢复和 schema migration。
4. README 更新架构图、状态机、API、配置、启动步骤、评测方式、fallback 行为和演示脚本；简历只列已实现且有测试/演示证据的能力，外部 reranker 未配置时如实写为可插拔能力与降级策略。
5. 留存一组可重复演示数据和评测结果；记录硬件/模型/数据集和配置，避免将单次输出宣称为通用质量提升。

**最终验收：** 新旧链路可启动并通过自动化回归；task、面试和画像可刷新恢复；RAG 有基线和 rerank 对照数字；端到端演示可从知识沉淀走到岗位匹配、面试、复盘和画像更新建议。

## 6. 推荐开发顺序与依赖

```text
阶段 0 基线/契约
   ├── 阶段 1 Task 持久化 ───────┐
   ├── 阶段 2 Hybrid RAG/rerank ─┼── 阶段 5 整合与简历事实核验
   └── 阶段 3 多轮面试            │
              └── 阶段 4 画像闭环 ┘
```

推荐顺序为 **0 → 1 → 2 → 3 → 4 → 5**。Task 生命周期先完成，后续面试轮次可引用 taskId；Hybrid RAG 先稳定证据结构，面试评价与画像建议再消费其证据；画像放在面试之后，能使用面试实证而非只依据静态简历。各阶段内部仍应拆为小 PR/提交，优先保证每一阶段可运行。

## 7. 风险与应对

| 风险 | 应对 |
| --- | --- |
| 当前 schema 初始化器捕获异常后只打 warning，造成运行中缺表 | 新增 migration/schema 检查；持久化功能启用时启动健康检查失败即 fail fast，保留无数据库 demo 的显式模式 |
| 外部 reranker 带来费用、延迟和隐私问题 | adapter 可禁用；传输脱敏 chunk、限制候选长度与 timeout；先用公开/合成评测集，用户简历相关请求默认遵循配置与数据策略 |
| Hybrid RAG 增候选可能增加上下文成本 | 候选、文本长度、最终 topK 均设上限；记录 token 估算和耗时；以评测调参 |
| 状态表和领域模型扩张过快 | 每阶段先交付最小字段；证据保留引用与摘要；暂不引入事件总线、复杂工作流引擎或通用 memory 平台 |
| 用户画像错误推断影响岗位建议 | 建议审核制、证据追溯、置信度独立、允许更正和撤回 |
| 当前无认证导致未来 owner 隔离风险 | 单用户默认值集中管理；所有 Repository 方法显式带 owner；认证作为后续边界，不宣称现已多用户安全 |
| LLM 输出格式漂移或不可复现 | 用结构化 DTO、校验器和失败状态；评测保存模型/提示词版本和参数；不把 fallback 输出计入模型质量成绩 |
| 未提交工作树修改被计划执行覆盖 | 执行前先核对 `git status`；只编辑所需文件；禁止重置、清理或覆盖用户现存改动 |

## 8. 测试与质量门槛

- **领域单测：** 状态机非法迁移、执行上限、RRF 计算/稳定排序/去重、reranker 失败回退、面试追问规则、画像建议审批和版本冲突。
- **Repository 集成测试：** schema 建表与升级、事务回滚、唯一约束、分页排序、owner 隔离、task 重启恢复、轮次幂等。
- **API/SSE 测试：** 成功与失败状态事件、taskId 传播、断开后的可查询状态、旧事件兼容、输入验证。
- **前端测试：** 新 SSE 事件解析、历史 task 状态展示、面试恢复与轮次流程、画像建议状态变化。
- **RAG 离线评测：** 固定数据集运行 recall/ranking metrics；结果记录配置与时间；质量门槛先由 baseline 决定，不预先捏造提升百分比。
- **回归：** 执行现有 Maven/JUnit 和 Node 测试及前端构建；新增测试只覆盖新增行为和必要回归，不在本计划文档阶段运行。

## 9. 面试准备与简历表述边界

完成并验证后可按实际成果描述：

- 将 Agent 编排任务和步骤持久化，提供 SSE 执行轨迹及历史状态查询，并实现超时、最大步骤/工具调用限制和失败终态处理。
- 构建向量与关键词并行召回、RRF 融合及可插拔 cross-encoder 重排链路，通过固定评测集比较 Recall@K、MRR/nDCG 与耗时，并对 reranker/Milvus 故障降级。
- 实现 MySQL 持久化多轮面试，基于回答进行结构化评价和动态追问，结束后生成可追溯复盘。
- 建立带来源证据和审核记录的技能画像，将岗位差距、面试评价与学习计划串成可确认的成长闭环。

未实现的外部 reranker、服务重启断点续跑、多用户认证和画像自动更新不得写成已完成。每条简历成果应对应代码、测试或演示记录；数字型效果只使用实际评测所得结果。
