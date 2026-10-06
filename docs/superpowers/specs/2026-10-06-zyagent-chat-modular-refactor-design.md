# zyagent 对话模块化重构设计

**状态：** 用户已审阅并确认，开始按计划实施。

**日期：** 2026-10-06

## 1. 目标

在保留 zyagent 现有技术栈和业务能力的前提下，重构智能对话的模块边界与执行链路，使意图路由、上下文、工具/RAG、提示词、任务追踪和 SSE 输出各有明确职责。重点减少对话路由混乱、跨 Skill 上下文串线、不相关工具调用，以及 Controller 和 Orchestrator 承担过多职责的问题。

本阶段只重构已有能力。后续功能扩展限定为面试日历和题库管理；不纳入语音面试、多模型管理等其它新增功能。

## 2. 当前背景

项目已经有 Spring MVC + Vue 聊天界面、Skill 路由、会话记忆、Agent 工具、Hybrid RAG、任务和步骤持久化、SSE 追踪、文字模拟面试、简历/岗位分析及画像建议等能力。这些能力应被整理和复用，不做业务重写。

目前 `AgentOrchestrator` 集中处理路由后的 Skill 执行、静态计划步骤、工具调用及重试、记忆范围、提示词组装、协作信息和任务状态；`ChatController` 同时承担会话/消息存取、同步和流式对话、SSE 事件及异常处理。前端 `ChatWorkspace.vue` 也包含较多会话、缓存、流式事件和页面协调逻辑。职责交叠使行为难以定位和分别调整。

参考项目采用 `common / infrastructure / modules` 的总体分层，业务按模块组织。zyagent 借鉴该分层原则，但保留自身框架与存储选型，不追随参考项目的技术版本。

## 3. 约束与原则

- 保持 Java 17、Spring Boot 3、Spring AI 1、Spring MVC、Spring JDBC、MySQL、Milvus、Vue 3 和 Vite。
- 不进行 Spring Boot、Spring AI、Java、数据库或前端框架升级。
- 保留现有 `/api/chat/*` API、请求/响应兼容性、SSE 事件名与数据结构、任务和追踪能力，以及主要聊天交互。
- 以渐进迁移方式切分包和职责；每一批迁移都能独立审阅，避免一次性移动全仓代码。
- 业务规则归领域模块。聊天编排通过明确的能力接口使用简历、岗位、知识库、面试、复盘和画像能力，不直接耦合其存储实现。
- 不把模拟的协作追踪宣传成多个独立模型 Agent；只保留现有用户可见追踪价值，不要求模型输出内部审计内容。

## 4. 目标模块结构

```text
com.zyagent
├── common/                 # 通用 API 响应、业务异常、跨模块基础类型
├── infrastructure/         # AI 客户端适配、JDBC、向量库、文件解析等技术实现
└── modules/
    ├── chat/               # 聊天 API、应用服务、会话与流式用例
    ├── agent/              # 对话运行时、路由、Skill、工具执行、Prompt、trace
    ├── knowledgebase/      # 文档、切片、检索、引用
    ├── resume/             # 简历分析与建议
    ├── profile/            # 用户技能画像、证据、建议审核
    ├── job/                # 岗位与采集
    ├── interview/          # 文字模拟面试与评价
    └── review/             # 面试复盘与补强计划
```

该结构是演进目标，不要求将现有所有类一次迁入新包。第一批优先建立聊天协议/应用层与 Agent 对话运行时的边界；随后按领域逐个将现有服务和存储端口归位。`infrastructure` 只包含技术适配，不放业务判断。

前端保留 Vue 3，聊天功能按职责组织：API 客户端、SSE 流解析、会话状态/缓存、聊天工作区编排以及纯展示组件。页面组件不直接处理底层网络协议或重复的 SSE 事件分发。

## 5. 对话执行设计

```text
HTTP request
  -> ChatController (验证/协议适配)
  -> ChatApplicationService (会话、消息、任务生命周期)
  -> ConversationPipeline
       1. 选择路由动作：继续当前 Skill / 切换 Skill / 澄清
       2. 选择 Skill 及允许使用的能力
       3. 按 Skill 作用域组装必要记忆和当前任务状态
       4. 只执行当前问题需要的工具或知识库检索
       5. 由 Skill 专属 Prompt Composer 组织指令、上下文、工具结果
       6. 生成回答，并保留引用、错误、用量和运行追踪
  -> SSE publisher / Chat response mapper
  -> 持久化消息与任务状态
```

### 路由与 Skill

- 继续使用现有显式模式、Active Skill、上下文意图和澄清能力；路由动作应由单一边界返回，避免 Controller 与 Orchestrator 各自推断。
- 每个 Skill 定义自己的 Prompt、工具白名单、需要的上下文类型和回答约束。
- 元反馈、纠错与意图不清场景保持专门处理，不触发业务工具。

### 上下文与工具

- 由独立 ContextAssembler 决定当前轮所需记忆，Skill 切换时沿用现有隔离规则；执行学习计划日程时只注入相关计划部分。
- 工具调用从“Skill 所有工具的静态列表”收敛为基于当前请求的资格判断/选择；只把成功且与当前任务相关的结果传给模型。
- RAG 是可按 Skill 启用的能力。返回的引用、召回状态和重排信息保留在追踪/回答引用协议中；没有证据时不得伪造引用或数字。
- Planner、Evaluator、Reviewer 等 trace 只在对最终回答有实际帮助时转成必要约束；默认不把完整审计 artifacts 拼入 Prompt。

