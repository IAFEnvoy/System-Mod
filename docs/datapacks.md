# 扩展配置

旧的 `SystemFunction` 执行机制已移除。执行预算、模型连接、本地路由和成就规则分别来自服务端 `config/system/` 中的 `system-agent.json`、`system-model.json`、`system-semantic.json` 和 `system-advancements.json`；系统名称与介绍使用下述新的系统定义注册表，签到奖励池、商城目录、抽奖奖池和模型提示词仍有独立的数据包入口。客户端不能提供价格、商品内容、抽奖权重或触发规则。

完整示例见 [执行预算](../examples/agent/system-agent.json)、[模型连接](../examples/agent/system-model.json)、[本地路由](../examples/agent/system-semantic.json) 和 [成就规则](../examples/agent/system-advancements.json)。首次初始化会从 JAR 内 `defaults/` 复制各默认文件，修改后正常重启。密钥只使用 `apiKeyEnvironment` 指定的环境变量。默认 DeepSeek 与其他 OpenAI 兼容服务的配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

`system-semantic.json` 独立控制轻量本地意图路由。新配置默认 ENFORCE，不需要模型文件，说明见 [轻量本地意图路由](semantic-routing.md)。

## 系统定义

系统参数使用 NeoForge 同步数据包注册表 `system:system`，文件路径为 `data/<namespace>/system/system/<path>.json`，条目 ID 为 `<namespace>:<path>`。当前所有玩家使用 `system:default`；JAR 内默认文件是 `data/system/system/system/default.json`，在数据包中覆盖同路径文件即可定制。也可新增其他 ID，供 Java 扩展读取，但本次尚未加入玩家选择或绑定其他系统的功能。

例如，将默认系统改成“签到系统”：

```json
{
  "name": "example.system.sign_in.name",
  "description": "example.system.sign_in.description",
  "modules": [
    "system:signin",
    {
      "id": "system:ftb_quests",
      "condition": { "type": "system:has_system" }
    }
  ]
}
```

`name` 和 `description` 必填，内容均为翻译键，客户端通过 `Component.translatable` 按玩家语言显示。自定义键需要配套客户端资源包，在 `assets/example/lang/zh_cn.json` 中提供：

```json
{
  "example.system.sign_in.name": "签到系统",
  "example.system.sign_in.description": "本系统旨在每日给宿主提供各类道具和资源。"
}
```

在 `assets/example/lang/en_us.json` 中提供相同的键，例如 `Daily Sign-in System` 与 `This system provides its host with items and resources every day.`。服务端数据包同步翻译键与模块 ID，不同步语言文件；仅在服务端添加语言文件无法让客户端获得翻译。也可以在默认 JSON 中保留内置键 `system.definition.default.name` / `system.definition.default.description`，通过资源包覆盖它们。

`modules` 现在控制每个已绑定玩家可用的功能，省略时为 `[]`。条目可以是模块 ID 字符串，也可以是 `{ "id": "system:shop", "condition": ... }`；省略 condition 表示无条件启用，condition 使用 Saturn 的 EntityCondition Codec，可写单个对象或条件数组（数组全部成立）。同一个 ID 的多条定义只要有一条成立即可启用。未列出的可选功能关闭；`core`、`binding`、`usage` 是基础设施，保留聊天、绑定管理与独立用量账本，不能通过模块开关关闭。客户端设置、记忆控制和语音输入仍属于本地功能。

模块 ID 是正式内置注册表 `SystemRegistries.SERVER_MODULE` 的条目 ID，`ServerModule.id()` 从注册表取得该值，并非 Agent 工作流 ID。客户端定义位于独立的 `SystemRegistries.CLIENT_MODULE`，客户端入口通常使用对应服务端模块的同名 ID。例如：

| 功能 | 模块 ID |
| --- | --- |
| 玩家查询 | `system:player` |
| 物品查询 | `system:items` |
| 签到 | `system:signin` |
| 收件箱与交付 | `system:inbox` |
| 商城（浏览、报价、购买） | `system:shop` |
| 抽奖（浏览、预览、抽取） | `system:lottery` |
| 成就工作流 | `system:advancement` |
| FTB 任务（列表、详情、提交、领奖、新手礼包） | `system:ftb_quests` |

发布默认定义启用全部必装的业务模块，FTB 由整合包显式加入；上述示例包含可选任务模块，未安装 FTB 时该条目不启用、不加载对应 API。开发源集的默认定义另外启用 FTB，不进入发布 JAR。

