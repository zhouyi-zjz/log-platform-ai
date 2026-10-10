<template>
  <div v-if="booting" class="boot-screen" aria-live="polite">
    <div class="brand-mark"><span></span><span></span><span></span></div>
    <p>正在连接日志平台</p>
  </div>

  <main v-else-if="!user" class="login-page">
    <section class="login-aside">
      <div class="login-brand">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <strong>LogScope</strong>
      </div>
      <div class="login-message">
        <p class="eyebrow">OBSERVABILITY PLATFORM</p>
        <h1>从海量日志中，<br />快速定位每一次异常。</h1>
        <p>统一检索服务、环境与链路日志，让排障过程更直接、更可靠。</p>
      </div>
      <div class="signal-panel" aria-hidden="true">
        <div class="signal-head"><span>实时日志流</span><span class="live-dot">LIVE</span></div>
        <div v-for="(line, index) in signalLines" :key="line.text" class="signal-row">
          <time>{{ line.time }}</time>
          <span :class="['signal-level', `is-${line.level.toLowerCase()}`]">{{ line.level }}</span>
          <span>{{ line.text }}</span>
          <i :style="{ width: `${72 - index * 9}%` }"></i>
        </div>
      </div>
      <small>企业级日志检索与分析平台</small>
    </section>

    <section class="login-main">
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="mobile-brand">
          <div class="brand-mark"><span></span><span></span><span></span></div>
          <strong>LogScope</strong>
        </div>
        <p class="eyebrow">欢迎回来</p>
        <h2>登录日志平台</h2>
        <p class="form-intro">请输入您的平台账号以继续</p>

        <label for="username">用户名</label>
        <el-input
          id="username"
          v-model="loginForm.username"
          :prefix-icon="UserRound"
          placeholder="请输入用户名"
          autocomplete="username"
          size="large"
          maxlength="64"
        />

        <div class="password-label"><label for="password">密码</label></div>
        <el-input
          id="password"
          v-model="loginForm.password"
          :prefix-icon="LockKeyhole"
          placeholder="请输入密码"
          autocomplete="current-password"
          size="large"
          maxlength="128"
          show-password
        />

        <p v-if="loginError" class="form-error" role="alert">{{ loginError }}</p>
        <el-button class="login-button" type="primary" native-type="submit" size="large" :loading="loginLoading">
          登录
          <el-icon v-if="!loginLoading"><ArrowRight /></el-icon>
        </el-button>
        <p class="security-note"><el-icon><ShieldCheck /></el-icon> 登录信息通过安全会话传输</p>
      </form>
    </section>
  </main>

  <div v-else class="app-shell">
    <div v-if="mobileMenu" class="mobile-mask" @click="mobileMenu = false"></div>
    <aside :class="['sidebar', { 'is-open': mobileMenu }]">
      <div class="sidebar-brand">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <strong>LogScope</strong>
        <button class="sidebar-close" title="关闭菜单" @click="mobileMenu = false"><X /></button>
      </div>
      <nav aria-label="主菜单">
        <p>工作台</p>
        <button class="nav-item active"><Search /><span>日志查询</span></button>
        <button class="nav-item" type="button" @click="scrollToAnalysis"><ChartNoAxesCombined /><span>日志分析</span></button>
        <p>管理</p>
          <button class="nav-item" type="button" @click="scrollToTrace"><GitBranch /><span>Trace 视图</span></button>
          <button class="nav-item disabled" disabled><BellRing /><span>告警规则</span></button>
        <button class="nav-item disabled" disabled><Settings /><span>平台设置</span></button>
      </nav>
      <div class="sidebar-foot">
        <span class="health-dot"></span>
        <div><strong>系统运行正常</strong><small>所有服务可用</small></div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <button class="menu-trigger" title="打开菜单" @click="mobileMenu = true"><Menu /></button>
        <div class="breadcrumb"><span>工作台</span><ChevronRight /><strong>日志查询</strong></div>
        <div class="topbar-actions">
          <button class="icon-button" title="刷新查询" @click="runSearch(true)"><RefreshCw /></button>
          <button class="icon-button" title="帮助"><CircleHelp /></button>
          <el-dropdown trigger="click" @command="handleAccountCommand">
            <button class="user-button">
              <span class="avatar">{{ initials }}</span>
              <span class="user-copy"><strong>{{ user.displayName || user.username }}</strong><small>{{ user.admin ? '平台管理员' : '只读用户' }}</small></span>
              <ChevronDown />
            </button>
            <template #dropdown>
              <el-dropdown-menu><el-dropdown-item command="logout"><LogOut />退出登录</el-dropdown-item></el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <div class="page-heading">
          <div><h1>日志查询</h1><p>按时间、服务和链路信息筛选日志，快速定位问题。</p></div>
          <span :class="['query-state', { 'has-error': searchError }]">
            <i></i>{{ searchError ? '查询服务异常' : searched ? '查询服务正常' : '等待首次查询' }}
          </span>
        </div>

        <section class="filter-panel" aria-label="日志查询条件">
          <div class="quick-row">
            <div class="field keyword-field">
              <label>关键字</label>
              <el-input v-model="filters.keyword" :prefix-icon="Search" placeholder="搜索消息、异常堆栈或代码位置" clearable @keyup.enter="submitSearch" />
            </div>
            <div class="field time-field">
              <label>时间范围</label>
              <el-date-picker
                v-model="filters.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="MM-DD HH:mm:ss"
                :shortcuts="timeShortcuts"
              />
            </div>
          </div>

          <div class="filter-grid">
            <div class="field"><label>项目 ID</label><el-input v-model="filters.projectId" placeholder="例如 payment" clearable /></div>
            <div class="field"><label>环境</label><el-select v-model="filters.environment" placeholder="全部环境" clearable><el-option v-for="item in environments" :key="item" :label="item" :value="item" /></el-select></div>
            <div class="field"><label>服务名称</label><el-input v-model="filters.serviceName" placeholder="例如 order-service" clearable /></div>
            <div class="field"><label>日志级别</label><el-select v-model="filters.logLevel" placeholder="全部级别" clearable><el-option v-for="item in levels" :key="item" :label="item" :value="item" /></el-select></div>
            <div class="field"><label>Trace ID</label><el-input v-model="filters.traceId" placeholder="输入 Trace ID" clearable /></div>
            <template v-if="advancedOpen">
              <div class="field"><label>实例 ID</label><el-input v-model="filters.instanceId" placeholder="输入实例 ID" clearable /></div>
              <div class="field"><label>Span ID</label><el-input v-model="filters.spanId" placeholder="输入 Span ID" clearable /></div>
              <div class="field"><label>错误类型</label><el-input v-model="filters.errorType" placeholder="例如 TimeoutException" clearable /></div>
            </template>
          </div>

          <div class="filter-actions">
            <button class="advanced-button" type="button" @click="advancedOpen = !advancedOpen">
              <SlidersHorizontal />{{ advancedOpen ? '收起筛选' : '更多筛选' }}<ChevronDown :class="{ rotated: advancedOpen }" />
            </button>
            <div>
              <el-button @click="resetFilters"><RotateCcw />重置</el-button>
              <el-button type="primary" :loading="searchLoading" @click="submitSearch"><Search />查询日志</el-button>
            </div>
          </div>
        </section>

        <section class="result-panel">
          <div class="result-head">
            <div>
              <h2>查询结果</h2>
              <p v-if="searched && !searchLoading">找到 <strong>{{ total.toLocaleString() }}</strong> 条日志<span v-if="duration">，耗时 {{ duration }} ms</span></p>
              <p v-else>查询结果将按时间倒序展示</p>
            </div>
            <div class="result-tools">
              <el-select v-model="pageSize" aria-label="每页条数" @change="submitSearch"><el-option :value="20" label="20 条/页" /><el-option :value="50" label="50 条/页" /><el-option :value="100" label="100 条/页" /></el-select>
              <button class="icon-button" title="刷新查询" @click="runSearch(true)"><RefreshCw /></button>
            </div>
          </div>

          <section v-if="searched" class="analysis-panel" aria-label="日志分析摘要">
            <header class="analysis-head">
              <div><h3><ChartNoAxesCombined />日志分析</h3><span>当前筛选条件下的 Top 分布</span></div>
              <span v-if="analysisLoading" class="analysis-state">分析中...</span>
              <span v-else-if="analysisError" class="analysis-state is-error">暂不可用</span>
              <span v-else class="analysis-state">{{ analysisCards.length ? '已更新' : '暂无数据' }}</span>
            </header>
            <div v-if="analysisError" class="analysis-error"><CircleAlert /><span>{{ analysisError }}</span></div>
            <div v-else class="analysis-grid">
              <article v-for="card in analysisCards" :key="card.field" class="analysis-card">
                <div class="analysis-card-title"><strong>{{ card.label }}</strong><span>{{ card.total }} 项</span></div>
                <div v-if="card.items.length" class="analysis-bars">
                  <div v-for="item in card.items" :key="item.key" class="analysis-bar-row">
                    <span :title="item.key">{{ item.key }}</span>
                    <div><i :style="{ width: `${item.percent}%` }"></i></div>
                    <b>{{ item.count }}</b>
                  </div>
                </div>
                <p v-else class="analysis-empty">暂无聚合结果</p>
              </article>
            </div>
          </section>

          <div v-if="searchError" class="result-error" role="alert">
            <CircleAlert /><div><strong>查询未完成</strong><span>{{ searchError }}</span></div><el-button @click="runSearch(true)">重试</el-button>
          </div>

          <div class="table-wrap" v-loading="searchLoading">
            <table v-if="rows.length">
              <thead><tr><th class="time-col">时间</th><th class="level-col">级别</th><th class="service-col">服务 / 实例</th><th>日志内容</th><th class="trace-col">Trace ID</th><th class="action-col"></th></tr></thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id" @click="openDetail(row)">
                  <td><time>{{ formatTimestamp(row.timestamp) }}</time></td>
                  <td><span :class="['level-tag', `level-${row.level.toLowerCase()}`]">{{ row.level }}</span></td>
                  <td><strong class="service-name">{{ row.service }}</strong><small>{{ row.instance }}</small></td>
                  <td><p class="log-message" :title="row.message">{{ row.message }}</p><small v-if="row.errorType" class="error-type">{{ row.errorType }}</small></td>
                  <td><button v-if="row.traceId" class="trace-link" type="button" title="按此 Trace 查询" @click.stop="filterByTrace(row.traceId)">{{ shorten(row.traceId) }}</button><span v-else class="muted">—</span></td>
                  <td><button class="row-action" title="查看日志详情"><ChevronRight /></button></td>
                </tr>
              </tbody>
            </table>
            <div v-else-if="!searchLoading" class="empty-state">
              <div><FileSearch /></div>
              <h3>{{ searched ? '没有找到匹配日志' : '等待查询' }}</h3>
              <p>{{ searched ? '尝试调整时间范围或减少筛选条件。' : '设置上方查询条件后开始检索日志。' }}</p>
            </div>
          </div>

          <footer v-if="rows.length" class="pagination">
            <span>第 {{ page }} 页 · 本页 {{ rows.length }} 条</span>
            <div>
              <button :disabled="page === 1 || searchLoading" title="上一页" @click="previousPage"><ChevronLeft /></button>
              <strong>{{ page }}</strong>
              <button :disabled="!hasNext || searchLoading" title="下一页" @click="nextPage"><ChevronRight /></button>
            </div>
          </footer>
        </section>
      </main>
    </section>

    <el-drawer v-model="detailOpen" size="min(680px, 92vw)" :with-header="false" destroy-on-close>
      <div v-if="selectedRow" class="detail-drawer">
        <header><div><span :class="['level-tag', `level-${selectedRow.level.toLowerCase()}`]">{{ selectedRow.level }}</span><h2>日志详情</h2></div><button title="关闭" @click="detailOpen = false"><X /></button></header>
        <div class="detail-summary">
          <div><span>时间</span><strong>{{ formatTimestamp(selectedRow.timestamp, true) }}</strong></div>
          <div><span>服务</span><strong>{{ selectedRow.service }}</strong></div>
          <div><span>日志索引</span><strong>{{ selectedRow.index }}</strong></div>
          <div><span>文档 ID</span><strong>{{ selectedRow.id }}</strong></div>
        </div>
        <section class="detail-actions">
          <el-button :loading="detailLoading" @click="loadContext"><RefreshCw />加载上下文</el-button>
          <el-button v-if="selectedRow.traceId" :loading="traceLoading" @click="loadTrace"><GitBranch />查看 Trace</el-button>
          <span v-if="detailError" class="detail-error">{{ detailError }}</span>
        </section>
        <section><h3>日志内容</h3><pre>{{ selectedRow.message }}</pre></section>
        <section v-if="contextRows.length"><h3>关联上下文（{{ contextRows.length }} 条）</h3><div class="context-list"><button v-for="item in contextRows" :key="item.id" type="button" @click="selectContext(item)"><time>{{ formatTimestamp(item.timestamp) }}</time><span :class="['level-tag', `level-${item.level.toLowerCase()}`]">{{ item.level }}</span><span>{{ item.message }}</span></button></div></section>
        <section><div class="section-title"><h3>原始字段</h3><button title="复制 JSON" @click="copySource(selectedRow.source)"><Copy /></button></div><pre class="json-block">{{ JSON.stringify(selectedRow.source, null, 2) }}</pre></section>
      </div>
    </el-drawer>

    <el-drawer v-model="traceOpen" size="min(860px, 96vw)" :with-header="false" destroy-on-close>
      <div class="trace-drawer">
        <header><div><span class="eyebrow">TRACE CORRELATION</span><h2>链路视图</h2></div><button title="关闭" @click="traceOpen = false"><X /></button></header>
        <div class="trace-summary">
          <div><span>Trace ID</span><strong>{{ traceData?.traceId || selectedRow?.traceId }}</strong></div>
          <div><span>关联日志</span><strong>{{ traceData?.logs?.items?.length || 0 }} 条</strong></div>
          <div><span>Span</span><strong>{{ traceSpans.length }} 个</strong></div>
        </div>
        <p v-if="traceError" class="detail-error">{{ traceError }}</p>
        <div v-if="traceLoading" class="trace-loading">正在加载 Trace...</div>
        <section v-else-if="traceSpans.length" class="trace-waterfall" aria-label="Trace 瀑布图">
          <div v-for="span in traceSpans" :key="span.id" class="trace-span-row">
            <div class="trace-span-label"><strong>{{ span.name }}</strong><small>{{ span.service || 'unknown-service' }}</small></div>
            <div class="trace-span-track"><i :style="{ left: `${span.left}%`, width: `${span.width}%` }"></i></div>
            <time>{{ span.durationMs }} ms</time>
          </div>
        </section>
        <div v-else class="empty-state"><div><GitBranch /></div><h3>暂无 Tempo Span</h3><p>请确认 OTel Collector 与 Tempo 已启用并完成联调。</p></div>
        <section v-if="traceData?.logs?.items?.length" class="trace-log-list">
          <h3>关联日志</h3>
          <button v-for="item in traceData.logs.items" :key="item.id" type="button" @click="selectContext(normalizeLog(item))"><time>{{ formatTimestamp(item.source?.['@timestamp']) }}</time><span>{{ item.source?.message || '日志事件' }}</span></button>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowRight, BellRing, ChartNoAxesCombined, ChevronDown, ChevronLeft, ChevronRight, GitBranch,
  CircleAlert, CircleHelp, Copy, FileSearch, LockKeyhole, LogOut, Menu, RefreshCw,
  RotateCcw, Search, Settings, ShieldCheck, SlidersHorizontal, UserRound, X
} from 'lucide-vue-next'
import { aggregateLogs, ensureCsrf, getCurrentUser, getLogContext, getLogDetail, getTraceCorrelation, login, logout, searchLogs } from '@/api/platform'

