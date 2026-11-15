# 通用任务接口与任务来源

当前 `master` 的任务入口已经从 FTB Quests 专属工具抽出。FTBQ 是第一个适配实现；其他任务模组或未来内置任务系统可以复用列表、详情、提交、领奖、说明书、卡片和固定 HUD，无需重新编写整套 Agent 工作流。

## 数据和来源

`assistant.quest.Quest` 是只读任务数据接口：`id()`、`source()`、`title()`、`chapter()` 和 `progress()`。进度使用 0～100 的百分比。现有 `QuestBriefOutput`、`QuestOutput`、`QuestPinOutput` 实现该接口，分别用于摘要、完整详情和实时跟踪；它们仍是各自独立的 Codec 数据对象，不持有 FTBQ 等模组的原生对象。

`QuestSource` 包含稳定的 `Identifier id` 和用于显示的 `Component name`。显示名由服务端传递，可使用翻译键；列表、详情、通知和固定卡片显示来源。新增文案及“来源／未知来源／FTB 任务”均提供中英文语言键。

任务与领奖引用使用 `QuestSource.key(localId)` 生成字符串：

```text
system:ftb_quests#0123456789ABCDEF
example:quests#chapter_1/welcome
```

`#` 前是来源 ID，之后是来源自己的不透明 ID。通用输入不再要求 16 位十六进制；FTBQ 的十六进制解析只留在兼容层。来源显示名不参与身份比较，也不能用于绕过权限。未带来源的旧卡片 ID 仅在恰好一个任务来源启用时消歧，不能随意发送给第一个来源。

任务列表的章节／章节组按来源隔离，再保留来源自身的组织信息。只展示此次查询命中的章节，不枚举其他来源的空章节。`QuestBriefOutput.icon` 可以提供 Saturn `IconReference`，使用通用 `system:icon` 节点；FTBQ 未提供这个字段时，继续走原生任务图标和可选依赖隔离。

## 任务来源接口

`assistant.quest.QuestProvider` 是服务端接入点，不引用任何客户端或第三方任务类。基础接口只有：

| 方法 | 返回值 | 职责 |
| --- | --- | --- |
| `source()` | `QuestSource` | 声明来源身份和显示名。 |
| `query(context, input)` | `CompletionStage<List<QuestBriefOutput>>` | 按 active／claimable／submittable 列出可见结果，不执行提交和领奖。 |
| `find(context, input)` | `CompletionStage<List<QuestBriefOutput>>` | 搜索可读任务；优先精确名称，返回实际 ID。 |
| `detail(context, input, revision)` | `CompletionStage<QuestOutput>` | 按已经解析好的 ID 读取一个任务；保留各详情区块的分页偏移。 |

其他能力通过二级接口声明，不通过一串布尔值判断来源类型：

| 接口 | 额外方法 | 用途 |
| --- | --- | --- |
| `QuestProvider.Submission` | `submit(context, input, revision)` | 显式提交资源或完成支持的勾选任务；不得顺便领取奖励。 |
| `QuestProvider.Rewards` | `claim(context, input)` | 消费先前签发的领奖引用；自行检查归属、有效期、任务和奖励状态。 |
| `QuestProvider.Guides` | `guides(context, input)` | 返回当前玩家有权读取的说明书片段和正文。 |
| `QuestProvider.StarterGifts` | `gifts(context)`、`claimGifts(context, input)` | 可选的新手礼包预览和显式领取，不强制其他来源使用 FTBQ 的礼包判定。 |
| `QuestProvider.Tracking` | `tracking(player, localId)` | 服务端线程读取轻量 `QuestPinOutput`；不提交、不领奖、不生成 AI 请求。 |
| `QuestProvider.Narration` | `load(context, questId)` | 可选地为任务播报提供真实任务快照，按来源分派并隔离缓存键。 |

没有 `Submission`／`Rewards` 的来源不进入对应的可提交／可领奖列表。没有说明书或礼包能力的来源不参加对应汇总；直接请求不支持的写操作返回 `UNSUPPORTED`。通用工作流外壳保持一致，避免同一动作因多个来源重复注册产生冲突。

## 装配与生命周期

