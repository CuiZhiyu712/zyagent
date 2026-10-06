# zyagent 对话模块化重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. Work only in the new project copy and preserve the original directory.

**Goal:** 在独立副本 `D:\JAVA_Projects\ZYagent-refactor` 内重构现有聊天链路并把后端代码按 common、infrastructure 与业务 modules 归类，保持技术栈和对外契约。

**Architecture:** ChatController 作为 Web/SSE 边界，ChatApplicationService 协调会话与任务，ConversationPipeline 拆分路由、Skill 能力、上下文、工具/RAG 和 Prompt。领域模块通过服务端口提供已有能力；Vue 聊天工作区把页面状态和网络协议移入聊天模块。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring AI 1.1、Spring MVC、Spring JDBC、MySQL、Milvus、Vue 3、Vite。不得升级或替换。

---

## 工作区边界

- 源目录：`D:\JAVA_Projects\ZYagent`，只读，不在此目录实施后续更改。
- 实施目录：`D:\JAVA_Projects\ZYagent-refactor`，包含当前源码、未提交项目改动和 Git 历史。
- `.env`、`backend/data`、`backend/target`、`frontend/node_modules`、`frontend/dist`、`logs` 已在复制时排除；`.env.example` 保留。
- 用户已同意可复用 AGPL-3.0 代码。只在逐文件检查许可证、依赖和兼容性后复用；复制时记录来源和修改说明。
- 用户随后明确要求修改 `start-backend.bat` 并运行构建和测试；已据此加入 `test`、`build`、`start` 三种脚本命令，并运行后端与前端测试/构建。保留并同步了现有测试源码的包名/路径。

## 参考代码复用初筛

已读取上游提交 `969e2af8550f680688f5b50f0d895186a661a319`。

- `app/src/main/java/interview/guide/common/ai/PromptSanitizer.java`：依赖参考项目配置，主要是规则表达式过滤；直接移植会把启发式净化误作安全边界，本阶段不复制。
- `common/ai/StructuredOutputInvoker.java`：依赖 Spring AI 2 的 `ChatClient`/`BeanOutputConverter`、Micrometer 和参考项目配置；zyagent 当前版本接口不兼容，且不解决对话编排主问题，不复制。
- `common/ai/LlmProviderRegistry.java`：依赖 Spring AI 2 的模型、Advisor 和 ToolCallback 组合，并服务多模型配置；该功能明确不在本阶段，不复制。
- `common/result/Result.java`：和现有 `ApiResponse<T>` 重复，无需引入第二种响应类型。

因此本阶段复用其模块分层和 Prompt 按业务能力分离的设计。若实施中发现新的、独立且兼容的代码候选，先在本计划里登记上游路径、固定提交、依赖和改动范围，再复用；否则保持本项目实现。

## 目标包映射

| 当前 package | 目标 package |
| --- | --- |
| `com.zyagent.common` | 保持 `com.zyagent.common` |
| `com.zyagent.config` | `com.zyagent.infrastructure.config` |
| `com.zyagent.ai` | `com.zyagent.infrastructure.ai` |
| `com.zyagent.structured` | `com.zyagent.infrastructure.ai.structured` |
| `com.zyagent.storage` | `com.zyagent.infrastructure.storage` |
| `com.zyagent.vector` | `com.zyagent.infrastructure.vector` |
| `com.zyagent.agent` | `com.zyagent.modules.agent` |
| `com.zyagent.skill` | `com.zyagent.modules.agent.skill` |
| `com.zyagent.tool` | `com.zyagent.modules.agent.tool` |
| `com.zyagent.chat` | `com.zyagent.modules.chat` |
| `com.zyagent.document`, `com.zyagent.rag` | `com.zyagent.modules.knowledgebase`、`com.zyagent.modules.knowledgebase.rag` |
| `com.zyagent.resume`, `com.zyagent.match` | `com.zyagent.modules.resume`、`com.zyagent.modules.resume.match` |
| `com.zyagent.profile` | `com.zyagent.modules.profile` |
| `com.zyagent.job` | `com.zyagent.modules.job` |
| `com.zyagent.interview` | `com.zyagent.modules.interview` |
| `com.zyagent.review` | `com.zyagent.modules.review` |

Java 类和测试包名依照此表同步迁移。URL、JSON 字段、数据库 schema 和 SSE 协议不随 Java package 改名。

## Task 1: 冻结兼容契约并登记复用审查

**Files:**
- Create: `docs/superpowers/plans/2026-10-06-zyagent-chat-modular-refactor.md`（本文件）
- Read: `backend/src/main/java/com/zyagent/chat/ChatController.java`
- Read: `frontend/src/chat/chatStream.js`
- Read: `frontend/src/components/chat/ChatWorkspace.vue`

