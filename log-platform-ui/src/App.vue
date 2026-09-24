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
        <button class="nav-item disabled" disabled><ChartNoAxesCombined /><span>日志分析</span><em>即将上线</em></button>
        <p>管理</p>
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
        <section><h3>日志内容</h3><pre>{{ selectedRow.message }}</pre></section>
        <section><div class="section-title"><h3>原始字段</h3><button title="复制 JSON" @click="copySource(selectedRow.source)"><Copy /></button></div><pre class="json-block">{{ JSON.stringify(selectedRow.source, null, 2) }}</pre></section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowRight, BellRing, ChartNoAxesCombined, ChevronDown, ChevronLeft, ChevronRight,
  CircleAlert, CircleHelp, Copy, FileSearch, LockKeyhole, LogOut, Menu, RefreshCw,
  RotateCcw, Search, Settings, ShieldCheck, SlidersHorizontal, UserRound, X
} from 'lucide-vue-next'
import { ensureCsrf, getCurrentUser, login, logout, searchLogs } from '@/api/platform'

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

function openDetail(row) {
  selectedRow.value = row
  detailOpen.value = true
}

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
