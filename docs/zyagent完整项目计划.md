# zyagent 完整项目计划

## Summary

zyagent 定位为面向计算机专业学生和 Java 后端求职者的个人知识成长与求职 Agent 平台。

核心闭环：

学习资料沉淀 -> 项目经验整理 -> 简历优化 -> 招聘 JD 导入/解析 -> 岗位匹配 -> 模拟面试 -> 面试复盘 -> 学习补强。

## Tech Stack

- Java 17, Spring Boot 3.x
- Spring AI / Spring AI Alibaba integration points
- DeepSeek
- RAG, ReAct, Plan-Executor, Multi-Agent
- MCP-style Tool Adapter
- Milvus, Redis, MySQL
- Vue 3, Vite, Element Plus

## Configuration

```env
DEEPSEEK_API_KEY=
MILVUS_HOST=localhost
MILVUS_PORT=19530
REDIS_HOST=localhost
REDIS_PORT=6379
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=zyagent
MYSQL_USERNAME=root
MYSQL_PASSWORD=
DEEPSEEK_BASE_URL=https://api.deepseek.com/anthropic
DEEPSEEK_CHAT_MODEL=deepseek-v4-flash
```

## Implemented MVP

- Spring Boot project skeleton and API structure.
- Vue workbench with pages for dashboard, knowledge base, chat, jobs, resume matching, interview, and review.
- Manual JD import, mock job source, JD parsing, and resume matching.
- Document upload, text extraction, chunking, and in-memory knowledge search.
- MCP-style tool registry with first tools for knowledge search, JD parsing, resume matching, interview questions, and study plans.
- Multi-Agent role routing and Plan-Executor style step generation.
- DeepSeek Anthropic-compatible API call and local fallback answer when API key is empty.
- Redis, Milvus, and MySQL configuration placeholders plus Docker Compose services.

## Next Implementation Priorities

1. Replace in-memory document/job storage with MySQL repositories.
2. Add real Milvus vector schema, embedding generation, upsert, and similarity search.
3. Replace `AiChatService` fallback with Spring AI ChatClient and DeepSeek streaming.
4. Persist chat messages and tool-call records.
5. Add independent MCP Server compatibility after the embedded Tool Adapter is stable.
6. Add authentication only after the single-user demo chain is stable.

## Manual Preparation

- DeepSeek API Key.
- Local Milvus, Redis, and MySQL, or Docker Compose.
- One real and one desensitized resume.
- One or two project README files.
- Study notes for Java, Spring Boot, MySQL, Redis, JVM, network, OS, and common interview questions.
- Ten to twenty JD samples from major internet companies.
- Two to three interview review samples.

## Resume Description

zyagent 是一个基于 Spring Boot、Spring AI Alibaba 和 DeepSeek 构建的个人知识成长与求职 Agent 平台，融合 RAG、ReAct、Plan-Executor、Multi-Agent 和 MCP Tool Adapter，支持学习资料问答、简历优化、主流互联网岗位 JD 解析、岗位匹配、模拟面试和复盘报告生成。系统使用 Milvus 构建个人向量知识库，使用 Redis 管理会话与任务状态，并通过工具调用机制实现 JD 解析、简历匹配、面试题生成和学习计划生成等能力。
