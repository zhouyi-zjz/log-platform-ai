import { reactive } from 'vue'

export type PetReminderType = 'message' | 'checklist'

export interface PetReminderState {
  visible: boolean
  type: PetReminderType | null
  text: string
  animationKey: number
}

const DEFAULT_DURATION_MS = 5000
const state = reactive<PetReminderState>({
  visible: false,
  type: null,
  text: '',
  animationKey: 0
})

let hideTimer: ReturnType<typeof setTimeout> | undefined

/**
 * 全局宠物提醒服务。
 * 消息订阅、Checklist 轮询等数据源只负责调用 show，展示组件只消费这份状态。
 * 这种分层可以避免把 WebSocket、定时器和页面 DOM 逻辑混在一起。
 */
export function usePetReminders() {
  function show(type: PetReminderType, text: string, durationMs = DEFAULT_DURATION_MS): void {
    if (hideTimer) clearTimeout(hideTimer)
    state.visible = true
    state.type = type
    state.text = text
    // 改变 key 让同一种提醒连续触发时也能重新播放 CSS 动画。
    state.animationKey += 1
    hideTimer = setTimeout(() => dismiss(), durationMs)
  }

  function showMessage(senderName: string): void {
    show('message', `好友${senderName || '好友'}发来信息`)
  }

  function showChecklist(title: string): void {
    show('checklist', `${title || '待办'}事项要准备开始啦`, 7000)
  }

  function dismiss(): void {
    if (hideTimer) clearTimeout(hideTimer)
    hideTimer = undefined
    state.visible = false
    state.type = null
    state.text = ''
  }

  return {
    reminder: state,
    show,
    showMessage,
    showChecklist,
    dismiss
  }
}
