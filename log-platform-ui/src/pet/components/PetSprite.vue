<script setup lang="ts">
/**
 * 宠物渲染器：优先使用 Codex v2 精灵图，没有资源时显示可访问的 CSS 兜底形象。
 * 组件只负责把快照映射为视觉，不在这里编写业务状态判断。
 */
import { computed } from 'vue'
import { PET_STATE_LABEL, PET_STATE_ROW } from '../constants'
import type { PetDefinition, PetSnapshot } from '../types'

const props = withDefaults(defineProps<{
  definition: PetDefinition
  snapshot: PetSnapshot
  size?: number
}>(), {
  size: 88
})

const sprite = computed(() => props.definition.spriteSheet)
const appearance = computed(() => props.definition.appearance ?? {})
const frameWidth = computed(() => sprite.value?.frameWidth ?? 192)
const frameHeight = computed(() => sprite.value?.frameHeight ?? 208)
const columns = computed(() => sprite.value?.columns ?? 8)
const rows = computed(() => sprite.value?.rows ?? 11)
// 初始快照没有视线事件，继续使用标准 idle；明确收到 look-at(000) 后才使用“向上看”帧。
const usesLookDirection = computed(() => props.snapshot.state === 'idle' && (
  props.snapshot.direction !== 0 || props.snapshot.lastEvent === 'look-at'
))

/** 标准动作位于 0-8 行；视线方向位于 9、10 行，每行 8 个方向。 */
const spritePosition = computed(() => {
  if (sprite.value && !usesLookDirection.value) {
    const stateRow = PET_STATE_ROW[props.snapshot.state]
    return { column: props.snapshot.frame % columns.value, row: stateRow }
  }
  const directionIndex = [0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5]
    .indexOf(props.snapshot.direction)
  return {
    column: Math.max(0, directionIndex % 8),
    row: 9 + Math.floor(Math.max(0, directionIndex) / 8)
  }
})

const spriteStyle = computed(() => ({
  width: `${frameWidth.value}px`,
  height: `${frameHeight.value}px`,
  transform: `scale(${props.size / frameWidth.value})`,
  backgroundImage: `url(${sprite.value?.url})`,
  backgroundSize: `${columns.value * 100}% ${rows.value * 100}%`,
  backgroundPosition: `${columns.value === 1 ? 0 : (spritePosition.value.column / (columns.value - 1)) * 100}% ${rows.value === 1 ? 0 : (spritePosition.value.row / (rows.value - 1)) * 100}%`
}))

const fallbackStyle = computed<Record<string, string>>(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  '--pet-body-color': appearance.value.bodyColor ?? '#dbe4ff',
  '--pet-accent-color': appearance.value.accentColor ?? '#8c9bd4',
  '--pet-border-color': appearance.value.borderColor ?? '#242b3d'
}))
const stateLabel = computed(() => PET_STATE_LABEL[props.snapshot.state])
</script>

<template>
  <div
    class="pet-sprite"
    :class="[`is-${snapshot.state}`, `is-${definition.appearance?.silhouette ?? 'orb'}`, { 'is-hovered': snapshot.isHovered }]"
    :style="fallbackStyle"
    role="img"
    :aria-label="`${definition.name}，${stateLabel}`"
  >
    <div v-if="sprite" class="pet-sprite__atlas" :style="spriteStyle" aria-hidden="true" />
    <div v-else class="pet-sprite__fallback" aria-hidden="true">
      <span class="pet-sprite__ear pet-sprite__ear--left" />
      <span class="pet-sprite__ear pet-sprite__ear--right" />
      <span class="pet-sprite__face">
        <span class="pet-sprite__eye pet-sprite__eye--left" />
        <span class="pet-sprite__eye pet-sprite__eye--right" />
        <span class="pet-sprite__mouth" />
      </span>
      <span class="pet-sprite__badge">{{ definition.emoji || '✦' }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pet-sprite {
  position: relative;
  flex: 0 0 auto;
  overflow: visible;
  display: grid;
  place-items: center;
}

.pet-sprite__atlas {
  transform-origin: center bottom;
  background-repeat: no-repeat;
  will-change: background-position;
}

.pet-sprite__fallback {
  position: relative;
  width: 74%;
  height: 70%;
  margin-top: 12%;
  border: 2px solid #242b3d;
  border-radius: 42% 42% 35% 35%;
  background: linear-gradient(145deg, color-mix(in srgb, var(--pet-body-color) 80%, #fff), var(--pet-body-color));
  border-color: var(--pet-border-color);
  box-shadow: 0 7px 0 var(--pet-accent-color), 0 12px 22px rgba(17, 25, 50, .2);
  transform-origin: center bottom;
}

.is-hovered .pet-sprite__fallback { transform: translateY(-3px) rotate(-2deg); }
.is-jumping .pet-sprite__fallback { animation: pet-jump .7s ease-in-out infinite alternate; }
.is-waving .pet-sprite__fallback::after {
  content: '👋';
  position: absolute;
  right: -30%;
  top: 13%;
  font-size: .52em;
  animation: pet-wave .5s ease-in-out infinite alternate;
}
.is-failed .pet-sprite__fallback { filter: saturate(.7); transform: rotate(-5deg); }
.is-running .pet-sprite__fallback { animation: pet-breathe .55s ease-in-out infinite alternate; }
.is-cat .pet-sprite__fallback { border-radius: 45% 45% 35% 35%; }
.is-cat .pet-sprite__ear { border-radius: 20% 70% 20% 60%; }
.is-robot .pet-sprite__fallback { border-radius: 18%; }
.is-cloud .pet-sprite__fallback { border-radius: 50% 44% 38% 42%; }

.pet-sprite__ear {
  position: absolute;
  top: -18%;
  width: 32%;
  height: 30%;
  border: 2px solid var(--pet-border-color);
  border-radius: 70% 20% 60% 20%;
  background: var(--pet-accent-color);
}
.pet-sprite__ear--left { left: 9%; transform: rotate(-24deg); }
.pet-sprite__ear--right { right: 9%; transform: scaleX(-1) rotate(-24deg); }
.pet-sprite__face { position: absolute; inset: 26% 16% 12%; }
.pet-sprite__eye {
  position: absolute;
  top: 22%;
  width: 14%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--pet-border-color);
}
.pet-sprite__eye--left { left: 23%; }
.pet-sprite__eye--right { right: 23%; }
.pet-sprite__mouth {
  position: absolute;
  left: 43%;
  top: 51%;
  width: 14%;
  height: 9%;
  border-bottom: 2px solid var(--pet-border-color);
  border-radius: 50%;
}
.pet-sprite__badge {
  position: absolute;
  right: -9%;
  top: -6%;
  color: var(--pet-accent-color);
  font-size: .34em;
  font-weight: 700;
}

@keyframes pet-jump { to { transform: translateY(-12%); } }
@keyframes pet-wave { to { transform: rotate(15deg) translateY(-5%); } }
@keyframes pet-breathe { to { transform: translateY(-2%); } }
</style>
