<script setup lang="ts">
/**
 * 宠物交互组件。
 * 它提供最小且稳定的宿主契约：点击、悬停、任务事件由控制器统一处理，
 * 业务页面可以通过 ref 调用 exposed API，而不需要了解精灵图坐标或状态转移规则。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { PET_STATE_LABEL, normalizePetDirection } from '../constants'
import { DEFAULT_PET } from '../defaultPet'
import { usePetReminders } from '../reminders'
import { usePetSettings } from '../settings'
import { usePet } from '../usePet'
import type { PetController, PetDefinition, PetEvent } from '../types'
import PetSprite from './PetSprite.vue'

const props = withDefaults(defineProps<{
  definition?: PetDefinition
  controller?: PetController
  size?: number
  showStatus?: boolean
}>(), {
  size: 76,
  showStatus: true
})

const emit = defineEmits<{
  event: [event: PetEvent]
  click: []
}>()

const { controller, snapshot, dispatch } = usePet({
  definition: props.definition ?? DEFAULT_PET,
  controller: props.controller
})
const { settings, selectedPet, setPosition } = usePetSettings()
const { reminder, dismiss } = usePetReminders()
const currentDefinition = computed(() => props.definition ?? selectedPet.value ?? DEFAULT_PET)
const currentSize = computed(() => props.definition ? props.size : settings.size)
const currentOpacity = computed(() => props.definition ? 1 : settings.opacity)
const isExpanded = ref(false)
const statusLabel = computed(() => PET_STATE_LABEL[snapshot.value.state])
const progressLabel = computed(() => `${Math.round(snapshot.value.progress)}%`)
const isDragging = ref(false)
const didDrag = ref(false)
const dragStart = ref({ pointerX: 0, pointerY: 0, left: 0, top: 0 })
const dragPosition = ref<{ left: number; top: number } | null>(null)
const positionStyle = computed((): Record<string, string> => {
  const left = dragPosition.value?.left ?? settings.positionX
  const top = dragPosition.value?.top ?? settings.positionY
  if (left === undefined || top === undefined) {
    return {}
  }
  return {
    left: `${left}px`,
    top: `${top}px`,
    right: 'auto',
    bottom: 'auto'
  }
})

function send(event: PetEvent): void {
  dispatch(event)
  emit('event', event)
}

function handlePointerEnter(): void {
  send({ type: 'pointer-enter' })
}

/**
 * 将指针相对宠物中心的位置转换为最近的 22.5° 方向，交给状态机归一化。
 * 计算放在交互层，状态机本身不依赖浏览器坐标 API。
 */
function handlePointerMove(event: PointerEvent): void {
  if (isDragging.value) {
    const target = event.currentTarget as HTMLElement
    const bounds = target.getBoundingClientRect()
    const nextLeft = clamp(
      dragStart.value.left + event.clientX - dragStart.value.pointerX,
      0,
      Math.max(0, window.innerWidth - bounds.width)
    )
    const nextTop = clamp(
      dragStart.value.top + event.clientY - dragStart.value.pointerY,
      0,
      Math.max(0, window.innerHeight - bounds.height)
    )
    dragPosition.value = { left: nextLeft, top: nextTop }
    didDrag.value = true
    return
  }
  const target = event.currentTarget as HTMLElement
  const bounds = target.getBoundingClientRect()
  const radians = Math.atan2(event.clientY - (bounds.top + bounds.height / 2), event.clientX - (bounds.left + bounds.width / 2))
  let degrees = (radians * 180) / Math.PI + 90
  if (degrees < 0) degrees += 360
  send({ type: 'look-at', direction: normalizePetDirection(degrees) })
}

function handlePointerLeave(): void {
  send({ type: 'pointer-leave' })
}

function handleClick(): void {
  if (didDrag.value) {
    didDrag.value = false
    return
  }
  send({ type: 'click' })
  emit('click')
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    handleClick()
  }
}

/** 暴露给页面的控制方法，后续可接入真实任务流或 WebSocket。 */
function updateProgress(progress: number, taskId?: string): void {
  send({ type: 'task-progress', progress, taskId })
}

