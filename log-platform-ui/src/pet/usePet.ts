import { onMounted, onUnmounted, shallowRef, type ShallowRef } from 'vue'
import { createPetController } from './createPetController'
import { DEFAULT_PET } from './defaultPet'
import type { PetController, PetDefinition, PetEvent, PetSnapshot } from './types'

export interface UsePetOptions {
  readonly definition?: PetDefinition
  readonly controller?: PetController
  readonly autoStart?: boolean
}

export interface UsePetResult {
  readonly controller: PetController
  readonly snapshot: ShallowRef<PetSnapshot>
  dispatch(event: PetEvent): PetSnapshot
}

/**
 * Vue 组合式 API。
 * 组件只关心响应式快照，控制器仍保持框架无关，因此同一领域模块可被其他页面复用。
 */
export function usePet(options: UsePetOptions = {}): UsePetResult {
  const isOwner = !options.controller
  const controller = options.controller ?? createPetController(options.definition ?? DEFAULT_PET)
  const snapshot = shallowRef(controller.getSnapshot())
  const unsubscribe = controller.subscribe(next => { snapshot.value = next })

  onMounted(() => {
    if (options.autoStart !== false) controller.start()
  })

  onUnmounted(() => {
    unsubscribe()
    if (isOwner) controller.destroy()
  })

  return {
    controller,
    snapshot,
    dispatch: event => controller.dispatch(event)
  }
}