const booting = ref(true)
const user = ref(null)
const loginLoading = ref(false)
const loginError = ref('')
const mobileMenu = ref(false)
const advancedOpen = ref(false)
const searchLoading = ref(false)
const searchError = ref('')
const searched = ref(false)
const rows = ref([])
const total = ref(0)
const duration = ref(0)
const page = ref(1)
const pageSize = ref(20)
const cursors = ref([null])
const nextCursor = ref(null)
const detailOpen = ref(false)
const selectedRow = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const contextRows = ref([])
const traceOpen = ref(false)
const traceLoading = ref(false)
const traceError = ref('')
const traceData = ref(null)
const analysisLoading = ref(false)
const analysisError = ref('')
const analysisCards = ref([])
let analysisRequestId = 0

const loginForm = reactive({ username: '', password: '' })
const signalLines = [
  { time: '10:42:18.091', level: 'INFO', text: 'request completed in 42ms' },
  { time: '10:42:18.126', level: 'WARN', text: 'retrying downstream request' },
  { time: '10:42:18.203', level: 'INFO', text: 'trace context propagated' },
  { time: '10:42:18.371', level: 'ERROR', text: 'upstream response timeout' }
]
const environments = ['production', 'staging', 'testing', 'development']
const levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

function defaultRange() {
  const end = new Date()
  return [new Date(end.getTime() - 15 * 60 * 1000), end]
}