- [x] **Step 1: 记录当前接口契约**

  从当前 ChatController 记录 `/api/chat/complete`、`/api/chat/stream`、`/api/chat/sessions` 及其子资源的请求字段、响应 DTO 和 fallback 行为；从 ChatWorkspace 与 chatStream 记录当前事件处理映射。
- [x] **Step 2: 固定上游参考提交并检查候选实现**

  固定源码提交为 `969e2af8550f680688f5b50f0d895186a661a319`，逐文件检查 PromptSanitizer、StructuredOutputInvoker、LlmProviderRegistry 与 Result；初筛结果如“参考代码复用初筛”所列。
- [x] **Step 3: 每批修改前对照兼容契约**

  每个后续任务完成时手工核对 URL、JSON/SSE 结构和降级路径未因内部重构改变。

## Task 2: 迁移后端包边界

**Files:**
- Move/Modify: `backend/src/main/java/com/zyagent/{config,ai,structured,storage,vector}` 到 `infrastructure` 目标包
- Move/Modify: `backend/src/main/java/com/zyagent/{agent,skill,tool,chat,document,rag,resume,match,profile,job,interview,review}` 到 `modules` 目标包
- Move/Modify: `backend/src/test/java/com/zyagent/**` 中与被迁移 package 对应的测试文件
- Modify: `backend/src/main/java/com/zyagent/ZyagentApplication.java` 及配置类中指向原 package 的扫描/引用

- [x] **Step 1: 建立目标目录并按表迁移源码**

  在 `backend/src/main/java/com/zyagent` 下建立 `infrastructure` 与 `modules` 目标目录；按映射表迁移 Java 文件，保持类名、Spring Bean 名称、`@RequestMapping` 路径和数据库映射不变。源目录和目标目录均必须在 `D:\JAVA_Projects\ZYagent-refactor\backend\src\main\java\com\zyagent` 下。
- [x] **Step 2: 同步修改 package 与 import 声明**

  更新所有 main/test Java 源码中的 `package` 和 `import`，使用目标包名映射；保留 `com.zyagent.common` 和 `com.zyagent.ZyagentApplication`。
- [x] **Step 3: 对照迁移清单清理旧源目录**

  仅在确认旧目录不再包含 Java 文件后移除空目录；不得删除资源、schema、测试数据或用户文件。
- [x] **Step 4: 静态检查 package 声明**

  对 `backend/src/main/java` 和 `backend/src/test/java` 搜索 `com.zyagent.<旧 package>`；仅在字符串、SQL 或注释确实不表示 Java package 时保留命中，并逐一检查。

## Task 3: 收窄 ChatController 并抽出应用服务

**Files:**
- Modify: `backend/src/main/java/com/zyagent/modules/chat/ChatController.java`
- Create: `backend/src/main/java/com/zyagent/modules/chat/application/ChatApplicationService.java`
- Create: `backend/src/main/java/com/zyagent/modules/chat/application/ChatSessionService.java`
- Create: `backend/src/main/java/com/zyagent/modules/chat/api/ChatRequest.java`
- Modify: `backend/src/main/java/com/zyagent/infrastructure/storage/ChatRepository.java`
- Modify: `backend/src/main/java/com/zyagent/infrastructure/storage/ToolCallRecordRepository.java`

- [x] **Step 1: 将 chat 请求 DTO 提升为稳定 API 类型**

  把现有 `ChatController.ChatRequest(sessionId, message, agentMode, idempotencyKey)` 移为 `com.zyagent.modules.chat.api.ChatRequest`，并把 `CreateSessionRequest(sessionId, title, agentMode)` 一并移为 `com.zyagent.modules.chat.api.CreateSessionRequest`；字段名、类型、默认处理与 Jackson 表示保持一致。
- [x] **Step 2: 抽取会话用例**

  `ChatSessionService` 接收当前 `ChatRepository` 可选依赖，承接列表、创建、删除、消息列表、ensure/touch 和数据库不可用 fallback。保留原返回的 `ApiResponse` 语义及错误消息。
- [x] **Step 3: 抽取同步对话用例**

  `ChatApplicationService.complete(ChatRequest)` 协调建立 session、保存用户消息、调用 Agent、保存工具记录与助手结果并返回原 `AgentResult`。ChatController 不再编排 Repository 或序列化助手 metadata。
- [x] **Step 4: 将控制器收敛为协议适配**

  Controller 只保留 `/api/chat/*` 映射、请求绑定、`SseEmitter` 创建和委托；会话与同步对话的实现转调应用服务，接口路径不变。

## Task 4: 拆分 SSE 协议适配与流执行

**Files:**
- Create: `backend/src/main/java/com/zyagent/modules/chat/application/ChatStreamService.java`
- Create: `backend/src/main/java/com/zyagent/modules/chat/api/ChatSsePublisher.java`
- Modify: `backend/src/main/java/com/zyagent/modules/chat/ChatController.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/AgentPipelineTrace.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/task/AgentTaskStatusUpdate.java`