条件在玩家初始化完成后、交互请求前及每 20 个服务端 tick 在主线程重新求值。变化后同步玩家实际模块与指令列表，刷新完整侧栏，清理路由缓存与交易报价。帮助、补全、本地语义候选、模型能力目录和实际执行共用权限过滤；旧卡片的结构化动作以及已经排入游戏线程的工具也会重新检查权限。功能定义和工具仍在服务器启动时注册，开关不卸载 Java 类或重新注册工作流。

例如，让商城跟随签到模块是否启用：

```json
{
  "id": "system:shop",
  "condition": { "type": "system:has_module", "module": "system:signin" }
}
```

条件异常或循环依赖会关闭涉及的模块并记入服务端日志，避免递归溢出。手动添加可覆盖条件、手动移除可禁用定义中默认提供的模块，基础设施和未安装模块均不接受覆盖。

### 模块 action 与 condition

以下类型注册到 Saturn 的实体 action / condition 注册表，可用于 Saturn 支持的触发、顺序动作及条件组合；不是交给模型执行的文本指令。

| 类型 ID | 种类 | 字段与行为 |
| --- | --- | --- |
| `system:add_module` | EntityAction | `module`：为已绑定玩家持久添加一个已安装模块 |
| `system:remove_module` | EntityAction | `module`：为已绑定玩家持久禁用一个已安装模块 |
| `system:has_module` | EntityCondition | `module`：检查实际启用状态，解析模块条件时递归解析依赖 |
| `system:has_system` | EntityCondition | 检查玩家是否已绑定且初始化完成；可选 `system` 指定当前系统 ID |

```json
{ "type": "system:add_module", "module": "system:lottery" }
```

```json
{ "type": "system:remove_module", "module": "system:lottery" }
```

```json
{ "type": "system:has_system", "system": "system:default" }
```

目前绑定对象统一为 `system:default`，其他 system ID 不会匹配。实体不是服务端玩家、运行时未就绪或玩家未绑定时，action 不改数据，condition 返回 false。手动增删通过主线程 API `AssistantServer.get(server).moduleManager().add/remove(player, moduleId)`，移除立即停止模块；添加会异步初始化存储，成功后才发布可用状态，顺序动作中的后续 has_module 对刚添加模块可能暂为 false。权限解析内部的依赖条件使用目标模块集合，避免初始化顺序影响条件计算。可通过 `hasModule(uuid, id)`、`hasSystem(uuid)`、`enabled(uuid)` 查询不可变状态。

模块覆盖保存在玩家独立数据文件 `<存档>/system-agent/<玩家 UUID>.json` 的 `moduleData` 字段中，由 `system:module_access` DataStorage 持有。数据按玩家 UUID 隔离，登出与重登保留，成功 `revoke/reset` 删除整份玩家数据，`forget` 不清除；累计用量仍保存在单独的世界 SavedData 文件中。

功能模块在 `ServerModule.storages()` 声明 ModuleStorage，Codec 通过 Saturn DATA_STORAGE_TYPE 独立注册，管理器统一创建数据和驱动生命周期；事务内使用 `ModuleData.get(data, SomeSystemModule.STORAGE)`。PlayerData.moduleData 使用 Saturn DataStorageHolder，以模块 ID 和 Java 类型寻址，Codec 类型 ID 独立。商城和抽奖持有独立交易存储，Core 持有权限和幂等日志。普通业务修改在 AgentStore.update 原子落盘；DataStorage.tick 在主线程内存事务中执行，变化需 markDirty，由管理器合并异步保存。不使用玩家附件，停用保留账本，revoke/reset 才清空。

名称应用于助手标题、对话说话人和通知标题，介绍显示在欢迎区及首次绑定通知中；简约界面空闲时也展示介绍，绑定播报包含系统名称。定义从当前连接的原生注册表读取，不写入玩家附件或业务存档。该注册表在世界加载时读取，修改 JSON 后需要重开世界或重启服务器再连接；`/reload` 仍用于下述奖励、商品、奖池与提示词，不热更新系统定义。资源包的翻译文本可按客户端正常资源重载流程更新。

## 提示词模板

模型提示词使用 UTF-8 `.txt` 文件，支持 UTF-8 BOM，不经过 JSON 注册表。服务端 `PromptReloadListener` 在启动和 `/reload` 时扫描 `data/<namespace>/system/prompts/<path>.txt`，模板 ID 为 `<namespace>:<path>`。JAR 内自带以下默认模板；在数据包中放置同路径文件即可按数据包优先级整份覆盖，不必复制另一份未修改的模板：

