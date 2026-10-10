import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '',
  timeout: 120000,
  withCredentials: true,
  // The API returns Spring's masked token explicitly. Axios' default cookie
  // handling would replace it with the raw XSRF-TOKEN cookie and cause a 403.
  xsrfCookieName: null
})

let csrf = null

function messageOf(error) {
  return error.response?.data?.message || error.response?.data?.msg || error.message || '请求失败'
}

async function request(config) {
  try {
    const response = await http(config)
    const body = response.data
    if (body?.code !== 200) throw new Error(body?.message || '请求失败')
    return body.data
  } catch (error) {
    const wrapped = new Error(messageOf(error))
    wrapped.status = error.response?.status
    throw wrapped
  }
}

export async function ensureCsrf() {
  csrf = await request({ method: 'get', url: '/monitor/api/auth/csrf' })
  return csrf
}

async function write(method, url, data) {
  if (!csrf) await ensureCsrf()
  return request({
    method,
    url,
    data,
    headers: csrf ? { [csrf.headerName]: csrf.token } : undefined
  })
}

export const getCurrentUser = () => request({ method: 'get', url: '/monitor/api/auth/me' })
export async function login(credentials) {
  const user = await write('post', '/monitor/api/auth/login', credentials)
  // Spring Security may rotate the CSRF token when authentication succeeds.
  await ensureCsrf()
  return user
}
export const logout = () => write('post', '/monitor/api/auth/logout')
export const searchLogs = (query) => write('post', '/monitor/api/logs/search', query)
/** 读取服务端经过权限窗口和字段脱敏的日志详情。 */
export const getLogDetail = (id) => write('get', `/monitor/api/logs/${encodeURIComponent(id)}`)
/** 读取目标日志前后文，权限和时间范围由后端再次裁剪。 */
export const getLogContext = (id, query = {}) => write('post', `/monitor/api/logs/${encodeURIComponent(id)}/context`, query)
/** 查询受 ACL 约束的日志与 Tempo Trace 联查结果。 */
export const getTraceCorrelation = (traceId) => request({ method: 'get', url: `/monitor/api/traces/${encodeURIComponent(traceId)}` })
/** 按白名单字段获取聚合结果，用于日志分析面板。 */
export const aggregateLogs = (query, groupBy, size = 20) => write('post', '/monitor/api/logs/aggregations', { query, groupBy, size })
/** 创建异步 CSV 导出任务，任务权限和查询窗口由后端复用日志查询策略。 */
export const createLogExport = (query) => write('post', '/monitor/api/logs/exports', query)
/** 查询异步导出任务状态。 */
export const getLogExportStatus = (jobId) => write('get', `/monitor/api/logs/exports/${encodeURIComponent(jobId)}`)