- [x] **Step 1: 把事件命名与 payload 映射集中到 SSE publisher**

  建立 `ChatSsePublisher`，封装 skill、route、plan、tools、step、metrics、memory、collaboration、pipeline、references、message、task、status、usage 事件；维持旧事件名称、字段和发送顺序。
- [x] **Step 2: 把流式生命周期移入 ChatStreamService**

  `ChatStreamService.stream(ChatRequest, SseEmitter)` 承接原 worker 生命周期、stream prepare、token 收集、usage、完成/失败、部分回答保存和 emitter complete。断流只跳过推送，不更改持久化 task 终态。
- [x] **Step 3: 移除 ChatController 中的业务 helper**

  将 `ensureSession`、`saveUserMessage`、`saveAssistantMessage`、`saveTools`、`titleFrom` 和 trace 构造分别归到 ChatSessionService、ChatApplicationService、ChatStreamService 或 mapper；Controller 不直接引用存储仓库。
- [x] **Step 4: 手工核对事件契约**

  对照 Task 1 记录检查 stream/complete 的事件名、载荷、顺序、错误终态和 fallback 状态字段。

## Task 5: 抽离对话 Pipeline 职责

**Files:**
- Create: `backend/src/main/java/com/zyagent/modules/agent/runtime/ConversationPipeline.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/runtime/ConversationContextAssembler.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/runtime/SkillToolExecutor.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/prompt/SkillPromptComposer.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/AgentOrchestrator.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/PromptAdvisorChain.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/PlannerAgent.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/RetrieverAgent.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/EvaluatorAgent.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/ReviewerAgent.java`

- [x] **Step 1: 抽取上下文组装**

  `ConversationContextAssembler` 接收 sessionId、路由决策和 Skill；澄清场景用宽上下文，其它场景使用 `renderForSkill`；学习 Day 继续使用现有计划片段识别规则。不得由 Controller 再次计算 Active Skill。
- [x] **Step 2: 抽取工具执行协调**

  `SkillToolExecutor` 承接当前 `react`、`executeTool`、`inputFor`、超时与安全重试行为；将 Skill 工具白名单与本轮 IntentSignals/内容资格判断集中在该边界。`CHAT`、`CLARIFY`、元反馈不调用业务工具。
- [x] **Step 3: 抽取 Skill 专属 Prompt Composer**

  `SkillPromptComposer` 接收已经筛选的 Skill、Context、工具结果与必要的回答约束，输出当前 LLM 调用所需的 user prompt；不拼入完整协作 artifacts、日志、token metrics 或重复审计章节。
- [x] **Step 4: 建立 ConversationPipeline 并收窄 Orchestrator**

  Pipeline 按 route → skill → context → eligible tool/RAG → prompt → model 顺序协调；`AgentOrchestrator` 保持 `prepare/stream/complete/fail/retry` 外部方法和 `AgentRun` 结果兼容，只负责生命周期入口和组件组合。
- [x] **Step 5: 保留有价值的 trace，移除对回答的审计强制**

  Planner/Retriever/Evaluator/Reviewer trace 继续随原响应返回；Prompt 只使用与用户答案相关的证据和明确降级约束，不要求最终回答引用 Planner/Evaluator 等内部过程。

## Task 6: 将 Skill 的提示词与能力定义解耦

**Files:**
- Create: `backend/src/main/java/com/zyagent/modules/agent/skill/SkillPromptCatalog.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/AgentMode.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/skill/SkillDefinition.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/skill/SkillCatalog.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/skill/SkillRouter.java`
- Modify: `backend/src/main/resources/application.yml`

- [x] **Step 1: 从 AgentMode 移出长职责 Prompt**

  保留 AgentMode 的稳定枚举值和展示标题；将每类 Skill 的 system prompt 放到独立 catalog 或资源模板，不在枚举常量中叠加业务实现细节。
- [x] **Step 2: 保留 Skill 声明中的显式能力策略**

  沿用 SkillDefinition 中既有的 toolNames / planSteps 作为能力策略，SkillCatalog 继续按 Skill 显式声明；提示词通过 SkillPromptCatalog 解耦。为保持 Skill JSON 对外结构兼容，本阶段不向 SkillDefinition 增加序列化字段。
- [x] **Step 3: 按本轮请求筛选实际工具**

  SkillToolExecutor 仅在输入类型和当前路由允许的条件下调用搜索、JD 解析、简历建议、题目生成或学习计划工具；不执行“有计划项就必须调工具”的默认行为。
- [x] **Step 4: 核对会话级 Active Skill**

  SkillRouter 继续以会话任务状态为权威，处理 CONTINUE/SWITCH/CLARIFY；任何分类 fallback 都复用一个 `AgentRouteDecision`，不在 Controller 或 Prompt Composer 内二次分类。