const filters = reactive({
  keyword: '', timeRange: defaultRange(), projectId: '', environment: '', serviceName: '',
  logLevel: '', traceId: '', instanceId: '', spanId: '', errorType: ''
})

const timeShortcuts = [
  { text: '最近 15 分钟', value: () => defaultRange() },
  { text: '最近 1 小时', value: () => { const end = new Date(); return [new Date(end.getTime() - 3600000), end] } },
  { text: '最近 6 小时', value: () => { const end = new Date(); return [new Date(end.getTime() - 21600000), end] } },
  { text: '最近 24 小时', value: () => { const end = new Date(); return [new Date(end.getTime() - 86400000), end] } }
]

const initials = computed(() => (user.value?.displayName || user.value?.username || 'U').slice(0, 1).toUpperCase())
const hasNext = computed(() => Boolean(nextCursor.value?.length) && rows.value.length === pageSize.value)

onMounted(async () => {
  try {
    await ensureCsrf()
    user.value = await getCurrentUser()
    await runSearch(false)
  } catch (error) {
    if (error.status !== 401) console.warn(error.message)
  } finally {
    booting.value = false
  }
})

async function handleLogin() {
  loginError.value = ''
  if (!loginForm.username.trim() || !loginForm.password) {
    loginError.value = '请输入用户名和密码'
    return
  }
  loginLoading.value = true
  try {
    user.value = await login({ username: loginForm.username.trim(), password: loginForm.password })
    loginForm.password = ''
    await runSearch(false)
  } catch (error) {
    loginError.value = error.message
  } finally {
    loginLoading.value = false
  }
}