| 数据包文件 | 模板 ID | 可用占位符 |
| --- | --- | --- |
| `data/system/system/prompts/routing.txt` | `system:routing` | `{{player_language}}`、`{{capabilities}}` |
| `data/system/system/prompts/sign_in.txt` | `system:sign_in` | `{{player_language}}` |
| `data/system/system/prompts/quest_narration.txt` | `system:quest_narration` | `{{player_language}}` |
| `data/system/system/prompts/quest_background.txt` | `system:quest_background` | `{{player_language}}` |

`{{player_language}}` 由执行上下文自动提供，例如 `zh_cn`、`en_us`，来源为 MC 同步的玩家语言，模块不能通过同名参数覆盖它。`{{capabilities}}` 由路由器传入当前已注册的交互模块 JSON，包含说明和输入 schema；路由模板必须保留这个占位符。签到的玩家快照、候选奖励和日期仍通过用户消息的结构化 JSON 传入，不内联成系统指令。

例如，签到模板中可以写 `Write one short reason in {{player_language}}.`。占位符名使用小写字母、数字或下划线，首位必须为字母；不支持表达式、脚本或递归替换。变量值中的 `{{...}}`、`$`、反斜杠均按原文保留，不执行第二次展开；普通 JSON 的单层或嵌套花括号可直接使用。`{{` 保留给占位符语法。内置模板使用未知占位符、语法错误、空文件、缺失必要模板或移除路由的 `{{capabilities}}` 都会拒绝重载。

模板数量和资源文件大小不设额外硬上限；实际模型请求仍受模块的输入和 Token 预算约束。成功安装服务器资源后，在下一服务端 tick 发布不可变模板快照；失败时继续使用之前的快照，不发布半份模板。已经开始执行的任务保持原版本，新任务读取新版本；精确意图缓存的键也包含版本，旧任务的模型决策不会被新版模板复用。移除覆盖文件后恢复 JAR 内默认模板，无需复制到 `config`。

扩展模块可添加其他命名空间或子目录，例如 `data/example/system/prompts/notice.txt`，然后使用 `context.prompt("example:notice", Map.of("event_name", name))` 渲染，再将结果传给原来的 `context.model(...)`。扩展占位符由模块提供，缺失变量在请求模型前报错；提示词文件本身不会自动注册模块或获得工具权限。预算、奖励候选校验、玩家隔离与支付确认仍由 Java 代码强制执行。

## 奖励目录

奖励和商品只从下述数据包目录读取，不再由 `AgentSettings` 提供列表或兜底。条目 ID 来自文件路径，正文包含简短 `label` 和 `items` 列表；商品另有 Saturn `costs` 列表。产出物品直接使用原版 `ItemStackTemplate.CODEC`，包含注册 ID、可选的 `count`（默认 1，原版范围 1～99）及 `components`。签到奖励和商品产出每包为 1～36 项，与交付时主背包预留空位的规则一致。重载阶段只读取模板，不构建 `ItemStack`；实际扣款前或交付时才调用 `create()`，由原版校验最终组件及产出的实际堆叠上限。支付定义使用 Saturn `Cost.LIST_CODEC`，数量由 `amount` 提供，不受产出模板的堆叠数量限制。

AI 只返回候选奖励 ID，不能修改条目数量。入账保存完整奖励快照，修改目录不会改写已经获得的权益。正常签到必须成功调用 AI；成就固定奖励不受此要求影响。

## 数据包目录

完整可复制的示例位于 [examples/commerce-pack](../examples/commerce-pack)。把该目录复制到服务器存档的 `datapacks/` 下，使用正常的数据包加载流程或 `/reload`。示例不会自动安装，不进入发布 JAR。`pack.mcmeta` 格式根据当前依赖的 Minecraft 26.1.2 `version.json` 设置为 101.1。

| 文件路径 | 条目 ID | 用途 |
| --- | --- | --- |
| `data/<namespace>/system/signin_rewards/<path>.json` | `<namespace>:<path>` | AI 签到候选奖励包 |
| `data/<namespace>/system/shop_offers/<path>.json` | `<namespace>:<path>` | 商城商品和价格 |
| `data/<namespace>/system/lottery_pools/<path>.json` | `<namespace>:<path>` | 抽奖奖池、奖品权重和单次消耗 |

