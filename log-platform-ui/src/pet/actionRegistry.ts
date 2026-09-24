import type { PetActionDefinition, PetActionContext, PetState } from './types'

/**
 * 动作注册表。
 * 采用 Map 而不是散落的 if/else，便于业务方在不修改核心模块的情况下扩展动作。
 */
export class PetActionRegistry {
  private readonly actions = new Map<string, PetActionDefinition>()

  constructor(definitions: readonly PetActionDefinition[] = []) {
    definitions.forEach(definition => this.register(definition))
  }

  register(definition: PetActionDefinition): this {
    if (!definition.id.trim()) {
      throw new Error('宠物动作 id 不能为空')
    }
    if (this.actions.has(definition.id)) {
      throw new Error(`宠物动作已存在：${definition.id}`)
    }
    this.actions.set(definition.id, definition)
    return this
  }

  get(id: string): PetActionDefinition | undefined {
    return this.actions.get(id)
  }

  getByState(state: PetState): PetActionDefinition | undefined {
    return [...this.actions.values()].find(action => action.state === state)
  }

  list(): readonly PetActionDefinition[] {
    return [...this.actions.values()]
  }

  notifyEnter(context: PetActionContext): void {
    this.getByState(context.state)?.onEnter?.(context)
  }

  notifyExit(context: PetActionContext): void {
    this.getByState(context.state)?.onExit?.(context)
  }
}
