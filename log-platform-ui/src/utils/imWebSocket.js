/**
 * IM WebSocket 服务（STOMP 协议）
 *
 * ─── 设计说明 ──────────────────────────────────────────────────────────────
 * 1. 单例模式：全应用共享一个 WebSocket 连接，避免重复握手开销。
 * 2. 自动重连：断线后以指数退避策略（5s、10s、20s…最大 60s）自动重连，
 *    重连成功后自动恢复所有订阅。
 * 3. 多频道订阅：通过 subscribe/unsubscribe 方法管理任意数量的 STOMP 订阅，
 *    组件卸载时自动取消，防止内存泄漏。
 * 4. 扩展性：新增频道（群聊、系统通知等）只需调用 subscribe 订阅新目的地，
 *    无需修改本模块。
 *
 * ─── 频道规划 ──────────────────────────────────────────────────────────────
 * 订阅目的地                  | 说明
 * /user/queue/im            | 接收私信（当前实现）
 * /user/queue/notify        | 系统通知：好友申请、被@等（预留）
 * /topic/group/{groupId}    | 群聊广播（预留）
 *
 * ─── 使用示例 ──────────────────────────────────────────────────────────────
 * // 连接（通常在 App.vue 或 layout 里调用一次）
 * imWs.connect()
 *
 * // 订阅私信（在聊天组件中）
 * const subId = imWs.subscribe('/user/queue/im', (msg) => {
 *   console.log('收到消息', msg)
 * })
 *
 * // 组件卸载时取消订阅
 * imWs.unsubscribe(subId)
 *
 * // 发送消息
 * imWs.send('/app/im/send', { toUserId: 123, content: 'Hello', type: 'TEXT' })
 *
 * // 断开连接
 * imWs.disconnect()
 */

import { Client } from '@stomp/stompjs'
import { getToken } from '@/utils/auth'

// ─── 常量 ──────────────────────────────────────────────────────────────────

/**
 * 构建 WebSocket 基础地址
 *
 * 两种场景：
 *  1. 开发环境：VITE_APP_BASE_API = '/dev-api'（相对路径）
 *     → 使用当前页面的 host 拼出完整 ws:// 地址，交由 Vite 代理转发给后端
 *     → 最终地址：ws://localhost:3000/dev-api
 *  2. 生产/部署环境：VITE_APP_BASE_API = 'http://api.example.com'（绝对路径）
 *     → 直接将 http(s) 替换为 ws(s)
 *     → 最终地址：ws://api.example.com
 */