三个目录的所有有效资源按条目 ID 汇总：不同路径会新增条目，同路径按数据包优先级覆盖。JAR 内置奖励为 `system:food`、`system:mining`、`system:building`，商品为 `system:torch_bundle`，奖池为 `system:supplies`，默认内容和自定义内容使用同一加载流程。`{"enabled":false}` 可以覆盖并禁用单个条目；需要空目录时，禁用该类所有有效条目，或通过数据包过滤移除资源。移除覆盖文件会恢复下层数据包的同路径条目；若最终没有有效资源则保持空目录，不从配置补回。奖励包、商品、奖池及奖品总数和资源文件大小不设额外硬上限；每个奖励包内产出使用上述 1～36 项限制，付款材料列表不设额外项数上限。

签到包文件：

```json
{
  "label": "采矿补给",
  "items": [{ "id": "minecraft:torch", "count": 16 }]
}
```

商城兑换文件：

```json
{
  "label": "火把补给",
  "items": [{ "id": "minecraft:torch", "count": 4 }],
  "costs": [
    { "type": "saturn:item", "items": ["minecraft:cobblestone"], "amount": 16 }
  ]
}
```

`costs` 通过 Saturn 的 Cost 类型注册表分派，可以混合物品、资源及其他已注册的费用；列表各项都需要支付。启用的商品必须显式提供 `costs`，空列表 `[]` 表示免费。`saturn:item.items` 中的匹配项是可替代关系，例如 `["minecraft:cobblestone", "#minecraft:planks"]` 表示总计消耗 `amount` 个圆石或木板，不是每种各扣一次。普通物品 ID 只匹配物品类型，可能消耗带名称、附魔或其他组件的同类物品；组件条件使用 Saturn 注册的 matcher。实际读取玩家 `Inventory` 暴露的槽位，匹配、跨槽位选材和数量取整由 Saturn 执行。

系统币使用 Saturn 资源费用，例如：

```json
{
  "label": "系统币补给",
  "items": [{ "id": "minecraft:bread", "count": 6 }],
  "costs": [
    { "type": "saturn:resource", "resource": "example:system_coin", "amount": 20 }
  ]
}
```

资源需要先在 `data/example/saturn/resource/system_coin.json` 定义，例如 `{"default_value":0,"max":1000000000}`；示例包已附带该资源和 `system:coin_food` 商品，初始余额为 0，不会自动发币。资源注册表在世界加载时读取，添加或修改资源定义需重开世界 / 重启服务器，不能只 `/reload`。已有资源的商品费用可随商品正常重载。资源名称使用 Saturn 的翻译键格式，例如 `saturn.resource.example.system_coin`；客户端资源包未翻译时显示资源 ID。

`amount` 交由 Saturn `NumberProvider` 解析，支持其常量、公式和注册类型。浏览及生成报价均在服务端游戏线程求值，确认报价保留本次求值后的账单，两分钟内确认不会重新计算动态金额；确认仍会重新检查当前支付条件。客户端和模型只收到展示快照及报价引用，不能提供可执行 Cost。新增费用类型在 Saturn 注册，无需修改 System 的支付分支。

Saturn 逐项模拟后按列表顺序扣款，不合并重复或重叠的需求。因此同一种材料 / 资源尽量合并成一条 Cost，避免多个独立费用共享同一份余额导致部分扣款。若真实扣款失败或抛出异常，System 将订单标为 `PAYMENT_UNKNOWN` 并锁住奖励，不自动重试或退款，需管理员核对；不保证跨多个 Cost 的原子回滚。

标签为非空纯文本；无效物品 ID、不在原版范围内的数量、无法解析的组件、产出列表不在 1～36 项范围内或缺失价格都会拒绝本轮重载，不发布半份目录。依赖最终物品组件的校验留到运行时；商品产出无法创建时会在扣款前失败，奖励无法创建时保留为待领取。未知字段遵循原版 Codec 行为忽略。成功重载会更换目录版本，旧报价失效；正在推理的签到决策提交前需要校验版本。已入账的签到/商城奖励、订单和当日唯一标记均保留。

成就 `rewardId` 引用当前奖励池中的完整资源 ID，例如 `system:mining`。

## 抽奖奖池

奖池通过同一个目录重载监听器加载，奖池 ID 来自 `data/<namespace>/system/lottery_pools/<path>.json` 的路径。默认 `system:supplies` 消耗 1 个绿宝石，奖品分别为 16 个火把（60%）、8 个面包（30%）、4 个铁锭（10%）；可覆盖同路径文件或用 `{"enabled":false}` 禁用。可复制的示例在 [supplies.json](../examples/commerce-pack/data/example/system/lottery_pools/supplies.json)。

