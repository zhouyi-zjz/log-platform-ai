/**
 * 宠物领域类型定义。
 *
 * 设计原则：展示组件只消费这些类型，不直接依赖状态机的实现细节。
 * 将来切换精灵图、Lottie 或 Canvas 渲染器时，业务层仍然可以复用同一套契约。
 */

/** Codex 风格的标准动作行。动作名称与精灵图的 0-8 行一一对应。 */
export type PetState =
  | 'idle'
  | 'running-right'
  | 'running-left'
  | 'waving'
  | 'jumping'
  | 'failed'
  | 'waiting'
  | 'running'
  | 'review'

/** 16 个顺时针视线方向，单位为屏幕坐标角度。 */
export type PetDirection =
  | 0
  | 22.5
  | 45
  | 67.5
  | 90
  | 112.5
  | 135
  | 157.5
  | 180
  | 202.5
  | 225
  | 247.5
  | 270
  | 292.5
  | 315
  | 337.5

/** 用户或宿主应用可以驱动宠物的事件。 */
export type PetEvent =
  | { type: 'pointer-enter'; direction?: PetDirection }
  | { type: 'look-at'; direction: PetDirection }
  | { type: 'pointer-leave' }
  | { type: 'click' }
  | { type: 'task-start'; taskId?: string }
  | { type: 'task-progress'; progress: number; taskId?: string }
  | { type: 'task-complete'; taskId?: string }
  | { type: 'task-fail'; message?: string; taskId?: string }
  | { type: 'approval-needed'; taskId?: string }
  | { type: 'user-helped'; taskId?: string }
  | { type: 'reset' }
  | { type: 'tick'; now: number }

/** 宠物动作的上下文，动作插件可以通过它读取当前运行信息。 */
export interface PetActionContext {
  readonly state: PetState
  readonly direction: PetDirection
  readonly progress: number
  readonly taskId?: string
  readonly taskMessage?: string
}

/** 可插拔动作定义。新增动作时只需注册定义，无需修改核心状态机。 */
export interface PetActionDefinition {
  readonly id: string
  readonly label: string
  readonly state: PetState
  readonly durationMs?: number
  readonly onEnter?: (context: PetActionContext) => void
  readonly onExit?: (context: PetActionContext) => void
}

/** v2 精灵图元数据。默认每个单元格为 192×208，整图为 8×11。 */
export interface PetSpriteSheet {
  readonly url: string
  readonly frameWidth?: number
  readonly frameHeight?: number
  readonly columns?: number
  readonly rows?: number
  readonly spriteVersionNumber?: 2
}

/** CSS 兜底形象使用的外观参数；有精灵图时仍可以作为无障碍和预览信息复用。 */
export interface PetAppearance {
  readonly silhouette?: 'orb' | 'cat' | 'robot' | 'cloud'
  readonly bodyColor?: string
  readonly accentColor?: string
  readonly borderColor?: string
}

/** 宠物身份与视觉资源定义。 */
export interface PetDefinition {
  readonly id: string
  readonly name: string
  readonly description?: string
  readonly emoji?: string
  readonly appearance?: PetAppearance
  readonly spriteSheet?: PetSpriteSheet
  readonly actions?: readonly PetActionDefinition[]
}

/** 宠物当前快照，适合直接暴露给 Vue、React 或其他宿主。 */
export interface PetSnapshot {
  readonly state: PetState
  readonly direction: PetDirection
  readonly frame: number
  readonly progress: number
  readonly taskId?: string
  readonly taskMessage?: string
  readonly isHovered: boolean
  readonly lastEvent?: PetEvent['type']
  readonly updatedAt: number
}

/** 控制器配置。时间与随机函数可注入，便于单元测试和服务端预渲染。 */
export interface PetControllerOptions {
  readonly frameDurationMs?: number
  readonly idleActionDurationMs?: number
  readonly random?: () => number
  readonly now?: () => number
}

export interface PetController {
  readonly definition: PetDefinition
  getSnapshot(): PetSnapshot
  dispatch(event: PetEvent): PetSnapshot
  subscribe(listener: (snapshot: PetSnapshot) => void): () => void
  start(): void
  stop(): void
  destroy(): void
}
