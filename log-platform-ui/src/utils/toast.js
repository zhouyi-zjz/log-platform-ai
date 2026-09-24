import { showToast as vantShowToast } from 'vant'

/**
 * 显示顶部Toast提示（优化版）
 * @param {string|object} options - 消息文本或配置对象
 */
export function showToast(options) {
  // 如果是字符串，转为配置对象
  const config = typeof options === 'string'
    ? { message: options }
    : options

  // 统一配置
  return vantShowToast({
    position: 'top',           // 顶部显示
    duration: 2000,            // 2秒后消失
    className: 'custom-toast', // 自定义样式类
    ...config                  // 允许覆盖默认配置
  })
}

/**
 * 显示成功提示
 */
export function showSuccessToast(message) {
  return showToast({
    message,
    type: 'success',
    icon: 'success'
  })
}

/**
 * 显示失败提示
 */
export function showFailToast(message) {
  return showToast({
    message,
    type: 'fail',
    icon: 'cross'
  })
}
