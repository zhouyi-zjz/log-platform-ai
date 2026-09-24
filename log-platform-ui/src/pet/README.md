# 宠物模块

这个目录是应用宠物的独立领域模块，参考 Codex 宠物的状态反馈和 v2 精灵图约定。

## 目录职责

- `types.ts`：领域事件、动作、精灵图和快照契约。
- `constants.ts`：状态行号、帧数、16 个视线方向及中文标签。
- `actionRegistry.ts`：可插拔动作注册表。
- `stateMachine.ts`：框架无关的状态机，处理任务开始、进度、完成、失败和确认等待。
- `usePet.ts`：Vue 组合式 API，负责生命周期和响应式快照同步。
- `components/PetSprite.vue`：v2 精灵图渲染器，同时提供 CSS 兜底形象。
- `components/PetWidget.vue`：可直接挂载的交互组件。
- `settings.ts`：内置宠物目录、大小/透明度设置和 `localStorage` 持久化。
- `reminders.ts`：好友私信和 Checklist 到点提醒的全局状态服务。

## 扩展一只宠物

```ts
import { createPetController } from '@/pet'
import PetWidget from '@/pet/components/PetWidget.vue'

const controller = createPetController({
  id: 'my-pet',
  name: '我的宠物',
  spriteSheet: {
    url: '/assets/pets/my-pet/spritesheet.png',
    frameWidth: 192,
    frameHeight: 208,
    columns: 8,
    rows: 11,
    spriteVersionNumber: 2
  },
  actions: [
    { id: 'my-review', label: '自定义检查', state: 'review', durationMs: 2000 }
  ]
})
```

`PetWidget` 暴露了 `startTask`、`updateProgress`、`completeTask`、`failTask`、`needApproval` 和 `reset`，业务页面可以把后端任务流映射到这些方法，而不需要直接修改状态机。

## 宠物设置

设置页路由为 `/ai/pet-settings`，设置会保存在 `haige-ai-pet-settings` 本地存储键中：

- `size`：48-144 像素；
- `opacity`：0.2-1；
- `petId`：当前选中的内置宠物。

新增内置宠物时，在 `settings.ts` 的 `BUILT_IN_PETS` 中加入 `PetDefinition`，提供 `appearance` 即可在没有精灵图资源时展示不同的兜底外观。

## 全局提醒

通用布局会订阅 `/user/queue/im`，收到发给当前用户的私信时显示“好友***发来信息”气泡；同时每 10 秒查询当天 Checklist，在未完成任务的日期和 `HH:mm` 与系统时间一致时显示“***事项要准备开始啦”。提醒会自动消失，点击气泡也可以立即关闭。悬浮宠物支持鼠标/触控拖动，位置保存在同一份宠物设置中，并会被限制在可视区域内。拖动结束后才写入本地存储，避免高频写入。