const WS_BASE_URL = (() => {
  const apiBase = import.meta.env.VITE_APP_BASE_API || ''
  if (apiBase.startsWith('/')) {
    // 相对路径：用当前页面协议和 host 补全
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}${apiBase}`
  }
  // 绝对路径：直接替换协议
  return apiBase.replace(/^http/, 'ws')
})()

/** 重连延迟起始值（毫秒） */
const RECONNECT_DELAY_MS = 5000

/** 重连最大延迟（毫秒） */
const MAX_RECONNECT_DELAY_MS = 60000

// ─── 内部状态 ──────────────────────────────────────────────────────────────

/** STOMP 客户端实例 */
let stompClient = null

/**
 * 待恢复的订阅列表（断线重连后自动恢复）
 * Map<subId, { destination, callback }>
 */
const pendingSubscriptions = new Map()

/** 当前活跃的 STOMP Subscription 对象映射 Map<subId, StompSubscription> */
const activeSubscriptions = new Map()

/** 自增 ID，用于生成唯一的订阅 ID */
let subIdCounter = 0

/** 连接状态 */
let connected = false

/** 消息监听器：连接状态变化时通知外部（如显示/隐藏断线提示） */
const connectionListeners = new Set()

// ─── 内部方法 ──────────────────────────────────────────────────────────────

/**
 * 在 STOMP 连接建立后，将 pendingSubscriptions 中记录的订阅全部激活
 * （首次连接 + 断线重连后均会调用）
 */
function activateAllSubscriptions () {
  for (const [subId, { destination, callback }] of pendingSubscriptions.entries()) {
    _doSubscribe(subId, destination, callback)
  }
}

/**
 * 实际执行 STOMP subscribe（要求 stompClient 已连接）
 *
 * @param {string}   subId       本地订阅 ID（用于取消订阅）
 * @param {string}   destination STOMP 目的地
 * @param {Function} callback    收到消息时的回调，参数为已解析的 JS 对象
 */
function _doSubscribe (subId, destination, callback) {
  if (!stompClient || !stompClient.connected) return
  const stompSub = stompClient.subscribe(destination, (frame) => {
    try {
      const body = JSON.parse(frame.body)
      callback(body)
    } catch (e) {
      // 解析失败时传入原始字符串，保持健壮性
      callback(frame.body)
    }
  })
  activeSubscriptions.set(subId, stompSub)
}

/**
 * 通知所有连接状态监听器
 *
 * @param {boolean} isConnected 当前是否已连接
 */
function notifyConnectionListeners (isConnected) {
  connected = isConnected
  for (const listener of connectionListeners) {
    try { listener(isConnected) } catch (_) {}
  }
}

// ─── 公共 API ──────────────────────────────────────────────────────────────

/**
 * 建立 WebSocket 连接（幂等：已连接则直接返回）
 *
 * <p>建议在用户登录后（如 layout/common/index.vue 的 initialization 中）调用一次。</p>
 */
function connect () {
  if (stompClient && stompClient.active) {
    return // 已连接或正在连接，无需重复
  }

  stompClient = new Client({
    /**
     * WebSocket 工厂：每次（重）连接时被调用
     * 注意：token 在此处动态获取，确保重连时使用最新令牌
     */
    webSocketFactory: () => {
      const token = getToken()
      const url = `${WS_BASE_URL}/ws?token=${encodeURIComponent(token)}`
      return new WebSocket(url)
    },

    /** 自动重连间隔（毫秒），@stomp/stompjs 底层按此值不断重试 */
    reconnectDelay: RECONNECT_DELAY_MS,

    /** STOMP 连接成功回调 */
    onConnect: () => {
      console.info('[IM-WS] 连接成功')
      notifyConnectionListeners(true)
      // 恢复所有注册的订阅（首次连接 + 断线重连均需要）
      activateAllSubscriptions()
    },

    /** STOMP 连接断开回调 */
    onDisconnect: () => {
      console.info('[IM-WS] 连接断开，准备重连…')
      activeSubscriptions.clear() // 清空旧的 STOMP Subscription 对象
      notifyConnectionListeners(false)
    },

    /** STOMP 层错误 */
    onStompError: (frame) => {
      console.error('[IM-WS] STOMP 错误:', frame)
    },

    /** WebSocket 层错误（如服务器不可达） */
    onWebSocketError: (event) => {
      console.warn('[IM-WS] WebSocket 错误，将自动重连')
    },

    /** 调试日志（生产环境可注释掉） */
    // debug: (str) => console.debug('[STOMP]', str),
  })

  stompClient.activate()
}

/**
 * 订阅指定 STOMP 目的地
 *
 * <p>如果当前已连接，立即激活订阅；如果尚未连接，将订阅记录到 pendingSubscriptions，
 * 等连接成功后自动激活。断线重连后所有订阅均会自动恢复。</p>
 *
 * @param {string}   destination STOMP 目的地（如 '/user/queue/im'）
 * @param {Function} callback    收到消息时的回调（参数为已 JSON.parse 的对象）
 * @returns {string} subId       订阅 ID，用于 unsubscribe 时传入
 *
 * @example
 * const subId = imWs.subscribe('/user/queue/im', (msg) => {
 *   console.log('收到', msg)
 * })
 */
function subscribe (destination, callback) {
  const subId = `sub_${++subIdCounter}`
  // 记录到 pendingSubscriptions，断线重连后可自动恢复
  pendingSubscriptions.set(subId, { destination, callback })
  // 如果当前已连接，立即激活
  if (stompClient && stompClient.connected) {
    _doSubscribe(subId, destination, callback)
  }
  return subId
}

/**
 * 取消订阅
 *
 * @param {string} subId subscribe() 返回的订阅 ID
 */
function unsubscribe (subId) {
  // 从待恢复列表中移除（重连后不再恢复此订阅）
  pendingSubscriptions.delete(subId)
  // 取消当前活跃的 STOMP 订阅
  const stompSub = activeSubscriptions.get(subId)
  if (stompSub) {
    try { stompSub.unsubscribe() } catch (_) {}
    activeSubscriptions.delete(subId)
  }
}

/**
 * 向服务端发送 STOMP 消息
 *
 * @param {string} destination STOMP 目的地（如 '/app/im/send'）
 * @param {object} body        消息体，将被序列化为 JSON 字符串
 *
 * @example
 * imWs.send('/app/im/send', {
 *   toUserId: 123,
 *   content: 'Hello',
 *   type: 'TEXT',
 *   clientMsgId: 'uuid-xxx'
 * })
 */
function send (destination, body) {
  if (!stompClient || !stompClient.connected) {
    console.warn('[IM-WS] 尚未连接，消息发送失败:', destination, body)
    return false
  }
  stompClient.publish({
    destination,
    body: JSON.stringify(body)
  })
  return true
}

/**
 * 断开连接（通常在用户退出登录时调用）
 */
function disconnect () {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
  }
  pendingSubscriptions.clear()
  activeSubscriptions.clear()
  notifyConnectionListeners(false)
  console.info('[IM-WS] 已主动断开连接')
}

/**
 * 注册连接状态变化监听器
 *
 * @param {Function} listener  参数 isConnected: boolean
 * @returns {Function}         取消监听的函数
 *
 * @example
 * const cancel = imWs.onConnectionChange((isConnected) => {
 *   console.log(isConnected ? '已连接' : '断线')
 * })
 * // 组件卸载时
 * cancel()
 */
function onConnectionChange (listener) {
  connectionListeners.add(listener)
  // 立即同步当前状态给新监听器
  try { listener(connected) } catch (_) {}
  return () => connectionListeners.delete(listener)
}

/** 当前是否已连接 */
function isConnected () {
  return connected
}

// ─── 导出 ──────────────────────────────────────────────────────────────────

export default {
  connect,
  disconnect,
  subscribe,
  unsubscribe,
  send,
  onConnectionChange,
  isConnected
}