## Task 7: 领域能力通过模块接口供对话使用

**Files:**
- Create: `backend/src/main/java/com/zyagent/modules/agent/port/KnowledgeSearchPort.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/port/ResumeAdvicePort.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/port/JobAnalysisPort.java`
- Create: `backend/src/main/java/com/zyagent/modules/agent/port/InterviewPracticePort.java`
- Modify: `backend/src/main/java/com/zyagent/modules/agent/tool/AgentToolService.java`
- Modify: `backend/src/main/java/com/zyagent/modules/knowledgebase/DocumentService.java`
- Modify: `backend/src/main/java/com/zyagent/modules/resume/match/ResumeJobMatcher.java`
- Modify: `backend/src/main/java/com/zyagent/modules/job/JobDescriptionParser.java`
- Modify: `backend/src/main/java/com/zyagent/modules/interview/InterviewService.java`

- [x] **Step 1: 定义最小领域用例端口**

  接口按用户目标而非存储方式命名，只暴露 AgentToolService 当前真实需要的操作；`ToolResult` 和展示 trace 留在 Agent 模块。
- [x] **Step 2: 绑定现有领域实现**

  为已有知识库、简历建议、JD 解析/岗位匹配、面试练习实现适配器；已有领域 Service 保留业务规则，工具调用不直接触碰 JDBC 或 Milvus Repository。
- [x] **Step 3: 保留没有命中的可解释状态**

  Adapter 将无结果、不可用和调用异常映射为明确的空结果/工具错误；Prompt Composer 不得把错误解释为成功证据。

## Task 8: 拆分 Vue 聊天 API、状态和页面

**Files:**
- Create: `frontend/src/chat/chatApi.js`
- Create: `frontend/src/chat/useChatWorkspace.js`
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/chat/chatStream.js`
- Modify: `frontend/src/chat/chatSessionStore.js`
- Modify: `frontend/src/components/chat/ChatWorkspace.vue`
- Keep: `frontend/src/components/chat/ChatSidebar.vue`
- Keep: `frontend/src/components/chat/MessageList.vue`
- Keep: `frontend/src/components/chat/MessageComposer.vue`

- [x] **Step 1: 把 `/api/chat/*` 请求封装迁入 chatApi**

  提供 sessions/create/delete/messages/complete/stream/retry 所需方法；保留原路径、请求字段及 ApiResponse 解包语义。`api.js` 继续承载其它业务 API。
- [x] **Step 2: 收敛 SSE 事件分发**

  `chatStream.js` 使用一个事件分发表维护 handlers 与事件名映射，流末尾缓冲区仍交付完整 frame；取消请求仍触发 onAbort，异常仍触发 onError。
- [x] **Step 3: 抽离 ChatWorkspace 状态协调**

  `useChatWorkspace` 负责加载/创建/选择/删除 session、缓存恢复、chat stream、retry、upload 状态及消息 trace 合并；`ChatWorkspace.vue` 仅组合 sidebar/list/composer 并绑定 composable 返回值。
- [x] **Step 4: 保留现有展示组件**

  不重写 MessageList、Trace 子组件或样式；继续传入现有消息 payload，保证 route、references、task、pipeline 和 collaboration 可视化仍然读取相同字段。

## Task 9: 记录上游代码来源并完成静态审阅

**Files:**
- Create if code is copied: `THIRD_PARTY_NOTICES.md`
- Modify: `README.md` only if project-level AGPL/source offer notice is needed for copied code
- Review: all files changed in this plan

- [x] **Step 1: 若没有逐字复用代码，不新增第三方代码声明文件**

  仅使用模块分层思路时，在计划中记录该结论即可；不要因为浏览过上游仓库而误标代码来源。
- [x] **Step 2: 若最终复制了源码，增加来源和许可证声明**

  `THIRD_PARTY_NOTICES.md` 记录精确源路径、上游提交 `969e2af8550f680688f5b50f0d895186a661a319`、原作者版权、AGPL-3.0、文件改动摘要与获取源码方式；源文件已有的版权头保留。
- [x] **Step 3: 审阅最终 diff 与原目录隔离**

  在 `D:\JAVA_Projects\ZYagent-refactor` 运行 `git diff --check` 并审阅 `git status --short` 与 `git diff --stat`；确认所有业务源码改动仅出现在副本中，且未增加日历、题库、语音面试或多模型功能。

## 执行顺序与边界

按 Task 1–9 顺序内联实施，不并行修改共享 Java package。每完成一项先审阅当前 diff，再进入下一项。SSE/API 兼容、任务降级和当前 92 项未提交代码的功能均属于保留范围。详细迁移时如果发现与用户当前未提交更改冲突，只在副本内解决，不从原目录回拷或覆盖原文件。