### Prompt 与回答

- Prompt 由职责单一的 Composer 组装；系统规则、Skill 指令、记忆、当前请求、工具结果使用可辨识的段落边界。
- 每个 Skill 使用独立模板或专属构建方法，避免多个角色规则堆叠进一个全局 Prompt。
- 保留面向用户的自然回答要求；内部 trace、token 统计和质量诊断继续通过结构化事件单独展示。

### 任务、SSE 与异常

- ChatApplicationService 管理用户消息、Agent task、助手消息和运行结果的协调；Controller 负责把兼容数据映射到 HTTP/SSE。
- 现有 SSE 事件名称与载荷格式维持兼容，包含 route、skill、plan、tools、step、metrics、memory、collaboration、pipeline、references、message、task/status、usage 等已有事件。
- 工具失败记录在步骤和追踪中；可安全继续生成时标明降级情况，无法继续时返回确定的失败终态。
- 存储降级保留当前演示模式；状态必须区分已持久化与内存降级，不能报告虚假的持久化成功。
- 客户端断流仍不应破坏已创建任务的确定终态。

## 6. 代码复用与许可证

用户同意在可用时直接复用参考项目代码，并接受 AGPL-3.0 的相应义务。复用执行规则如下：

1. 先检查具体文件的许可证头、依赖和上下游调用，不根据 README 描述推断实现兼容。
2. 仅直接复用边界清楚、在 zyagent 当前技术栈下可移植且对本阶段有用的实现；记录上游路径、版本/提交、版权与许可证来源，并保留必要声明。
3. 当前参考项目 README 标注 Java 25、Spring Boot 4.1、Spring AI 2.0；zyagent 使用 Java 17、Spring Boot 3、Spring AI 1，因此框架专属实现默认需要适配，不能原样引入依赖版本或 API。
4. 不为复用完整子系统而引入语音、多模型、异步题库等本次未包含能力。非必要的同类能力用新代码按其设计重新实现。
5. 具体代码组合的许可义务可能取决于复制范围、链接方式与发布方式；项目采用 AGPL 代码时，应按接受的许可条件履行相应要求。GNU FAQ 说明 AGPL 网络交互条款要求向远程交互用户提供对应源代码。

## 7. 兼容与质量目标

- 现有聊天 API 的 endpoint、主要字段和成功/错误行为保持兼容。
- SSE 消费端不需要因为模块内部重构修改事件解析协议。
- 相同会话中的 Skill 延续、切换、澄清行为由一处路由结果驱动；切换 Skill 不再注入上一个 Skill 的长篇业务输出。
- 不因路由 Skill 而自动执行无关工具；例如纯闲聊、纠错、澄清不触发知识库或业务写入能力。
- 无 RAG 命中、工具失败或持久化降级时，trace 与回答不得声称成功命中或持久保存。
- 现有题目、简历、岗位、知识库、学习计划和复盘用例在重构后仍由各自模块提供，不重复实现业务逻辑。

## 8. 验证策略

- 兼容性验收覆盖路由动作、Skill 工具白名单、上下文隔离、Prompt 组合、无关工具不执行、SSE 协议映射、任务终态与存储降级。
- 前端验收覆盖 SSE 事件分发与会话恢复状态，确保页面组件仅依赖聊天模块接口。
- 对话验收场景覆盖闲聊/纠错、简历、岗位 JD、知识检索、面试回答/追问、学习计划延续、复盘及意图澄清；应核对路由、工具和引用期望，不能只凭 Prompt 文案判断质量。
- 自动化测试或运行测试命令需在用户明确要求测试/验证时进行；未获此要求时，通过代码审查和协议对照完成本次验收。
- 实施时按迁移切片复核相关结果，不通过整体改写隐藏回归。

## 9. 范围外及后续阶段

第一阶段不实现以下能力：语音面试、面试日历、题库管理、多模型配置、面试报告 PDF 导出、新数据库/队列/对象存储、认证多租户及技术栈升级。

第一阶段之后，如用户再次确认，后续新增范围限定为：

1. 面试日历：面试安排的创建、解析、状态、日期视图与提醒设计。
2. 题库管理：题目 CRUD、方向/难度/启停状态、知识库生成和专项面试集成。

两者应作为独立业务模块，复用第一阶段建立的模块边界与 AI/存储适配；不扩展到参考项目的其它缺失功能。

## 10. 实施拆分建议

1. 建立当前聊天 API/SSE 和模块依赖基线，避免内部重构破坏外部协议。
2. 抽离 ChatApplicationService，收窄 ChatController 为协议适配与 SSE 事件发送。
3. 将 ConversationPipeline 拆分为路由/Skill 选择、上下文组装、工具执行、Prompt 组装和结果记录职责。
4. 对照参考源码审查直接复用候选；针对兼容代码做最小移植并保留许可证来源。
5. 将 Vue 聊天 SSE、API 和状态逻辑从工作区组件抽离。
6. 分模块迁移现有知识库、简历/画像、岗位、面试和复盘服务；每一项保留原 API 契约。

以上顺序为设计阶段的建议。详细文件清单和可执行步骤待用户审阅本设计文档后再制定。

## 11. 参考

- [Snailclimb/interview-guide README](https://github.com/Snailclimb/interview-guide/blob/master/README.md)
- [Snailclimb/interview-guide AGENTS.md](https://github.com/Snailclimb/interview-guide/blob/master/AGENTS.md)
- [GNU GPL FAQ: AGPLv3 and network use](https://www.gnu.org/licenses/gpl-faq.en.html)