```json
{
  "label": "补给奖池",
  "costs": [
    { "type": "saturn:item", "items": ["minecraft:emerald"], "amount": 1 }
  ],
  "entries": [
    {
      "weight": 9,
      "reward": {
        "id": "example:lottery/food",
        "label": "食物补给",
        "items": [{ "id": "minecraft:bread", "count": 8 }]
      }
    },
    {
      "weight": 1,
      "reward": {
        "id": "example:lottery/diamond",
        "label": "钻石补给",
        "items": [{ "id": "minecraft:diamond", "count": 1 }]
      }
    }
  ]
}
```

`label` 非空，启用的奖池至少有一项 `entries`。`weight` 默认为 1，必须为正整数；单项概率等于该项权重除以奖池总权重，上例为 90% 与 10%。每次独立抽取一个完整奖励包，没有保底、多连抽或额外 AI 调用。`reward` 复用奖励 Codec：`id` 为 `Identifier`，`items` 为 1～36 项原版 `ItemStackTemplate`；奖品内联定义，不依赖签到奖励池。默认和示例均只引用必装的原版物品。

`costs` 为每次单抽的消耗，使用与商城相同的 Saturn `Cost.LIST_CODEC`，支持物品、资源和扩展费用。省略或设为 `[]` 表示免费，免费奖池可重复抽取。无法完成 Cost 定价的奖池可查看失败原因，不提供确认按钮；余额不足在确认时拒绝支付。旧的 `price` / `ITEMS` / `SYSTEM_COIN` 格式已移除，请按新格式重写，模组尚未发布，不提供旧数据迁移。

完整界面进入“系统抽奖”，或输入 `/lottery [关键词]`；`/draw <奖池ID>` / `单抽 <奖池ID>` 只预览，显示奖品概率和费用后，必须单独点击或用 `选择 编号` 确认。奖池与奖品均按每页 8 项展示。确认引用有效期两分钟，绑定玩家、操作类型及目录版本；成功重载会使旧确认失效。模型和本地语义路由只能浏览或预览，不能直接确认扣款、修改权重或指定中奖奖品。

服务端在确认时抽样并持久化中奖快照，共用商城的订单、扣款与收件箱结算流程；重复确认同一引用只返回原结果，不重复扣款或重抽。待支付与结果未知订单不展示未结算奖品。扣款前在游戏线程验证整个奖池的物品模板，任何奖品无法构建时均失败；资源重载阶段不会构建 `ItemStack`。已结算奖品保留快照，目录修改不会改写；仍需从收件箱领取。

## 成就规则

把 [成就规则示例](../examples/agent/system-advancements.json) 的 `rules` 填入 `config/system/system-advancements.json`。默认文件为 `{"rules":[]}`，示例不会自动启用。每条规则的字段为：

| 字段 | 内容 |
| --- | --- |
| `id` | 稳定规则 ID，普通调整不要换 ID 来重复发奖 |
| `advancementId` | Minecraft 成就 ID；只监听全部条件完成 |
| `moduleId` | 已注册且允许事件入口的模块 ID |
| `input` | 受信管理员配置的模块输入 |

`AdvancementRule.id` 和 `advancementId` 在 Java 中使用 `Identifier`，由原版 `Identifier.CODEC` 解析，JSON 仍写资源 ID 字符串。服务端直接用该类型查询和比较成就；持久化基线、事件任务与模块文本参数在边界转为字符串。

内置 `system:advancement_followup` 接受 `featureId` 与 `rewardId`，执行确保解锁标记存在、将候选目录中的固定奖励入账。适配器另外注入 `ruleId` 与 `advancementId`，不接受玩家伪造事件。

已有成就默认只建立基线，不追溯补发。规则/成就去重记录不随聊天删除，也不随重启清除。更改规则目标、移除规则或删除去重记录会改变业务语义，需要管理员审查；当前无自动历史补发和热重载。

## 策略覆盖

`system-agent.json` 中 `policies` 的键是模块 ID，值必须提供全部 `ExecutionPolicy` 字段。不同模块有自己的模型数、工具数、写步骤、tokens 与期限。`0` 模型次数表示禁止，不能写负数表示无限。未知模块配置会导致助手初始化失败并停止奖励写入。

配置错误不会回退到不校验的发奖方式。诊断在服务端日志中；修改配置文件需要正常重启，修改奖励、商品或奖池数据包可正常重载。FTB 自身任务配置仍是独立入口。