function buildQuery(cursor) {
  const range = filters.timeRange || []
  return compact({
    from: range[0]?.toISOString(), to: range[1]?.toISOString(),
    'project.id': filters.projectId, 'deployment.environment': filters.environment,
    'service.name': filters.serviceName, 'service.instance.id': filters.instanceId,
    'trace.id': filters.traceId, 'span.id': filters.spanId, 'log.level': filters.logLevel,
    'error.type': filters.errorType, keyword: filters.keyword, size: pageSize.value,
    searchAfter: cursor
  })
}

function compact(object) {
  return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== '' && value !== null && value !== undefined))
}

async function runSearch(showFeedback = false) {
  if (!user.value) return
  analysisRequestId += 1
  analysisCards.value = []
  analysisError.value = ''
  searchLoading.value = true
  searchError.value = ''
  const started = performance.now()
  try {
    const result = await searchLogs(buildQuery(cursors.value[page.value - 1]))
    rows.value = (result.items || []).map(normalizeLog)
    total.value = result.total || 0
    nextCursor.value = result.nextSearchAfter || null
    duration.value = Math.max(1, Math.round(performance.now() - started))
    searched.value = true
    loadAnalysis()
    if (showFeedback) ElMessage.success('日志已刷新')
  } catch (error) {
    if (error.status === 401) {
      user.value = null
      loginError.value = '登录状态已过期，请重新登录'
    } else {
      searchError.value = error.message
      rows.value = []
    }
  } finally {
    searchLoading.value = false
  }
}

