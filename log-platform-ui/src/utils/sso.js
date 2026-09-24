/**
 * SSO单点登录工具函数
 * 用于处理蓝凌系统单点登录相关逻辑
 */
import CryptoJS from 'crypto-js'

/**
 * 从URL中解析SSO参数
 * @param {string} url - 完整URL或查询字符串
 * @returns {Object} 包含usercode、timestamp、sign的对象
 */
export function parseSsoParams(url) {
  const params = {}
  const queryString = url.includes('?') ? url.split('?')[1] : url

  if (!queryString) {
    return params
  }

  const pairs = queryString.split('&')
  for (const pair of pairs) {
    const [key, value] = pair.split('=')
    if (key && value) {
      params[decodeURIComponent(key)] = decodeURIComponent(value)
    }
  }

  return params
}

/**
 * 生成SSO签名
 * 用于安全验证
 * @param {string} usercode - 用户工号
 * @param {number} timestamp - 时间戳
 * @param {string} secret - 密钥
 * @returns {string} MD5签名
 */
export function generateSsoSignature(usercode, timestamp, secret) {
  const str = `${usercode}${timestamp}${secret}`
  return CryptoJS.MD5(str).toString()
}

/**
 * 验证SSO签名
 * @param {string} usercode - 用户工号
 * @param {number} timestamp - 时间戳
 * @param {string} sign - 接收到的签名
 * @param {string} secret - 密钥
 * @returns {boolean} 签名是否有效
 */
export function verifySsoSignature(usercode, timestamp, sign, secret) {
  const expectedSign = generateSsoSignature(usercode, timestamp, secret)
  return expectedSign === sign
}

/**
 * 验证时间戳有效性
 * @param {number} timestamp - 时间戳（秒）
 * @param {number} maxAge - 最大有效期（秒），默认5分钟
 * @returns {boolean} 时间戳是否有效
 */
export function validateTimestamp(timestamp, maxAge = 300) {
  const now = Math.floor(Date.now() / 1000)
  const diff = Math.abs(now - timestamp)
  return diff <= maxAge
}

/**
 * 检测是否为移动设备
 * @returns {boolean} 是否为移动设备
 */
export function isMobile() {
  const ua = navigator.userAgent
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua)
}

/**
 * 检测是否为微信浏览器
 * @returns {boolean} 是否为微信浏览器
 */
export function isWeChat() {
  const ua = navigator.userAgent.toLowerCase()
  return ua.includes('micromessenger')
}

/**
 * 获取设备类型
 * @returns {string} 设备类型: 'ios', 'android', 'harmony', 'pc'
 */
export function getDeviceType() {
  const ua = navigator.userAgent.toLowerCase()

  if (/iphone|ipad|ipod/i.test(ua)) {
    return 'ios'
  } else if (/android/i.test(ua)) {
    return 'android'
  } else if (/harmony|harmonyos/i.test(ua)) {
    return 'harmony'
  } else {
    return 'pc'
  }
}

/**
 * 设置视口（viewport）meta标签
 * 用于移动端适配
 */
export function setMobileViewport() {
  let viewport = document.querySelector('meta[name=viewport]')

  if (!viewport) {
    viewport = document.createElement('meta')
    viewport.name = 'viewport'
    document.head.appendChild(viewport)
  }

  viewport.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover'
}

/**
 * 禁用页面缩放
 */
export function disableZoom() {
  // 禁用双击缩放
  let lastTouchEnd = 0
  document.addEventListener('touchend', function (event) {
    const now = Date.now()
    if (now - lastTouchEnd <= 300) {
      event.preventDefault()
    }
    lastTouchEnd = now
  }, false)

  // 禁用手势缩放
  document.addEventListener('gesturestart', function (event) {
    event.preventDefault()
  })
}
