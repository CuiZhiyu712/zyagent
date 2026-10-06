# 检索评测

## 目的

对 Hybrid RAG 的**融合与重排**阶段做可重复的离线评测，避免用单次输出宣称质量提升。
评测集固化每一路（向量 / 关键词）的候选排名 fixture，因此**不需要 Milvus 或 MySQL**即可重跑。

评测范围说明：这里衡量的是「给定两路召回结果后，融合 + 重排的排序质量」，
不覆盖 Embedding 模型本身的质量、也不覆盖 MySQL 关键词 SQL 的实际召回（那属于集成测试）。

## 运行方式

```bash
./.tools/apache-maven-3.9.9/bin/mvn.cmd -q -f backend/pom.xml test -Dtest=RagRetrievalEvaluationTest
```

测试会在控制台打印一行 baseline，例如：

```
RAG fusion baseline (n=8): recall@5=0.875 mrr@10=0.688 ndcg@10=0.737 two-channel-coverage=0.875
```

## 用例格式（`rag-retrieval-cases.jsonl`）

每行一个 JSON 对象：

```json
{"id":"exact-technical","query":"Redis 缓存一致性","relevant":["doc-redis:3"],
 "vector":["doc-redis:3","doc-mysql:1","doc-other:0"],"keyword":["doc-redis:3","doc-mysql:1"],
 "vectorStatus":"ok","keywordStatus":"ok"}
```

| 字段 | 含义 |
| --- | --- |
| `id` | 用例标识 |
| `query` | 查询文本 |
| `relevant` | 期望命中的 chunk key（`documentId:chunkIndex`） |
| `vector` | 向量通道返回的候选排名（含 chunk key） |
| `keyword` | 关键词通道返回的候选排名 |
| `vectorStatus` / `keywordStatus` | 该通道状态：`ok` / `failed` / `unavailable` |

覆盖场景：技术名词精确查询、自然语言语义查询、同义词（仅关键词命中）、多文档冲突、
知识类型过滤、空结果、向量单侧故障、关键词单侧故障。

## 指标定义

- **Recall@5**：相关 chunk 出现在前 5 的比例（按相关集合平均）。
- **MRR@10**：第一个相关结果排名的倒数。
- **nDCG@10**：位置折损的排序质量，`DCG/IDCG`，对数底为 2。
- **两路候选覆盖率**：相关 chunk 至少被一路召回到的用例占比。
- **rerank 前后排名变化**：默认 `rrf_fallback` 下与融合顺序一致；接入外部 cross-encoder 后应重跑并记录变化。

## 基线

| 配置 | k | recall@5 | mrr@10 | ndcg@10 | 覆盖率 |
| --- | --- | --- | --- | --- | --- |
| RRF(k=60) + `rrf_fallback`，n=8 | 60 | 0.875 | 0.688 | 0.737 | 0.875 |

> 记录硬件/模型/数据集与配置；本节数值需与实际运行输出一致，不得手工抬高。
> 未配置外部 reranker 时如实记为 `rrf_fallback`，不声称重排已成功运行。

## 改动约定

1. 修改融合/重排逻辑后重跑，并把新的实测数值写入上表。
2. 需要调整阈值时，先说明原因（例如新增更难的用例），不要在 README 与实际输出不一致时放行。
