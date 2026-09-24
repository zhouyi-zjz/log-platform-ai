import { PetStateMachine } from './stateMachine'
import type { PetController, PetControllerOptions, PetDefinition } from './types'

/** 创建控制器的工厂函数，统一隔离状态机实现，方便未来替换为远端/Worker 实现。 */
export function createPetController(
  definition: PetDefinition,
  options: PetControllerOptions = {}
): PetController {
  return new PetStateMachine(definition, options)
}
