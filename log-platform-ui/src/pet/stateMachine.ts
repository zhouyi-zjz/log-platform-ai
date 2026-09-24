import {
  PET_DEFAULT_ACTION_DURATION_MS,
  PET_DEFAULT_FRAME_DURATION_MS,
  PET_DEFAULT_IDLE_ACTION_DURATION_MS,
  PET_STATE_FRAME_COUNT,
  normalizePetDirection
} from './constants'
import { PetActionRegistry } from './actionRegistry'
import type {
  PetController,
  PetControllerOptions,
  PetDefinition,
  PetEvent,
  PetSnapshot,
  PetState
} from './types'

const TERMINAL_TASK_STATES: readonly PetState[] = ['failed', 'review', 'waiting']

/**
 * 宠物状态机。
 *
 * 状态机只负责“当前应该做什么”，不负责 DOM、网络请求或具体绘制。
 * 这样可让同一只宠物同时服务桌面端、移动端和测试环境。
 */
export class PetStateMachine implements PetController {
  readonly definition: PetDefinition
  private readonly registry: PetActionRegistry
  private readonly frameDurationMs: number
  private readonly idleActionDurationMs: number
  private readonly random: () => number
  private readonly now: () => number
  private readonly listeners = new Set<(snapshot: PetSnapshot) => void>()
  private timer: ReturnType<typeof setInterval> | undefined
  private actionExpireAt: number | undefined
  private snapshot: PetSnapshot

  constructor(definition: PetDefinition, options: PetControllerOptions = {}) {
    this.definition = definition
    this.registry = new PetActionRegistry(definition.actions)
    this.frameDurationMs = Math.max(40, options.frameDurationMs ?? PET_DEFAULT_FRAME_DURATION_MS)
    this.idleActionDurationMs = Math.max(
      this.frameDurationMs,
      options.idleActionDurationMs ?? PET_DEFAULT_IDLE_ACTION_DURATION_MS
    )
    this.random = options.random ?? Math.random
    this.now = options.now ?? Date.now
    this.snapshot = this.createSnapshot({ state: 'idle', direction: 0 })
  }

  getSnapshot(): PetSnapshot {
    return this.snapshot
  }

  subscribe(listener: (snapshot: PetSnapshot) => void): () => void {
    this.listeners.add(listener)
    listener(this.snapshot)
    return () => this.listeners.delete(listener)
  }

  dispatch(event: PetEvent): PetSnapshot {
    const previous = this.snapshot
    const next = this.reduce(previous, event)
    if (next.state !== previous.state) {
      this.registry.notifyExit(this.toActionContext(previous))
      this.registry.notifyEnter(this.toActionContext(next))
    }
    this.snapshot = next
    this.notify()
    return next
  }

  start(): void {
    if (this.timer) return
    this.timer = setInterval(() => this.dispatch({ type: 'tick', now: this.now() }), this.frameDurationMs)
  }

  stop(): void {
    if (!this.timer) return
    clearInterval(this.timer)
    this.timer = undefined
  }

  destroy(): void {
    this.stop()
    this.listeners.clear()
  }

  private reduce(current: PetSnapshot, event: PetEvent): PetSnapshot {
    switch (event.type) {
      case 'pointer-enter':
        return this.transition(current, {
          direction: event.direction ?? current.direction,
          isHovered: true
        }, event.type)
      case 'look-at':
        return this.transition(current, { direction: event.direction }, event.type)
      case 'pointer-leave':
        return this.transition(current, { isHovered: false }, event.type)
      case 'click':
        return this.transition(current, { state: 'waving' }, event.type)
      case 'task-start':
        return this.transition(current, {
          state: 'running',
          taskId: event.taskId,
          taskMessage: undefined,
          progress: 0
        }, event.type)
      case 'task-progress':
        return this.transition(current, {
          state: 'running',
          progress: Math.min(100, Math.max(0, event.progress)),
          taskId: event.taskId ?? current.taskId
        }, event.type)
      case 'task-complete':
        return this.transition(current, {
          state: 'review',
          progress: 100,
          taskId: event.taskId ?? current.taskId,
          taskMessage: undefined
        }, event.type)
      case 'task-fail':
        return this.transition(current, {
          state: 'failed',
          taskId: event.taskId ?? current.taskId,
          taskMessage: event.message
        }, event.type)
      case 'approval-needed':
        return this.transition(current, {
          state: 'waiting',
          taskId: event.taskId ?? current.taskId
        }, event.type)
      case 'user-helped':
        return this.transition(current, { state: 'running', taskMessage: undefined }, event.type)
      case 'reset':
        return this.transition(current, {
          state: 'idle',
          direction: 0,
          frame: 0,
          progress: 0,
          taskId: undefined,
          taskMessage: undefined,
          isHovered: false
        }, event.type)
      case 'tick':
        return this.tick(current, event.now)
      default: {
        const exhaustiveCheck: never = event
        return exhaustiveCheck
      }
    }
  }

  private tick(current: PetSnapshot, now: number): PetSnapshot {
    const frameCount = PET_STATE_FRAME_COUNT[current.state]
    const nextFrame = (current.frame + 1) % frameCount
    if (this.actionExpireAt && now >= this.actionExpireAt && TERMINAL_TASK_STATES.includes(current.state)) {
      return this.transition(current, { state: 'idle', frame: 0 }, 'tick')
    }

    // 待机时偶尔做一个短动作，模拟 Codex 宠物的自主微交互。
    if (current.state === 'idle' && this.random() > 0.985) {
      return this.transition(current, { state: 'jumping', frame: 0 }, 'tick')
    }
    return this.transition(current, { frame: nextFrame }, 'tick')
  }

  private transition(
    current: PetSnapshot,
    patch: Partial<PetSnapshot>,
    eventType: PetEvent['type']
  ): PetSnapshot {
    const state = patch.state ?? current.state
    if (state !== current.state) {
      const actionDuration = this.registry.getByState(state)?.durationMs
        ?? PET_DEFAULT_ACTION_DURATION_MS[state]
        ?? (state === 'idle' ? this.idleActionDurationMs : undefined)
      this.actionExpireAt = actionDuration ? this.now() + actionDuration : undefined
    }
    return this.createSnapshot({ ...current, ...patch, state, lastEvent: eventType })
  }

  private createSnapshot(snapshot: Partial<PetSnapshot>): PetSnapshot {
    return {
      state: 'idle',
      frame: 0,
      progress: 0,
      isHovered: false,
      updatedAt: this.now(),
      ...snapshot,
      direction: normalizePetDirection(snapshot.direction ?? 0)
    }
  }

  private toActionContext(snapshot: PetSnapshot) {
    return {
      state: snapshot.state,
      direction: snapshot.direction,
      progress: snapshot.progress,
      taskId: snapshot.taskId,
      taskMessage: snapshot.taskMessage
    }
  }

  private notify(): void {
    this.listeners.forEach(listener => listener(this.snapshot))
  }
}
