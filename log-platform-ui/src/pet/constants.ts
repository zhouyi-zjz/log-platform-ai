import type { PetDirection, PetState } from './types'

/**
 * 精灵图行号映射。
 * 这里集中维护协议映射，业务代码只使用可读的动作名称。
 */
export const PET_STATE_ROW: Readonly<Record<PetState, number>> = {
  idle: 0,
  'running-right': 1,
  'running-left': 2,
  waving: 3,
  jumping: 4,
  failed: 5,
  waiting: 6,
  running: 7,
  review: 8
}

/** 每一行的默认帧数；视线方向行使用 16 个方向拆成两行。 */
export const PET_STATE_FRAME_COUNT: Readonly<Record<PetState, number>> = {
  idle: 8,
  'running-right': 8,
  'running-left': 8,
  waving: 8,
  jumping: 8,
  failed: 8,
  waiting: 8,
  running: 8,
  review: 8
}

export const PET_DIRECTIONS: readonly PetDirection[] = [
  0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5,
  180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5
]

/** 默认状态持续时间，避免动作结束后永远停留在反馈态。 */
export const PET_DEFAULT_ACTION_DURATION_MS: Readonly<Partial<Record<PetState, number>>> = {
  waving: 1600,
  jumping: 1400,
  failed: 2200,
  review: 1800
}

export const PET_DEFAULT_FRAME_DURATION_MS = 140
export const PET_DEFAULT_IDLE_ACTION_DURATION_MS = 7000

/** 将角度归一化到合法方向，调用方传入非标准角度时取最近的方向。 */
export function normalizePetDirection(value: number): PetDirection {
  const normalized = ((value % 360) + 360) % 360
  return PET_DIRECTIONS.reduce((closest, direction) => {
    const currentDistance = Math.abs(direction - normalized)
    const closestDistance = Math.abs(closest - normalized)
    return currentDistance < closestDistance ? direction : closest
  }, PET_DIRECTIONS[0])
}

/** 将标准动作映射为更适合展示的中文名称。 */
export const PET_STATE_LABEL: Readonly<Record<PetState, string>> = {
  idle: '待机',
  'running-right': '向右移动',
  'running-left': '向左移动',
  waving: '挥手',
  jumping: '跳跃',
  failed: '任务失败',
  waiting: '等待确认',
  running: '处理中',
  review: '检查结果'
}