/** 调用白名单聚合接口生成分析摘要，聚合失败不影响主查询结果。 */
async function loadAnalysis() {
  if (!user.value) return
  const requestId = ++analysisRequestId
  analysisLoading.value = true
  analysisError.value = ''
  const query = buildQuery(null)
  delete query.searchAfter
  const dimensions = [
    { field: 'log.level', label: '日志级别' },
    { field: 'service.name', label: '服务名称' },
    { field: 'deployment.environment', label: '部署环境' }
  ]
  try {
    const results = await Promise.all(dimensions.map(async (dimension) => {
      const response = await aggregateLogs(query, [dimension.field], 5)
      const buckets = response?.aggregations?.groups?.buckets || []
      return {
        ...dimension,
        total: buckets.length,
        items: buckets.map((bucket) => ({
          key: bucket.key_as_string ?? String(bucket.key ?? '未设置'),
          count: bucket.doc_count || 0
        }))
      }
    }))
    if (requestId !== analysisRequestId) return
    analysisCards.value = results.map((card) => {
      const max = Math.max(1, ...card.items.map((item) => item.count))
      return { ...card, items: card.items.map((item) => ({ ...item, percent: Math.max(4, Math.round(item.count / max * 100)) })) }
    })
  } catch (error) {
    if (requestId === analysisRequestId) {
      analysisCards.value = []
      analysisError.value = error.message || '聚合查询失败'
    }
  } finally {
    if (requestId === analysisRequestId) analysisLoading.value = false
  }
}

