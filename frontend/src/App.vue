<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">ZY</div>
        <div>
          <h1>zyagent</h1>
          <p>成长与求职 Agent</p>
        </div>
      </div>
      <el-menu v-model:default-active="active" class="nav" @select="active = $event">
        <el-menu-item index="dashboard">工作台</el-menu-item>
        <el-menu-item index="knowledge">知识库</el-menu-item>
        <el-menu-item index="chat">智能对话</el-menu-item>
        <el-menu-item index="jobs">岗位中心</el-menu-item>
        <el-menu-item index="match">简历匹配</el-menu-item>
        <el-menu-item index="interview">模拟面试</el-menu-item>
        <el-menu-item index="review">复盘报告</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header v-if="active !== 'chat'" class="topbar">
        <div>
          <h2>{{ pageTitle }}</h2>
          <p>{{ pageSubtitle }}</p>
        </div>
        <el-tag type="success" effect="plain">DeepSeek / Milvus / Redis 已配置</el-tag>
      </el-header>

      <el-main :class="['main', active === 'chat' ? 'chat-main-shell' : '']">
        <section v-if="active === 'dashboard'" class="grid">
          <div class="metric"><span>知识文档</span><strong>{{ documents.length }}</strong></div>
          <div class="metric"><span>岗位样本</span><strong>{{ jobs.length }}</strong></div>
          <div class="metric"><span>Agent 角色</span><strong>5</strong></div>
          <div class="metric"><span>MCP 工具</span><strong>5+</strong></div>
          <el-card class="wide" shadow="never">
            <template #header>演示链路</template>
            <el-steps :active="2" finish-status="success" simple>
              <el-step title="上传资料" />
              <el-step title="导入 JD" />
              <el-step title="岗位匹配" />
              <el-step title="模拟面试" />
              <el-step title="复盘补强" />
            </el-steps>
          </el-card>
        </section>

        <section
          v-if="active === 'knowledge'"
          :class="['panel', 'knowledge-panel', knowledgeDragging ? 'dragging' : '']"
          @dragenter.prevent="handleKnowledgeDragEnter"
          @dragover.prevent="handleKnowledgeDragOver"
          @dragleave.prevent="handleKnowledgeDragLeave"
          @drop.prevent="handleKnowledgeDrop"
        >
          <div class="toolbar">
            <el-select v-model="knowledgeType" style="width: 180px">
              <el-option label="学习资料" value="STUDY" />
              <el-option label="简历" value="RESUME" />
              <el-option label="项目文档" value="PROJECT" />
              <el-option label="岗位 JD" value="JOB" />
              <el-option label="复盘" value="REVIEW" />
            </el-select>
            <input type="file" accept=".pdf,.doc,.docx,.md,.txt" @change="upload" />
          </div>
          <div class="knowledge-dropzone">
            <strong>{{ knowledgeDragging ? '松开即可上传到知识库' : '拖拽文件到这里上传' }}</strong>
            <span>当前类型：{{ knowledgeTypeName }}</span>
          </div>
          <el-table :data="documents" height="480">
            <el-table-column prop="filename" label="文件名" />
            <el-table-column prop="knowledgeType" label="类型" width="140" />
            <el-table-column prop="parseStatus" label="状态" width="120" />
            <el-table-column prop="chunkCount" label="切片" width="100" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="danger" plain @click="deleteDocument(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <ChatWorkspace v-if="active === 'chat'" />

        <section v-if="active === 'jobs'" class="split">
          <el-card class="wide boss-collect" shadow="never">
            <template #header>
              <div class="card-header-row">
                <span>BOSS 直聘聚合采集</span>
                <el-tag :type="bossStatus.probablyLoggedIn ? 'success' : 'warning'" effect="plain">
                  {{ bossStatus.message || '未连接' }}
                </el-tag>
              </div>
            </template>
            <div class="boss-grid">
              <el-input v-model="bossForm.city" placeholder="城市，例如 北京" />
              <el-input v-model="bossKeywordText" placeholder="关键词，用逗号分隔" />
              <el-input-number v-model="bossForm.maxPages" :min="1" :max="10" />
              <el-input-number v-model="bossForm.maxJobs" :min="1" :max="120" />
              <el-switch v-model="bossForm.fetchDetail" active-text="抓详情" inactive-text="只抓列表" />
            </div>
            <div class="job-actions">
              <el-button :loading="openingBoss" plain @click="openBossSession">打开 BOSS 登录窗口</el-button>
              <el-button :loading="collectingBoss" type="primary" @click="collectBossJobs">采集 BOSS 岗位</el-button>
              <el-button plain @click="refreshBossStatus">刷新状态</el-button>
            </div>
            <p class="boss-note">仅使用本地浏览器登录会话读取页面；如出现登录或验证码，请先在打开的浏览器中手动完成。</p>
          </el-card>
          <el-card shadow="never">
            <template #header>导入 JD</template>
            <el-input v-model="jobText" type="textarea" :rows="12" placeholder="粘贴公司、岗位、城市、职责、任职要求等内容" />
            <el-button type="primary" @click="importJob">导入岗位</el-button>
          </el-card>
          <el-card shadow="never">
            <template #header>
              <div class="card-header-row">
                <span>岗位列表</span>
                <div class="header-actions">
                  <el-button :loading="cleaningJobs" plain @click="cleanInvalidJobs">清理无效采集</el-button>
                  <el-button :loading="collectingJobs" type="primary" plain @click="collectAllJobs">立即采集全部</el-button>
                </div>
              </div>
            </template>
            <el-table :data="jobs" height="420" @row-click="selectedJob = $event">
              <el-table-column prop="company" label="公司" width="100" />
              <el-table-column prop="title" label="岗位" />
              <el-table-column prop="city" label="城市" width="90" />
              <el-table-column label="来源" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ sourceLabel(row.sourceName || row.sourceUrl) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="selectedJob" class="job-detail">
              <div class="job-detail-head">
                <div>
                  <h3>{{ selectedJob.company }} · {{ selectedJob.title }}</h3>
                  <p>{{ selectedJob.city }} / {{ selectedJob.jobType }} / {{ selectedJob.direction }}</p>
                </div>
                <el-tag v-if="isInvalidJob(selectedJob)" type="warning" effect="plain">采集结果不完整</el-tag>
              </div>
              <div class="job-tags">
                <el-tag v-for="skill in selectedJob.skills || []" :key="skill" size="small" effect="plain">{{ skill }}</el-tag>
              </div>
              <dl class="job-meta">
                <div><dt>来源</dt><dd>{{ selectedJob.sourceName || sourceLabel(selectedJob.sourceUrl) }}</dd></div>
                <div><dt>最近采集</dt><dd>{{ selectedJob.lastSeenAt || selectedJob.collectedAt || '-' }}</dd></div>
                <div><dt>投递链接</dt><dd class="job-link">{{ selectedJob.sourceUrl || '-' }}</dd></div>
              </dl>
              <div class="job-actions">
                <el-button v-if="selectedJob.sourceUrl" type="primary" plain @click="openJobLink(selectedJob.sourceUrl)">打开投递链接</el-button>
              </div>
              <pre class="job-raw">{{ selectedJob.rawText || '暂无 JD 原文' }}</pre>
            </div>
          </el-card>
          <el-card class="wide" shadow="never">
            <template #header>
              <div class="card-header-row">
                <span>自动采集源</span>
                <small>关键词：Java, 后端, Spring Boot, Redis, MySQL, 校招, 实习, agent, 大模型</small>
              </div>
            </template>
            <el-table :data="jobSources" height="260">
              <el-table-column prop="company" label="公司" width="90" />
              <el-table-column prop="name" label="来源" width="160" />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ row.sourceType || 'STATIC_HTML' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="详情" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.enabledDetailFetch ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.enabledDetailFetch ? '开启' : '关闭' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="url" label="公开 URL" min-width="260" show-overflow-tooltip />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.lastStatus === 'FAILED' ? 'danger' : 'success'" effect="plain">
                    {{ row.lastStatus || 'NEW' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="lastCollectedAt" label="最近采集" width="180" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button size="small" :loading="collectingSourceId === row.id" @click="collectOneSource(row.id)">采集</el-button>
                  <el-button size="small" plain @click="editJobSource(row)">配置</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
          <el-card class="wide" shadow="never">
            <template #header>采集日志</template>
            <el-table :data="collectLogs" height="220">
              <el-table-column prop="startedAt" label="开始时间" width="180" />
              <el-table-column prop="sourceName" label="来源" min-width="160" />
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="added" label="新增" width="80" />
              <el-table-column prop="updated" label="更新" width="80" />
              <el-table-column prop="skipped" label="跳过" width="80" />
              <el-table-column prop="failed" label="失败" width="80" />
              <el-table-column prop="searched" label="候选" width="80" />
              <el-table-column prop="detailFetched" label="详情" width="80" />
              <el-table-column prop="errorMessage" label="失败原因" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-card>
        </section>

        <section v-if="active === 'match'" class="split">
          <el-card shadow="never">
            <template #header>简历内容</template>
            <el-select v-model="selectedJobId" class="full" placeholder="选择岗位">
              <el-option v-for="job in jobs" :key="job.id" :label="`${job.company} - ${job.title}`" :value="job.id" />
            </el-select>
            <el-input v-model="resumeText" type="textarea" :rows="10" placeholder="粘贴脱敏简历或项目经历" />
            <el-input v-model="resumeSkills" placeholder="技能，用逗号分隔，例如 Java,Spring Boot,MySQL,Redis" />
            <el-button type="primary" @click="matchResume">生成匹配报告</el-button>
          </el-card>
          <el-card shadow="never">
            <template #header>匹配报告</template>
            <pre class="output">{{ matchResult }}</pre>
          </el-card>
        </section>

        <section v-if="active === 'interview'" class="split">
          <el-card shadow="never">
            <template #header>创建模拟面试</template>
            <el-select v-model="interview.jobId" class="full">
              <el-option v-for="job in jobs" :key="job.id" :label="`${job.company} - ${job.title}`" :value="job.id" />
            </el-select>
            <el-select v-model="interview.interviewType" class="full">
              <el-option label="Java 基础" value="Java 基础" />
              <el-option label="项目深挖" value="项目深挖" />
              <el-option label="系统设计" value="系统设计" />
              <el-option label="综合面" value="综合面" />
            </el-select>
            <el-button type="primary" @click="simulateInterview">开始面试</el-button>
          </el-card>
          <el-card shadow="never">
            <template #header>首轮问题</template>
            <pre class="output">{{ interviewResult }}</pre>
          </el-card>
        </section>

        <section v-if="active === 'review'" class="split">
          <el-card shadow="never">
            <template #header>面试复盘</template>
            <el-input v-model="reviewText" type="textarea" :rows="12" placeholder="记录面试问题、你的回答、没答好的地方" />
            <el-button type="primary" @click="submitReview">生成补强计划</el-button>
          </el-card>
          <el-card shadow="never">
            <template #header>复盘结果</template>
            <pre class="output">{{ reviewResult }}</pre>
          </el-card>
        </section>
      </el-main>
    </el-container>
  </el-container>
  <el-dialog v-model="sourceFormVisible" title="采集源配置" width="720px">
    <el-form :model="sourceForm" label-width="110px" class="source-form">
      <el-form-item label="公司">
        <el-input v-model="sourceForm.company" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="sourceForm.name" />
      </el-form-item>
      <el-form-item label="入口 URL">
        <el-input v-model="sourceForm.url" />
      </el-form-item>
      <el-form-item label="采集类型">
        <el-segmented v-model="sourceForm.sourceType" :options="['STATIC_HTML', 'SEARCH_PAGE']" />
      </el-form-item>
      <el-form-item label="搜索模板">
        <el-input v-model="sourceForm.searchUrlTemplate" placeholder="例如 https://example.com/search?keyword={keyword}&page={page}" />
      </el-form-item>
      <el-form-item label="列表选择器">
        <el-input v-model="sourceForm.listItemSelector" placeholder="默认自动识别 article、li、job-item 等" />
      </el-form-item>
      <el-form-item label="详情链接选择器">
        <el-input v-model="sourceForm.detailUrlSelector" placeholder="默认 a[href]" />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="sourceForm.keywords" />
      </el-form-item>
      <el-form-item label="进入详情页">
        <el-switch v-model="sourceForm.enabledDetailFetch" />
      </el-form-item>
      <el-form-item label="详情页上限">
        <el-input-number v-model="sourceForm.maxDetailPages" :min="1" :max="50" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="sourceForm.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="sourceFormVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingSource" @click="saveSourceConfig">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from './api'
import ChatWorkspace from './components/chat/ChatWorkspace.vue'
import { extractDroppedFile, knowledgeTypeLabel } from './chat/uploadActions'

const active = ref('dashboard')
const documents = ref([])
const jobs = ref([])
const jobSources = ref([])
const collectLogs = ref([])
const knowledgeType = ref('STUDY')
const jobText = ref('公司：字节跳动\n岗位：Java 后端开发实习生\n城市：北京\n岗位类型：实习\n任职要求：熟悉 Java、Spring Boot、MySQL、Redis、JVM')
const selectedJob = ref(null)
const selectedJobId = ref('')
const resumeText = ref('熟悉 Java、Spring Boot、MySQL，做过 Redis 缓存和订单系统项目。')
const resumeSkills = ref('Java,Spring Boot,MySQL,Redis')
const matchResult = ref('')
const interview = reactive({ jobId: '', interviewType: '项目深挖', difficulty: '中等' })
const interviewResult = ref('')
const reviewText = ref('')
const reviewResult = ref('')
const collectingJobs = ref(false)
const collectingSourceId = ref('')
const cleaningJobs = ref(false)
const openingBoss = ref(false)
const collectingBoss = ref(false)
const sourceFormVisible = ref(false)
const savingSource = ref(false)
const sourceForm = reactive(defaultSourceForm())
const bossStatus = reactive({ opened: false, currentUrl: '', probablyLoggedIn: false, message: '' })
const bossForm = reactive({ city: '北京', maxPages: 3, maxJobs: 60, fetchDetail: true })
const bossKeywordText = ref('Java 后端 实习,AI Agent 后端 实习')
const knowledgeDragging = ref(false)
let knowledgeDragDepth = 0

const titles = {
  dashboard: ['工作台', '查看求职准备闭环的当前状态'],
  knowledge: ['知识库', '上传简历、项目、学习笔记和复盘材料'],
  chat: ['智能对话', '按 Agent 角色进行 RAG 问答和工具调用'],
  jobs: ['岗位中心', '导入、解析和管理主流互联网岗位 JD'],
  match: ['简历匹配', '对比简历与岗位要求，生成优化建议'],
  interview: ['模拟面试', '基于岗位和个人资料连续追问'],
  review: ['复盘报告', '沉淀薄弱点并生成补强计划']
}

const pageTitle = computed(() => titles[active.value][0])
const pageSubtitle = computed(() => titles[active.value][1])
const knowledgeTypeName = computed(() => knowledgeTypeLabel(knowledgeType.value))

async function refresh() {
  try {
    documents.value = await api.listDocuments()
    jobs.value = await api.listJobs()
    await refreshJobCollection()
    if (!selectedJobId.value && jobs.value.length) selectedJobId.value = jobs.value[0].id
    if (!interview.jobId && jobs.value.length) interview.jobId = jobs.value[0].id
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function refreshJobCollection() {
  try {
    jobSources.value = await api.listJobSources()
    collectLogs.value = await api.listJobCollectLogs()
    await refreshBossStatus()
  } catch {
    // The core job list remains usable while collection APIs are starting.
  }
}

async function upload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  await uploadKnowledgeFile(file)
  event.target.value = ''
}

async function uploadKnowledgeFile(file) {
  if (!file) return
  await api.uploadDocument(file, knowledgeType.value)
  ElMessage.success('上传并解析完成')
  await refresh()
}

function handleKnowledgeDragEnter() {
  knowledgeDragDepth += 1
  knowledgeDragging.value = true
}

function handleKnowledgeDragOver() {
  knowledgeDragging.value = true
}

function handleKnowledgeDragLeave() {
  knowledgeDragDepth = Math.max(0, knowledgeDragDepth - 1)
  if (knowledgeDragDepth === 0) {
    knowledgeDragging.value = false
  }
}

async function handleKnowledgeDrop(event) {
  knowledgeDragDepth = 0
  knowledgeDragging.value = false
  const file = extractDroppedFile(event)
  if (!file) return
  try {
    await uploadKnowledgeFile(file)
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
  }
}

async function deleteDocument(documentId) {
  try {
    await ElMessageBox.confirm('确定要删除该文档吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await api.deleteDocument(documentId)
    ElMessage.success('文档已删除')
    await refresh()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

async function importJob() {
  const job = await api.importJobText({ rawText: jobText.value, sourceUrl: 'manual://input' })
  selectedJob.value = job
  selectedJobId.value = job.id
  ElMessage.success('岗位已导入')
  await refresh()
}

async function collectAllJobs() {
  collectingJobs.value = true
  try {
    const result = await api.collectJobs()
    ElMessage.success(`采集完成：新增 ${result.added}，更新 ${result.updated}，失败 ${result.failed}`)
    await refresh()
  } finally {
    collectingJobs.value = false
  }
}

async function collectOneSource(id) {
  collectingSourceId.value = id
  try {
    const result = await api.collectJobSource(id)
    ElMessage.success(`采集完成：新增 ${result.added}，更新 ${result.updated}，失败 ${result.failed}`)
    await refresh()
  } finally {
    collectingSourceId.value = ''
  }
}

async function cleanInvalidJobs() {
  cleaningJobs.value = true
  try {
    const count = await api.deleteInvalidCollectedJobs()
    ElMessage.success(`已清理 ${count} 条无效自动采集岗位`)
    selectedJob.value = null
    await refresh()
  } finally {
    cleaningJobs.value = false
  }
}

async function refreshBossStatus() {
  try {
    Object.assign(bossStatus, await api.bossSessionStatus())
  } catch {
    Object.assign(bossStatus, { opened: false, currentUrl: '', probablyLoggedIn: false, message: 'BOSS 采集接口未就绪' })
  }
}

async function openBossSession() {
  openingBoss.value = true
  try {
    Object.assign(bossStatus, await api.openBossSession())
    ElMessage.success('已打开 BOSS 浏览器窗口')
  } catch (error) {
    ElMessage.error(error.message || '打开 BOSS 失败')
  } finally {
    openingBoss.value = false
  }
}

async function collectBossJobs() {
  collectingBoss.value = true
  try {
    const result = await api.collectBossJobs({
      city: bossForm.city,
      keywords: bossKeywordText.value.split(/[,，]/).map(item => item.trim()).filter(Boolean),
      maxPages: bossForm.maxPages,
      maxJobs: bossForm.maxJobs,
      fetchDetail: bossForm.fetchDetail
    })
    ElMessage.success(`BOSS 采集完成：新增 ${result.added}，更新 ${result.updated}，失败 ${result.failed}`)
    await refresh()
  } catch (error) {
    ElMessage.error(error.message || 'BOSS 采集失败')
  } finally {
    collectingBoss.value = false
  }
}

function defaultSourceForm() {
  return {
    id: '',
    company: '',
    name: '',
    url: '',
    enabled: true,
    keywords: 'Java,后端,Spring Boot,Redis,MySQL,实习,校招',
    sourceType: 'SEARCH_PAGE',
    searchUrlTemplate: '',
    listItemSelector: '',
    detailUrlSelector: '',
    enabledDetailFetch: true,
    maxDetailPages: 12
  }
}

function editJobSource(source) {
  Object.assign(sourceForm, defaultSourceForm(), source, {
    sourceType: source.sourceType || 'SEARCH_PAGE',
    enabledDetailFetch: Boolean(source.enabledDetailFetch),
    maxDetailPages: source.maxDetailPages || 12
  })
  sourceFormVisible.value = true
}

async function saveSourceConfig() {
  savingSource.value = true
  try {
    const payload = { ...sourceForm }
    if (payload.id) {
      await api.updateJobSource(payload.id, payload)
    } else {
      await api.saveJobSource(payload)
    }
    ElMessage.success('采集源已保存')
    sourceFormVisible.value = false
    await refreshJobCollection()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    savingSource.value = false
  }
}

function openJobLink(url) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

function isInvalidJob(job) {
  return !job || job.company === '未标注' || job.title === '未标注' || String(job.rawText || '').toLowerCase().includes('enable javascript to run this app')
}

function sourceLabel(value) {
  if (!value) return '未知'
  if (String(value).startsWith('mock')) return 'Mock'
  if (String(value).startsWith('manual')) return '手动'
  if (String(value).startsWith('url')) return 'URL'
  if (String(value).includes('BOSS')) return 'BOSS直聘'
  return '自动采集'
}

async function matchResume() {
  const report = await api.matchResume(selectedJobId.value, {
    resumeText: resumeText.value,
    skills: resumeSkills.value.split(',').map(item => item.trim()).filter(Boolean),
    projects: []
  })
  matchResult.value = JSON.stringify(report, null, 2)
}

async function simulateInterview() {
  const result = await api.simulateInterview(interview)
  interviewResult.value = JSON.stringify(result, null, 2)
}

async function submitReview() {
  const result = await api.submitReview({ content: reviewText.value })
  reviewResult.value = JSON.stringify(result, null, 2)
}

onMounted(refresh)
</script>
