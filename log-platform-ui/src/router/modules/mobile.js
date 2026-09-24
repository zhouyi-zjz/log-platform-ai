/**
 * 移动端H5路由配置
 * 用于移动端聊天页面、SSO登录等功能
 */

const mobileRoutes = [
  {
    path: '/mobile',
    redirect: '/mobile/chat',
    hidden: true,
    meta: { title: '移动端' }
  },
  {
    path: '/mobile/sso/callback',
    name: 'MobileSsoCallback',
    hidden: true,
    component: () => import('@/views/mobile/sso/callback.vue'),
    meta: {
      title: 'SSO登录',
      requiresAuth: false  // 不需要登录认证
    }
  },
  {
    path: '/mobile/chat/:sessionId?',
    name: 'MobileChat',
    hidden: true,
    component: () => import('@/views/mobile/chat/index.vue'),
    meta: {
      title: 'AI智能助手',
      requiresAuth: true  // 需要登录认证
      // keepAlive: true  // 禁用缓存，确保每次打开都是全新会话（与PC端行为一致）
    }
  },
  {
    path: '/mobile/error/unauthorized',
    name: 'MobileUnauthorized',
    hidden: true,
    component: () => import('@/views/mobile/error/unauthorized.vue'),
    meta: {
      title: '未授权访问',
      requiresAuth: false
    }
  },
  {
    path: '/mobile/error/404',
    name: 'MobileNotFound',
    hidden: true,
    component: () => import('@/views/mobile/error/404.vue'),
    meta: {
      title: '页面未找到',
      requiresAuth: false
    }
  }
]

export default mobileRoutes