/** 从侧边栏定位到当前结果页的分析摘要。 */
function scrollToAnalysis() {
  document.querySelector('.analysis-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 从侧边栏定位到最近一次链路视图。 */
function scrollToTrace() {
  if (selectedRow.value?.traceId) loadTrace()
  else ElMessage.info('请先打开包含 Trace ID 的日志详情')
  mobileMenu.value = false
}

function normalizeLog(hit) {
  const source = hit.source || {}
  return {
    id: hit.id, index: hit.index, source,
    timestamp: source['@timestamp'] || source.timestamp,
    level: String(source['log.level'] || source.level || 'INFO').toUpperCase(),
    service: source['service.name'] || 'unknown-service',
    instance: source['service.instance.id'] || source.host?.name || '—',
    message: source.message || source.body || '(无日志消息)',
    traceId: source['trace.id'] || '',
    errorType: source['error.type'] || ''
  }
}

function submitSearch() {
  page.value = 1
  cursors.value = [null]
  nextCursor.value = null
  runSearch(false)
}

function nextPage() {
  if (!hasNext.value) return
  cursors.value[page.value] = nextCursor.value
  page.value += 1
  runSearch(false)
}

function previousPage() {
  if (page.value <= 1) return
  page.value -= 1
  runSearch(false)
}

function resetFilters() {
  Object.assign(filters, { keyword: '', timeRange: defaultRange(), projectId: '', environment: '', serviceName: '', logLevel: '', traceId: '', instanceId: '', spanId: '', errorType: '' })
  submitSearch()
}

function filterByTrace(traceId) {
  filters.traceId = traceId
  submitSearch()
}

async function openDetail(row) {
  selectedRow.value = row
  contextRows.value = []
  detailError.value = ''
  detailOpen.value = true
  detailLoading.value = true
  try {
    const detail = await getLogDetail(row.id)
    selectedRow.value = normalizeLog(detail)
  } catch (error) {
    detailError.value = error.message
  } finally {
    detailLoading.value = false
  }
}

/** 请求服务端上下文，并将结果转换为与列表相同的展示模型。 */
async function loadContext() {
  if (!selectedRow.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getLogContext(selectedRow.value.id, {
      timestamp: selectedRow.value.timestamp,
      traceId: selectedRow.value.traceId || undefined,
      serviceName: selectedRow.value.service || undefined,
      beforeSeconds: 30,
      afterSeconds: 30
    })
    contextRows.value = (result.items || []).map(normalizeLog)
  } catch (error) {
    detailError.value = error.message
  } finally {
    detailLoading.value = false
  }
}

/** 从上下文列表切换到目标日志，保持同一个详情抽屉。 */
function selectContext(row) {
  openDetail(row)
}

/** 加载日志与 Tempo 的联查结果，并保持 Tempo 故障不影响日志查询。 */
async function loadTrace() {
  const traceId = selectedRow.value?.traceId
  if (!traceId) return
  traceOpen.value = true
  traceLoading.value = true
  traceError.value = ''
  try {
    traceData.value = await getTraceCorrelation(traceId)
  } catch (error) {
    traceData.value = null
    traceError.value = error.message || 'Trace 查询失败'
  } finally {
    traceLoading.value = false
  }
}

/** 将 Tempo 常见 v2 trace 结构展平为前端瀑布图行。 */
function flattenTrace(value) {
  const spans = []
  const toNanoseconds = (raw) => {
    if (typeof raw === 'bigint') return raw
    if (typeof raw === 'number' && Number.isFinite(raw) && Number.isInteger(raw)) return BigInt(raw)
    const text = String(raw ?? '').trim()
    return /^\d+$/.test(text) ? BigInt(text) : 0n
  }
  const visit = (node, service = '') => {
    if (!node || typeof node !== 'object') return
    const attributes = node.attributes || node.resource?.attributes || []
    const attributeList = Array.isArray(attributes) ? attributes : Object.entries(attributes).map(([key, val]) => ({ key, value: val }))
    const serviceName = attributeList.find(item => item.key === 'service.name')?.value?.stringValue
      || attributeList.find(item => item.key === 'service.name')?.value
      || service
    const start = toNanoseconds(node.startTimeUnixNano ?? node.start_time_unix_nano)
    const duration = toNanoseconds(node.durationNanos ?? node.duration_nanos)
    if (node.name && start > 0n) {
      spans.push({
        id: node.spanId || `${node.name}-${start}`,
        name: node.name,
        service: serviceName,
        start,
        duration,
        durationMs: Math.max(0, Math.round(Number(duration) / 1e6))
      })
    }
    Object.entries(node).forEach(([key, child]) => {
      if (key !== 'attributes' && key !== 'resource') visit(child, serviceName)
    })
  }
  visit(value)
  if (!spans.length) return []
  const origin = spans.reduce((minimum, span) => span.start < minimum ? span.start : minimum, spans[0].start)
  const end = spans.reduce((maximum, span) => {
    const spanEnd = span.start + span.duration
    return spanEnd > maximum ? spanEnd : maximum
  }, origin)
  const total = end > origin ? end - origin : 1n
  const percent = (nanoseconds) => Number(nanoseconds * 10000n / total) / 100
  return spans.sort((a, b) => a.start < b.start ? -1 : a.start > b.start ? 1 : 0).map(span => ({
    ...span,
    left: Math.max(0, percent(span.start - origin)),
    width: Math.max(1.5, percent(span.duration))
  }))
}

const traceSpans = computed(() => flattenTrace(traceData.value?.tempoTrace))

function shorten(value) {
  return value.length > 14 ? `${value.slice(0, 7)}…${value.slice(-5)}` : value
}

function formatTimestamp(value, full = false) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const options = full
    ? { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3, hour12: false }
    : { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }
  return new Intl.DateTimeFormat('zh-CN', options).format(date).replaceAll('/', '-')
}

async function copySource(source) {
  await navigator.clipboard.writeText(JSON.stringify(source, null, 2))
  ElMessage.success('原始字段已复制')
}

async function handleAccountCommand(command) {
  if (command !== 'logout') return
  try {
    await logout()
    user.value = null
    rows.value = []
    await ensureCsrf().catch(() => {})
  } catch (error) {
    ElMessage.error(`退出失败：${error.message}`)
  }
}
</script>
