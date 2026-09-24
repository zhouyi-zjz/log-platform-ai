import { computed, reactive } from 'vue'
import { DEFAULT_PET } from './defaultPet'
import type { PetDefinition } from './types'

const STORAGE_KEY = 'haige-ai-pet-settings'

/** 内置宠物目录；后续接入服务端时可以将这里替换为接口返回的数据。 */
export const BUILT_IN_PETS: readonly PetDefinition[] = [
  {
    ...DEFAULT_PET,
    id: 'haige-coder',
    name: '小U助手',
    description: '适合日常对话和任务处理的默认助手。',
    emoji: '✦',
    appearance: { silhouette: 'orb', bodyColor: '#dbe4ff', accentColor: '#8c9bd4', borderColor: '#242b3d' }
  },
  {
    id: 'star-cat',
    name: '星际猫',
    description: '活泼敏捷，适合喜欢轻快互动的用户。',
    emoji: '✧',
    appearance: { silhouette: 'cat', bodyColor: '#ffe0a8', accentColor: '#f3a95f', borderColor: '#543c35' },
    actions: DEFAULT_PET.actions
  },
  {
    id: 'cloud-helper',
    name: '云朵助手',
    description: '柔和安静，适合长时间陪伴工作。',
    emoji: '☁',
    appearance: { silhouette: 'cloud', bodyColor: '#e3f7ff', accentColor: '#79c9de', borderColor: '#31586b' },
    actions: DEFAULT_PET.actions
  },
  {
    id: 'mini-robot',
    name: '迷你机器人',
    description: '专注可靠，适合观察任务进度和结果。',
    emoji: '◆',
    appearance: { silhouette: 'robot', bodyColor: '#d8f3e5', accentColor: '#62b987', borderColor: '#244c3b' },
    actions: DEFAULT_PET.actions
  }
]

export interface PetSettingsState {
  petId: string
  size: number
  opacity: number
  positionX?: number
  positionY?: number
}

const DEFAULT_SETTINGS: PetSettingsState = {
  petId: DEFAULT_PET.id,
  size: 76,
  opacity: 1,
  positionX: undefined,
  positionY: undefined
}

function loadSettings(): PetSettingsState {
  if (typeof window === 'undefined') return { ...DEFAULT_SETTINGS }
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '{}') as Partial<PetSettingsState>
    return {
      petId: typeof parsed.petId === 'string' ? parsed.petId : DEFAULT_SETTINGS.petId,
      size: clamp(parsed.size ?? DEFAULT_SETTINGS.size, 48, 144),
      opacity: clamp(parsed.opacity ?? DEFAULT_SETTINGS.opacity, 0.2, 1),
      positionX: typeof parsed.positionX === 'number' ? parsed.positionX : undefined,
      positionY: typeof parsed.positionY === 'number' ? parsed.positionY : undefined
    }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Number.isFinite(value) ? value : min))
}

const state = reactive<PetSettingsState>(loadSettings())

function persist(): void {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

export function usePetSettings() {
  const selectedPet = computed(() => BUILT_IN_PETS.find(pet => pet.id === state.petId) ?? BUILT_IN_PETS[0])

  function setPet(petId: string): void {
    if (BUILT_IN_PETS.some(pet => pet.id === petId)) {
      state.petId = petId
      persist()
    }
  }

  function setSize(size: number): void {
    state.size = Math.round(clamp(size, 48, 144))
    persist()
  }

  function setOpacity(opacity: number): void {
    state.opacity = Math.round(clamp(opacity, 0.2, 1) * 100) / 100
    persist()
  }

  function setPosition(positionX: number, positionY: number): void {
    state.positionX = Math.round(positionX)
    state.positionY = Math.round(positionY)
    persist()
  }

  function clearPosition(): void {
    state.positionX = undefined
    state.positionY = undefined
    persist()
  }

  function reset(): void {
    Object.assign(state, DEFAULT_SETTINGS)
    persist()
  }

  return {
    settings: state,
    selectedPet,
    pets: BUILT_IN_PETS,
    setPet,
    setSize,
    setOpacity,
    setPosition,
    clearPosition,
    reset
  }
}