function startTask(taskId?: string): void { send({ type: 'task-start', taskId }) }
function completeTask(taskId?: string): void { send({ type: 'task-complete', taskId }) }
function failTask(message?: string, taskId?: string): void { send({ type: 'task-fail', message, taskId }) }
function needApproval(taskId?: string): void { send({ type: 'approval-needed', taskId }) }
function reset(): void { send({ type: 'reset' }) }

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function handleDragStart(event: PointerEvent): void {
  if (event.button !== 0) return
  const target = event.currentTarget as HTMLElement
  const bounds = target.getBoundingClientRect()
  dragStart.value = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    left: bounds.left,
    top: bounds.top
  }
  isDragging.value = true
  didDrag.value = false
  dragPosition.value = { left: bounds.left, top: bounds.top }
  target.setPointerCapture?.(event.pointerId)
  event.preventDefault()
}

function handleDragEnd(event: PointerEvent): void {
  if (!isDragging.value) return
  isDragging.value = false
  if (didDrag.value && dragPosition.value) {
    setPosition(dragPosition.value.left, dragPosition.value.top)
  }
  dragPosition.value = null
  const target = event.currentTarget as HTMLElement
  target.releasePointerCapture?.(event.pointerId)
}

function keepPositionInViewport(): void {
  if (settings.positionX === undefined || settings.positionY === undefined) return
  const target = document.querySelector('.pet-widget') as HTMLElement | null
  if (!target) return
  const bounds = target.getBoundingClientRect()
  setPosition(
    clamp(settings.positionX, 0, Math.max(0, window.innerWidth - bounds.width)),
    clamp(settings.positionY, 0, Math.max(0, window.innerHeight - bounds.height))
  )
}

onMounted(() => window.addEventListener('resize', keepPositionInViewport))
onUnmounted(() => window.removeEventListener('resize', keepPositionInViewport))

defineExpose({ controller, snapshot, updateProgress, startTask, completeTask, failTask, needApproval, reset })
</script>

<template>
  <section
    class="pet-widget"
    :class="{ 'is-expanded': isExpanded, 'is-reminding': reminder.visible }"
    :style="{ ...positionStyle, opacity: currentOpacity }"
    aria-label="桌面宠物"
  >
    <transition name="pet-reminder-pop">
      <div
        v-if="reminder.visible"
        :key="reminder.animationKey"
        class="pet-widget__reminder"
        :class="`is-${reminder.type}`"
        role="status"
        aria-live="polite"
        @click="dismiss"
      >
        <span class="pet-widget__reminder-dot" aria-hidden="true" />
        {{ reminder.text }}
      </div>
    </transition>
    <button
      class="pet-widget__button"
      type="button"
      :aria-expanded="isExpanded"
      :aria-label="`${currentDefinition.name}：${statusLabel}`"
      @click="handleClick"
      @keydown="handleKeydown"
      @mouseenter="handlePointerEnter"
      @pointerdown="handleDragStart"
      @pointermove="handlePointerMove"
      @pointerup="handleDragEnd"
      @pointercancel="handleDragEnd"
      @mouseleave="handlePointerLeave"
    >
      <PetSprite
        :definition="currentDefinition"
        :snapshot="snapshot"
        :size="currentSize"
      />
      <span v-if="snapshot.state === 'running'" class="pet-widget__progress" aria-hidden="true">
        {{ progressLabel }}
      </span>
    </button>

    <button
      class="pet-widget__toggle"
      type="button"
      :aria-label="isExpanded ? '收起宠物状态' : '展开宠物状态'"
      :aria-expanded="isExpanded"
      @click="isExpanded = !isExpanded"
    >
      <span aria-hidden="true">{{ isExpanded ? '−' : '+' }}</span>
    </button>

    <div v-if="showStatus && isExpanded" class="pet-widget__panel">
      <strong>{{ currentDefinition.name }}</strong>
      <span class="pet-widget__status">{{ statusLabel }}</span>
      <span v-if="snapshot.taskMessage" class="pet-widget__message">{{ snapshot.taskMessage }}</span>
      <button v-if="snapshot.state === 'waiting'" type="button" class="pet-widget__action" @click="send({ type: 'user-helped' })">
        继续任务
      </button>
      <button v-else-if="snapshot.state === 'failed'" type="button" class="pet-widget__action" @click="reset">
        重置状态
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.pet-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  color: #242b3d;
}

