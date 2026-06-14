# 每日岗位查询模块详细升级计划

## 背景判断

当前岗位采集模块以 `JobSource.url` 为入口，使用 Jsoup 抓取公开页面 HTML，再从列表页 DOM 中提取 `article`、`li`、`.job`、`.position` 等候选块。这个方案适合静态页面，但多数互联网招聘站点是强前端渲染：列表页初始 HTML 只有应用壳，真实岗位需要进入站点后触发搜索、分页接口或详情接口才能拿到。

因此，“每日查询岗位查不到”的主要原因不是解析器本身，而是采集路径不完整：现在只抓入口页，没有执行站内搜索、分页、详情页二次抓取，也没有针对公司招聘站的 API 适配。

## 目标

- 保留现有岗位中心、岗位库、每日定时任务、采集日志和手动导入能力。
- 将采集流程从“抓一个页面”升级为“搜索入口 -> 列表解析 -> 详情页解析 -> 入库去重”。
- 优先支持 Java / 后端 / Spring Boot / Redis / MySQL / 实习 / 校招等关键词。
- 失败时给出明确日志，能区分 JS 壳页面、无搜索结果、详情页失败、字段不完整、反爬限制。

## 数据模型扩展

`job_source` 建议新增字段：

- `source_type`：`STATIC_HTML`、`SEARCH_PAGE`、`JSON_API`、`MANUAL`
- `search_url_template`：搜索 URL 模板，例如包含 `{keyword}`、`{page}`、`{city}`
- `detail_url_selector`：列表页详情链接选择器
- `list_item_selector`：列表项选择器
- `next_page_selector`：下一页选择器，静态分页使用
- `api_method`：`GET` / `POST`
- `api_payload_template`：JSON API 请求体模板
- `headers_json`：必要请求头
- `enabled_detail_fetch`：是否进入详情页
- `max_detail_pages`：每个来源每天最多进入多少个详情链接

为了兼容当前版本，这些字段可以先放到 `metadata_json`，后续再拆列。

## 采集流程

1. 读取启用的 `JobSource`。
2. 根据 `source_type` 选择采集策略：
   - `STATIC_HTML`：沿用当前 Jsoup 页面提取。
   - `SEARCH_PAGE`：按关键词和页码生成搜索 URL，抓列表页。
   - `JSON_API`：直接请求招聘站内部公开接口。
3. 提取列表候选项：
   - 标题、公司、城市、发布时间、详情链接。
   - 如果列表项字段不完整，但有详情链接，先不判无效。
4. 进入详情页：
   - 抓取详情页 HTML 或 API。
   - 提取完整 JD、任职要求、技能关键词、投递链接。
5. 调用现有 `JobDescriptionParser` 标准化为 `JobPosting`。
6. 用 `sourceUrl + title + city + contentHash` 去重入库。
7. 写入 `job_collect_log`：
   - `added`
   - `updated`
   - `skipped`
   - `failed`
   - `errorMessage`
   - 建议新增 `searched`、`detailFetched`、`detailFailed`

## 后端设计

新增接口/类：

- `JobSourceStrategy`
  - `boolean supports(JobSource source)`
  - `List<JobCrawlItem> crawl(JobSource source, JobCollectOptions options)`
- `StaticHtmlJobSourceStrategy`
- `SearchPageJobSourceStrategy`
- `JsonApiJobSourceStrategy`
- `JobDetailFetcher`
- `JobSearchTemplateRenderer`
- `JobCollectOptions`

改造点：

- `JobCrawler` 从单类逻辑拆成 strategy 编排器。
- `JobCollectorService.collect()` 不再只接收 `maxPagesPerSource`，而是接收关键词、页数、详情页上限等配置。
- `JobCrawlItem` 扩展字段：`title`、`company`、`city`、`detailUrl`、`sourceUrl`、`rawListText`、`rawDetailText`。

## 前端设计

岗位源配置页增加：

- 采集类型选择：静态页面 / 搜索页 / JSON API
- 搜索 URL 模板输入
- 关键词预览
- 是否进入详情页开关
- 单源测试采集按钮
- 测试结果预览：搜索 URL、抓到列表数、进入详情数、可解析岗位数、失败原因

岗位中心增加：

- “只测试不入库”
- “查看原始采集内容”
- “查看失败详情”

## 实施阶段

### 第一阶段：定位和日志增强

- 在 `JobCollectLog` 中记录抓取到的原始候选数量、过滤数量、详情抓取数量。
- 采集失败时写明：JS 壳页面、无候选 DOM、关键词不匹配、字段缺失、请求失败。
- 前端日志表展示更清楚的失败原因。

### 第二阶段：详情页二次抓取

- `JobCrawler` 提取列表项中的详情链接后，进入详情页抓取正文。
- 列表页文本和详情页文本合并后再交给 `JobDescriptionParser`。
- 控制每个来源每日详情页数量，避免请求过多。

### 第三阶段：搜索 URL 模板

- 为每个公司源配置 `search_url_template`。
- 按关键词、页码生成搜索 URL。
- 支持关键词轮询：`Java`、`后端`、`Spring Boot`、`Redis`、`MySQL`、`实习`、`校招`。

### 第四阶段：JSON API 适配

- 对强 JS 招聘站优先分析其公开搜索 API。
- 使用 `JsonApiJobSourceStrategy` 直接请求接口。
- 每个站点只维护最小配置，不在业务代码里硬编码复杂页面逻辑。

### 第五阶段：调度和去重优化

- 每日任务按来源分批执行。
- 同一岗位只更新时间和内容 hash，不重复插入。
- 对连续失败的来源降频，并在前端标记“需要维护”。

## 验证用例

- 静态 HTML 列表页能继续采集。
- 列表页只有标题和详情链接时，进入详情页后能成功入库。
- JS 壳页面不入库，并记录明确失败原因。
- 搜索页模板能按关键词和页码生成多个 URL。
- 同一岗位第二天采集进入 updated，而不是重复 added。
- 详情页失败不影响同来源其他岗位。

## 优先级建议

先做“详情页二次抓取 + 日志增强”。这一步最贴近当前问题，也不会大改数据结构。等能确认哪些公司源仍然查不到，再针对这些来源补 `SEARCH_PAGE` 或 `JSON_API`。