来源仍由自己的模块决定是否启用，不另建一个脱离模块权限的全局任务注册表。在服务端模块的 `workflows(ModuleContext)` 中调用：

```java
MyQuestRuntime runtime = context.sharedRuntime(MyQuestRuntime.class, () -> new MyQuestRuntime(context));
return QuestWorkflows.install(context, runtime);
```

其中 `MyQuestRuntime` 实现 `QuestProvider` 及实际支持的能力；有维护需求时同时实现 `PlayerModule`，由该模块的 `runtime(context)` 返回同一个实例。运行时只由 `ModuleManager` 负责 validate、玩家 Session、tick 与 close，不另设以 `MinecraftServer` 为键的静态 Map。多个配置共享同一来源时使用同一个 Provider 对象；不同实现不能冒用相同来源 ID。

`QuestWorkflows.install` 注册来源的模块 owner，装配共用工作流与工具，并为 `Tracking` 注册常驻卡片来源。`QuestService` 通过 `ModuleContext.sharedRuntime` 在单个服务端中共享，管理所有来源与玩家最近列表；服务器关闭时由管理器清理。没有创建新的 `system_module` 类型或改变现有 `system:ftb_quests` 定义。

新增任务模组适配必须把可选类隔离在自己的兼容包中，仅在确认依赖存在后创建 Provider。公共接口、Codec 和客户端卡片不能引用它的原生任务类。FTBQ 当前仍通过 `QuestSystemModule.available()` 和检查后的反射工厂进入兼容层。

## 分派、编号与权限

查询会汇总玩家已启用来源的结果并统一编号。名称搜索的单个匹配直接打开详情，不覆盖之前的编号列表；多个匹配作为新的列表。按 ID 查询、提交、领奖和 pin 都回到 ID 所属来源，不能因为其他来源可用而换源重试。

编号列表记录系统代次、聊天历史代次、有效期及每个摘要的 `revision`；退出、撤销或权限变化时失效。Provider 应为摘要填入能识别队伍／账户／任务定义变更的短期版本标识。按编号选中时，通用层把该版本交给 detail／submit；Provider 必须在访问原生状态的同一服务端线程操作中重新核对。直接按 ID 访问不带旧版本，但仍必须完整检查当前可见性、依赖、队伍和资源。

FTBQ 的版本组合包含队伍身份和当前原生对象身份；它只负责失效检测，不是授权令牌。FTBQ 原先保存的独立编号列表已移除，避免多个来源的编号互相覆盖。领奖引用仍保留玩家、队伍、任务／奖励版本、完成轮次、选择表和有效期等原有校验。

`QuestService.checkAccess(context, sourceId)` 检查来源关联的模块实例是否仍启用。异步排队的 Provider 在真正读写世界的回调内也必须调用它，防止排队期间移除模块后继续执行。FTBQ 通过 `FtbQuestAccess.onPlayer` 统一执行这一步；原生目标提交和奖励领取逻辑仍由 FTBQ 完成。

## 工具与交互

通用工具 ID 已去掉 FTBQ 专属前缀：

| 工具 | 返回类型 |
| --- | --- |
| `quest.query` | `List<QuestBriefOutput>` |
| `quest.find` | `List<QuestBriefOutput>` |
| `quest.detail` | `QuestOutput` |
| `quest.submit` | `QuestSubmitOutput` |
| `quest.claim` | `QuestClaimOutput` |
| `quest.guide` | `QuestGuideOutput` |
| `quest.starter_gifts` | `StarterGiftOutput` |
| `quest.starter_gift` | `StarterGiftOutput` |

`system:quest_query` 等 AgentAction ID、系统界面内的任务指令、列表分页和确认策略保留。任务列表／详情操作仍通过原有按钮协议执行；提交保持显式命令或按钮触发，查询不会自动勾选或领取。多个任务新解锁继续合并列表、单个解锁展示详情、完成任务保留独立通知。

此轮提供通用接口与实际 FTBQ 迁入，不新增内置任务定义、目标追踪器或玩家委托引擎。后续内置任务系统只需实现相同 Provider 和自身持久化／事件逻辑。

本轮只做编译与静态核对，不启动 Minecraft，也不把编译通过当成多来源游戏内验收。
