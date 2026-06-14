# Java 后端开发简历（脱敏演示版）

## 基本信息

- 姓名：张同学（脱敏）
- 求职方向：Java 后端开发 / 后端研发实习
- 学历：本科，计算机科学与技术，2027 届
- 个人定位：熟悉 Java、Spring Boot、MySQL、Redis，具备电商后端项目和 RAG Agent 项目经验。

## 技能栈

- 编程语言：Java、SQL、JavaScript
- 后端框架：Spring Boot、Spring MVC、MyBatis、Spring Security
- 数据库：MySQL，熟悉索引、事务、锁、慢 SQL 分析
- 缓存与中间件：Redis，了解缓存穿透、击穿、雪崩、分布式锁
- 工程能力：Maven、Git、Docker、Linux 基础部署
- AI 工程：RAG、向量数据库 Milvus、Agent 工具调用、SSE 流式输出

## 项目经历

### zyagent 个人知识成长与求职 Agent 平台

项目背景：面向 Java 后端求职者，构建集学习资料问答、简历优化、JD 分析、岗位匹配、模拟面试和复盘报告于一体的个人 Agent 平台。

技术栈：Spring Boot、DeepSeek、Milvus、Redis、MySQL、Vue 3、Element Plus。

个人职责：

- 设计 RAG 文档上传、解析、切片、向量检索流程。
- 实现 SkillRouter，将学习导师、简历顾问、岗位分析、面试官、复盘教练抽象为内部 Skill。
- 接入 DeepSeek token 级 streaming，通过 SSE 推送到前端。
- 使用 MySQL 持久化会话、消息、工具调用、文档和岗位数据。
- 设计 ChatGPT 风格流式对话界面，展示 Skill、Plan、Tool Trace 和引用内容。

项目亮点：

- 将求职流程拆解为“上传资料 -> JD 分析 -> 匹配报告 -> 模拟面试 -> 复盘补强”的闭环。
- Agent 工具调用链路可观测，便于面试中解释 ReAct、Plan-Executor 和 MCP Tool Adapter。
- Milvus 不可用时支持关键词降级，保证演示链路稳定。

### GGmall 电商订单与库存系统

项目背景：模拟电商下单、库存扣减、支付回调和订单状态流转。

技术栈：Spring Boot、MyBatis、MySQL、Redis、RabbitMQ、Docker。

个人职责：

- 负责商品、购物车、订单、库存模块接口设计。
- 使用 Redis 缓存商品详情和库存热点数据，降低 MySQL 查询压力。
- 使用分布式锁控制秒杀库存扣减，避免超卖。
- 通过消息队列异步处理支付回调和订单超时关闭。
- 对慢 SQL 添加联合索引，将订单列表查询耗时从 900ms 降到 120ms 左右。

## 实习经历

### 某互联网公司后端研发实习（脱敏）

- 参与内部运营系统接口开发，负责需求评审、接口设计、编码和联调。
- 优化批量查询接口，减少 N+1 查询，将接口响应时间降低约 60%。
- 补充单元测试和接口文档，提高模块交付质量。

## 竞赛与实践

- 校内软件设计大赛二等奖，负责后端服务和数据库设计。
- 维护个人技术博客，整理 Java、MySQL、Redis、JVM 面试笔记。

## 自我评价

具备较强的问题拆解和工程落地能力，能把 AI Agent、RAG 和传统后端业务结合到真实求职场景中。希望在后端研发岗位持续提升系统设计、性能优化和复杂业务建模能力。
