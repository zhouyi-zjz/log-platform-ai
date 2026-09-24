import type { PetActionDefinition, PetDefinition } from './types'

/**
 * 内置示例宠物。
 *
 * 应用可以传入自己的 PetDefinition 替换它：资源、名称、动作回调都不会耦合到组件。
 */
const defaultActions: readonly PetActionDefinition[] = [
  { id: 'idle', label: '待机', state: 'idle' },
  { id: 'running', label: '处理中', state: 'running' },
  { id: 'waiting', label: '等待确认', state: 'waiting', durationMs: 0 },
  { id: 'review', label: '检查结果', state: 'review', durationMs: 1800 },
  { id: 'failed', label: '任务失败', state: 'failed', durationMs: 2200 },
  { id: 'waving', label: '挥手', state: 'waving', durationMs: 1600 },
  { id: 'jumping', label: '跳跃', state: 'jumping', durationMs: 1400 }
]

export const DEFAULT_PET: PetDefinition = {
  id: 'haige-coder',
  name: '海格小助手',
  description: '会观察、执行任务，并在需要确认时提醒你的桌面宠物。',
  emoji: '✦',
  actions: defaultActions
}