.pet-widget__button,
.pet-widget__toggle {
  border: 0;
  cursor: pointer;
  font: inherit;
}

.pet-widget__reminder {
  position: absolute;
  right: 12px;
  bottom: calc(100% + 14px);
  width: max-content;
  max-width: min(260px, calc(100vw - 32px));
  padding: 9px 13px 9px 11px;
  border: 1px solid rgba(255, 255, 255, .7);
  border-radius: 13px 13px 4px 13px;
  box-shadow: 0 8px 20px rgba(35, 48, 79, .2);
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  white-space: normal;
  animation: pet-reminder-flash .85s ease-in-out infinite alternate;
}
.pet-widget__reminder::after {
  position: absolute;
  right: 12px;
  bottom: -7px;
  width: 12px;
  height: 12px;
  border-right: 1px solid rgba(255, 255, 255, .7);
  border-bottom: 1px solid rgba(255, 255, 255, .7);
  background: inherit;
  content: '';
  transform: rotate(45deg);
}
.pet-widget__reminder.is-message { background: linear-gradient(135deg, #5c7cfa, #7864d8); }
.pet-widget__reminder.is-checklist { background: linear-gradient(135deg, #ed9f35, #ee6575); }
.pet-widget__reminder-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin: 0 6px 1px 0;
  border-radius: 50%;
  background: #fff;
  vertical-align: middle;
}

.pet-widget__button {
  position: relative;
  display: grid;
  place-items: center;
  padding: 0;
  background: transparent;
  border-radius: 50%;
  transition: filter .15s ease;
  touch-action: none;
  user-select: none;
}
.pet-widget__button:active { cursor: grabbing; }

.pet-widget__button:hover,
.pet-widget__button:focus-visible { filter: brightness(1.04); outline: none; }
.pet-widget__button:focus-visible { box-shadow: 0 0 0 3px rgba(64, 158, 255, .36); }
.pet-widget.is-reminding .pet-widget__button { animation: pet-widget-alert .7s ease-in-out infinite alternate; }

.pet-widget__progress {
  position: absolute;
  right: -5px;
  top: -5px;
  min-width: 31px;
  padding: 2px 5px;
  border-radius: 8px;
  background: #242b3d;
  color: #fff;
  font-size: 10px;
  text-align: center;
}

.pet-widget__toggle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  color: #667085;
  box-shadow: 0 3px 12px rgba(17, 25, 50, .18);
}
.pet-widget__toggle:hover,
.pet-widget__toggle:focus-visible { color: #409eff; outline: 2px solid rgba(64, 158, 255, .25); }

.pet-widget__panel {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 180px;
  padding: 12px;
  border: 1px solid #e8ecf3;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(17, 25, 50, .16);
  font-size: 12px;
}
.pet-widget__status { color: #667085; }
.pet-widget__message { color: #ef6b73; line-height: 1.45; }
.pet-widget__action {
  align-self: flex-start;
  padding: 4px 9px;
  border: 1px solid #d9e2f3;
  border-radius: 5px;
  background: #f6f8fc;
  color: #3268b8;
  cursor: pointer;
  font-size: 12px;
}

@keyframes pet-reminder-flash {
  from { filter: brightness(1); transform: translateY(0); }
  to { filter: brightness(1.22); transform: translateY(-3px); }
}
@keyframes pet-widget-alert {
  from { transform: translateY(0) rotate(-2deg); }
  to { transform: translateY(-8px) rotate(2deg); }
}
.pet-reminder-pop-enter-active,
.pet-reminder-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.pet-reminder-pop-enter-from,
.pet-reminder-pop-leave-to { opacity: 0; transform: translateY(8px) scale(.94); }

@media (max-width: 768px) {
  .pet-widget { right: 14px; bottom: 14px; }
  .pet-widget__panel { width: 165px; }
}
</style>
