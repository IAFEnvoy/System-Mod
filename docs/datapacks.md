# 扩展配置

[表情与 HUD](emotions.md)使用同步数据包注册表 `system:emotion` 定义名称、语义和分类，客户端资源包独立映射相同 ID 的贴图 / 说话动画；AI 目录自动从注册表生成。文件位于 `data/<namespace>/system/emotion/<path>.json`，不使用图片编号作为表情协议。

[系统币经济](economy.md)新增 `system:currency` 原生数据包注册表，并提供多币种钱包、`system:currency` 费用/条件和 `system:add_currency` 实体动作；使用格式与字段见专页。

新增的本地玩法定义见[装备工坊](workshop.md)和[在线作业](online-jobs.md)。工坊在模块 server 内配置维修 Saturn Cost，并用 `salvage` 选择服务端 `system:salvage_rule` 注册表的 ID／tag；分解输入、条件、费用与动态产出见[装备分解](salvage.md)。作业使用独立原生数据包注册表与 tag，`jobs` 可选项支持 ID/tag/混合数组。二者均有同名内置模块，并加入默认模块 tag。[物品鉴定](item-inspection.md)属于已有 items 模块。

旧的 `SystemFunction` 执行机制已移除。执行预算、模型连接、本地路由和成就规则分别来自服务端 `config/system/` 中的 `system-agent.json`、`system-model.json`、`system-semantic.json` 和 `system-advancements.json`；系统名称与介绍使用下述新的系统定义注册表，签到奖励池、商城目录、抽奖奖池和模型提示词仍有独立的数据包入口。客户端不能提供价格、商品内容、抽奖权重或触发规则。

完整示例见 [执行预算](../examples/agent/system-agent.json)、[模型连接](../examples/agent/system-model.json)、[本地路由](../examples/agent/system-semantic.json) 和 [成就规则](../examples/agent/system-advancements.json)。首次初始化会从 JAR 内 `defaults/` 复制各默认文件，修改后正常重启。凭据类型、字段值及用途绑定统一保存在私有 `system-api.json`，不进入数据包，见[统一 API 管理](api-management.md)。默认 DeepSeek 与其他 OpenAI 兼容服务的配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

`system-semantic.json` 独立控制轻量本地意图路由。新配置默认 ENFORCE，不需要模型文件，说明见 [轻量本地意图路由](semantic-routing.md)。

## 系统定义

系统参数使用 NeoForge 同步数据包注册表 `system:system`，文件路径为 `data/<namespace>/system/system/<path>.json`，条目 ID 为 `<namespace>:<path>`。内置默认定义位于 `src/main/resources/data/system/system/system/default.json`，条目为 `system:default`；其 modules 只引用 `#system:default`，数据包可覆盖该 tag 或提供其他系统。数据包可按同一资源路径提供该定义，或新增其他 ID，通过 `/system grant <系统ID> [玩家]` 为玩家选定，例如 `/system grant example:daily Steve`。自己绑定使用 `/system grant example:daily`；系统 ID 从该存档注册表补全和校验，玩家不必共用同一个定义。

每名玩家当前最多绑定一个系统，重复授予同一个 ID 不重新初始化，授予另一 ID 被拒绝，须先 `/system revoke <玩家>`。绑定状态以不可变 `SystemBindings` 集合保存于玩家文件的 `systems` 字段，集合形式为后续多系统保留位置，当前 Codec 最多接受一项；客户端状态同步当前系统 ID。绑定授予在同一玩家事务内检查与写入，未知 ID 不写入存档；定义后来被移除时保留原 ID 并停止初始化，不自动切换到默认系统。

例如，创建一个“签到系统”定义：

```json
{
  "name": "example.system.sign_in.name",
  "description": "example.system.sign_in.description",
  "personality_prompt": "example:personality/sign_in",
  "modules": [
    "system:signin",
    {
      "id": "system:ftb_quests",
      "condition": { "type": "system:has_system" }
    }
  ]
}
```

`personality_prompt` 是可选的提示词 Identifier，使用 lenientOptionalFieldOf 读取；省略或字段无效时不附加人格。上例对应 `data/example/system/prompt/personality/sign_in.txt`，例如：

```text
You are the Host's daily supply system: calm, practical and attentive.
Address the player as 宿主 in Chinese or Host in English. Reply in {{player_language}}.
Keep advice concise and grounded in the available game data.
```

人格只追加到扩展模式的自由聊天兜底与完整自由模式，不影响意图路由、签到奖励选择、任务整理和联网整理等专用模板。正文沿用 TXT 重载和占位符机制，可使用自动注入的 `{{player_language}}`；引用文件不存在时记录警告并继续使用基础聊天提示词，不额外请求模型。内置默认系统引用 `system:personality/default`。更换 JSON 中的引用需要重开世界 / 重启服务器；修改已引用的 TXT 正文后 `/reload` 即可让新请求使用新版本。

`name` 和 `description` 必填，内容均为翻译键，客户端通过 `Component.translatable` 按玩家语言显示。自定义键需要配套客户端资源包，在 `assets/example/lang/zh_cn.json` 中提供：

```json
{
  "example.system.sign_in.name": "签到系统",
  "example.system.sign_in.description": "本系统旨在每日给宿主提供各类道具和资源。"
}
```

在 `assets/example/lang/en_us.json` 中提供相同的键，例如 `Daily Sign-in System` 与 `This system provides its host with items and resources every day.`。服务端数据包同步翻译键与模块 ID，不同步语言文件；仅在服务端添加语言文件无法让客户端获得翻译。也可以在默认 JSON 中保留内置键 `system.definition.default.name` / `system.definition.default.description`，通过资源包覆盖它们。

`modules` 控制每个已绑定玩家可用的功能，省略时为 `[]`。条目支持模块 ID（`"system:signin"`）、模块 tag（`"#example:basic"`），或带条件的对象 `{ "id": "#example:basic", "condition": ... }`；对象的 `id` 也可填单个模块 ID。省略 condition 表示无条件启用，condition 使用 Saturn 的 EntityCondition Codec，可写单个对象或条件数组（数组全部成立）。tag 中每个模块分别求值，同一模块被 ID 或多个 tag 引用时，任一匹配条目的条件成立即可启用。未列出的可选功能关闭；`core`、`binding`、`usage` 是基础设施，保留聊天、绑定管理与独立用量账本，不能通过模块开关关闭。客户端设置、记忆控制和语音输入仍属于本地功能。

### 系统模块定义

`system:system_module` 是新增的原生同步数据包注册表，文件路径为 `data/<namespace>/system/system_module/<path>.json`。系统的 modules、tag 和 condition 引用的是此处的配置实例；服务端实现类型依然由静态 `system:server_module` 注册表的 MapCodec 分派。

例如 `data/example/system/system_module/daily.json` 定义 `example:daily`：

```json
{
  "order": 80,
  "icon": { "id": "minecraft:book" },
  "server": { "type": "system:signin" },
  "client": "system:signin"
}
```

系统配置写 `"modules": ["example:daily"]`，即可引用此实例。server 对象按 type dispatch 解码，其余键交给具体模块 Codec；无参数类型使用 MapCodec.unit，带参数类型使用 RecordCodecBuilder.mapCodec，字段直接放在 type 同级，不改变系统引用格式。两份模块配置可以选择同一个实现类型，并由不同系统引用不同参数；工作流执行按当前玩家启用的配置选择。相同动作的多个候选同时启用时，按模块配置 ID 排序取第一个，建议同类变体条件互斥。

顶层 `order` 控制客户端模块入口的显示顺序，整数越大越靠前，允许负数；缺失或类型错误时按 0 处理。同值保留原有声明顺序，动态模块原本按 ID 装配；共用同一个 client 实现的多份配置合并为一个入口，取其中最大的 order。本地常驻入口优先读取同 ID 的模块定义，没有定义时为 0。order 不改变服务端工作流候选的选择规则。

顶层 `icon` 为可选的 Saturn `IconReference`：纹理写 Identifier 字符串，如 `"example:textures/gui/module.png"`；物品写 ItemStackTemplate 对象，如 `{"id":"minecraft:book"}`。裸字符串始终按纹理解析，不作为物品 ID。字段通过 `lenientOptionalFieldOf` 读取，缺失或格式错误时为空；随模块配置同步给客户端，显示在完整界面的左侧模块名称前。共享同一个 client 入口时按 order 降序取首个已配置图标，没有图标则只显示文字。内置侧栏模块已提供默认物品图标，可由数据包替换。

内置顺序为玩家信息 100、任务 90、签到 80、系统空间 70、商城 60、抽奖 50、物品查询与在线作业 40、传送锚点 35、成就总览 32；测试源集的联网搜索为 30。模块定义属于同步数据包注册表，调整 order 后需要重新进入世界或正常重启服务器。

server/client 都可省略：成就等纯服务端类型只填 server；仅需要客户端贡献的条目只填 client。client 只是客户端本地实现 Map 的 ID，服务端不加载客户端类。系统定义和模块定义均在世界加载时读取、登录时同步；修改参数需正常重启，不通过 /reload 热替换运行中的工作流。

签到、商城和抽奖的 server 还可分别设置 `rewards`、`offers`、`pools`，引用下文三张目录注册表。字段接受单个 ID、`#tag` 或混合数组，详见[模块目录可选项](#模块目录可选项)。签到另支持 `offline` 对象配置 Evaluate 分位数和价值区间，见[每日签到](sign-in.md)。

传送锚点的 `server.type` 为 `system:anchor`，支持 `limit`（Saturn NumberProvider，省略为 3）、`cooldown`（秒，省略为 10）、`cross_dimension`（省略为 true）。例如 `"limit": "min(12, 3 + floor(caster_level / 10))"` 随原版经验等级动态解锁数量；完整配置和容量下降规则见[传送锚点](teleport-anchors.md)。

内置的 core、binding、usage、player、items、signin、inbox、shop、lottery、advancement 配置位于 main；FTB 和联网搜索的默认配置仅位于 test-mod。发布包不直接引用 FTB 可选类型，未安装 FTB 的开发运行也会保留安全 Codec 并通过 available() 跳过装配。

### 模块 tag

tag 来自动态模块配置注册表 `system:system_module`，成员为模块配置实例，不是实现 Codec 或 Agent 工作流。文件路径为 `data/<namespace>/tags/system/system_module/<path>.json`。例如 `data/example/tags/system/system_module/basic.json` 定义 `#example:basic`：

```json
{
  "replace": false,
  "values": ["system:player", "system:items", "system:signin", "system:inbox"]
}
```

系统定义可以将 ID、tag 和带条件的 tag 混用：

```json
{
  "name": "system.definition.default.name",
  "description": "system.definition.default.description",
  "modules": [
    "#example:basic",
    "system:web_search",
    {
      "id": "#example:commerce",
      "condition": { "type": "system:has_module", "module": "system:signin" }
    }
  ]
}
```

上例的 `#example:commerce` 可另建 `data/example/tags/system/system_module/commerce.json`，其 `values` 为 `["system:shop", "system:lottery"]`。tag 合并、`replace`、嵌套 `#tag` 和 `{ "id": "...", "required": false }` 均使用原版 tag 加载规则。未知或空 tag 不启用任何额外模块，依赖缺席的模块仍被安装检查排除。手动移除优先于 tag，手动添加仍可覆盖条件，基础设施规则不变。

`ModuleDefinition` 复用 Saturn `RegistryCodecs.holderOrTag` 编解码符号引用，通过 `matches` 在权限求值时读取已加载的注册表 tag；不在系统定义解码阶段展开或固定 tag 成员。原版 tag 重载后的成员变化会在后续模块权限刷新时生效；系统定义本身仍按下文的注册表加载规则更新。

模块 ID 是当前世界 `system:system_module` 的数据包条目 ID，并非 Agent 工作流 ID 或实现类型 ID。ServerModule 不提供 id()，通过作用域 ModuleContext.id() 可取得当前配置 ID。客户端实现由独立的 SystemClientModules Map 管理。例如，内置配置沿用以下 ID：

| 功能 | 模块 ID |
| --- | --- |
| 玩家查询 | `system:player` |
| 物品查询与来源 | `system:items` |
| 签到 | `system:signin` |
| 收藏图鉴 | `system:collection` |
| 成就总览 | `system:achievement` |
| 系统空间与交付 | `system:inbox` |
| 私人传送锚点 | `system:anchor` |
| 商城（浏览、报价、购买） | `system:shop` |
| 抽奖（浏览、预览、抽取） | `system:lottery` |
| 联网搜索（需玩家明确要求） | `system:web_search` |
| 成就工作流 | `system:advancement` |
| FTB 任务（列表、详情、提交、领奖、新手礼包） | `system:ftb_quests` |

内置默认系统的 modules 仅引用 ["#system:default"]，默认 tag 位于 src/main/resources/data/system/tags/system/system_module/default.json。联网搜索的示例模块配置位于 src/test-mod/resources，不进入发布包，也不再追加到默认 tag；需由管理员明确配置或添加。FTB 类型的 Codec 始终注册，依赖缺席时由 available() 排除，不加载 FTB API；条件和手动覆盖无法启用不可用模块。

条件在玩家初始化完成后、交互请求前及每 20 个服务端 tick 在主线程重新求值。变化后同步玩家实际模块与指令列表，刷新完整侧栏，清理路由缓存与交易报价。帮助、补全、本地语义候选、模型能力目录和实际执行共用权限过滤；旧卡片的结构化动作以及已经排入游戏线程的工具也会重新检查权限。功能定义和工具仍在服务器启动时注册，开关不卸载 Java 类或重新注册工作流。

例如，让商城跟随签到模块是否启用：

```json
{
  "id": "system:shop",
  "condition": { "type": "system:has_module", "module": "system:signin" }
}
```

条件异常或循环依赖会关闭涉及的模块并记入服务端日志，避免递归溢出。手动添加可覆盖条件、手动移除可禁用定义中默认提供的模块，实现 `CoreModule` 的基础设施和未安装模块均不接受覆盖。纯服务端 `ToolModule` 同样使用这些模块开关，但只导出工作流内部使用的工具，不增加玩家指令或意图路由入口；调用时会检查该工具所属模块是否启用。

管理员可使用 `/system module add <玩家> <模块ID>`、`/system module remove <玩家> <模块ID>` 调整已绑定在线玩家的持久覆盖，例如 `/system module add Steve system:lottery`。`/system module list` 列出全部已注册模块（含依赖缺席的安全定义），`/system module list Steve` 列出该玩家实际启用的模块；新增模块初始化完成后才出现在玩家列表。玩家参数使用原版在线玩家补全，模块参数从当前世界 system_module 注册表补全且排除必需模块和 `available()` 为 false 的模块；即使当前条件已启用或禁用，也可以明确写入手动覆盖。

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

`has_system` 未提供 `system` 时匹配任意已绑定且就绪的系统，提供时与该玩家当前绑定的 ID 比较。实体不是服务端玩家、运行时未就绪或玩家未绑定时，action 不改数据，condition 返回 false。手动增删通过主线程 API `AssistantServer.get(server).moduleManager().add/remove(player, moduleId)`，移除立即停止模块；添加会异步初始化存储，成功后才发布可用状态，顺序动作中的后续 has_module 对刚添加模块可能暂为 false。权限解析内部的依赖条件使用目标模块集合，避免初始化顺序影响条件计算。可通过 `hasModule(uuid, id)`、`hasSystem(uuid)`、`system(uuid)`、`enabled(uuid)` 查询不可变状态。

模块覆盖保存在玩家独立数据文件 `<存档>/system/players/<玩家 UUID>.dat` 的 `moduleData` 字段中，通过 `FileAccess.NBT_COMPRESSED` 写入GZIP 压缩 NBT，由 `system:module_access` DataStorage 持有。数据按玩家 UUID 隔离，登出与重登保留，成功 `revoke/reset` 删除整份玩家数据，`forget` 不清除；累计用量仍保存在单独的世界 SavedData 文件中。

功能模块在 `ServerModule.storages()` 声明 ModuleStorage，Codec 通过 Saturn DATA_STORAGE_TYPE 独立注册，管理器统一创建数据和驱动生命周期；事务内使用 `ModuleData.get(data, SomeSystemModule.STORAGE)`。PlayerData.moduleData 使用 Saturn DataStorageHolder，以 ModuleStorage.owner 和 Java 类型寻址；内置同类配置共用对应功能账本，Codec 类型 ID 独立。商城和抽奖持有独立交易存储，Core 持有权限和幂等日志。普通业务修改在 AgentStore.update 原子落盘；DataStorage.tick 在主线程内存事务中执行，变化需 markDirty，由管理器合并异步保存。不使用玩家附件，停用保留账本，revoke/reset 才清空。

名称应用于助手标题、对话说话人和通知标题，介绍显示在欢迎区及首次绑定通知中；简约界面空闲时也展示介绍，绑定播报包含系统名称。定义从当前连接的原生注册表读取，不写入玩家附件或业务存档。系统、模块和三张目录注册表均在世界加载时读取，修改 JSON 后需要重开世界或重启服务器再连接；`/reload` 继续更新提示词和原生 tag，不热替换注册表条目。资源包的翻译文本可按客户端正常资源重载流程更新。

## 提示词模板

模型提示词使用 UTF-8 `.txt` 文件，支持 UTF-8 BOM，不经过 JSON 注册表。服务端 `PromptReloadListener` 在启动和 `/reload` 时扫描 `data/<namespace>/system/prompt/<path>.txt`，模板 ID 为 `<namespace>:<path>`。JAR 内自带以下默认模板；在数据包中放置同路径文件即可按数据包优先级整份覆盖，不必复制另一份未修改的模板：

| 数据包文件 | 模板 ID | 可用占位符 |
| --- | --- | --- |
| `data/system/system/prompt/routing.txt` | `system:routing` | `{{player_language}}`、`{{capabilities}}` |
| `data/system/system/prompt/web_search.txt` | `system:web_search` | `{{player_language}}` |
| `data/system/system/prompt/knowledge_answer.txt` | `system:knowledge_answer` | `{{player_language}}` |
| `data/system/system/prompt/sign_in.txt` | `system:sign_in` | `{{player_language}}` |
| `data/system/system/prompt/quest_narration.txt` | `system:quest_narration` | `{{player_language}}` |
| `data/system/system/prompt/quest_background.txt` | `system:quest_background` | `{{player_language}}` |
| `data/system/system/prompt/react.txt` | `system:react` | `{{player_language}}` |
| `data/system/system/prompt/tool_call.txt` | `system:tool_call` | 无；原生函数调用的共享规则，真实参数定义由请求 tools 字段传递 |
| `data/system/system/prompt/free_chat.txt` | `system:free_chat` | `{{player_language}}` |
| `data/system/system/prompt/personality/default.txt` | `system:personality/default` | `{{player_language}}` |

`{{player_language}}` 由执行上下文自动提供，例如 `zh_cn`、`en_us`，来源为 MC 同步的玩家语言，模块不能通过同名参数覆盖它。`{{capabilities}}` 由路由器传入当前已注册的交互模块 JSON，包含说明和输入 schema；路由模板必须保留这个占位符。签到的玩家快照、候选奖励和日期仍通过用户消息的结构化 JSON 传入，不内联成系统指令。

例如，签到模板中可以写 `Write one short reason in {{player_language}}.`。占位符名使用小写字母、数字或下划线，首位必须为字母；不支持表达式、脚本或递归替换。变量值中的 `{{...}}`、`$`、反斜杠均按原文保留，不执行第二次展开；普通 JSON 的单层或嵌套花括号可直接使用。`{{` 保留给占位符语法。内置模板使用未知占位符、语法错误、空文件、缺失必要模板或移除路由的 `{{capabilities}}` 都会拒绝重载。

模板数量和资源文件大小不设额外硬上限；实际模型请求仍受模块的输入和 Token 预算约束。成功安装服务器资源后，在下一服务端 tick 发布不可变模板快照；失败时继续使用之前的快照，不发布半份模板。已经开始执行的任务保持原版本，新任务读取新版本；精确意图缓存的键也包含版本，旧任务的模型决策不会被新版模板复用。移除覆盖文件后恢复 JAR 内默认模板，无需复制到 `config`。

扩展模块可添加其他命名空间或子目录，例如 `data/example/system/prompt/notice.txt`，然后使用 `context.prompt("example:notice", Map.of("event_name", name))` 渲染，再将结果传给原来的 `context.model(...)`。扩展占位符由模块提供，缺失变量在请求模型前报错；提示词文件本身不会自动注册模块或获得工具权限。预算、奖励候选校验、玩家隔离与支付确认仍由 Java 代码强制执行。

## 奖励目录

奖励、商品和奖池直接使用 NeoForge Data Pack Registry，由原版读取条目和 tag，不再使用自定义目录 reload listener。三张表仅在服务端加载，不向客户端同步完整目录与 Cost；客户端只接收业务结果和报价快照。条目 ID 来自注册表键，正文包含 `label`（翻译键字符串或 `Component`）和可选的 `items` / `contents` 列表；商品另有 Saturn `costs` 列表。产出物品直接使用原版 `ItemStackTemplate.CODEC`，包含注册 ID、可选的 `count`（默认 1，原版范围 1～99）及 `components`。`items` 与 `contents` 各为 0～36 项，启用的签到奖励和商品至少有一项产出。`contents` 复用空间内容类型，支持系统币、经验点 / 等级和 Saturn action，完整字段及示例见[奖励与非物品结果](rewards.md)。物品交付时模拟主背包的堆叠与空位。注册表加载时只读取模板，不构建 `ItemStack`；实际扣款前或交付时才调用 `create()`，由原版校验最终组件及产出的实际堆叠上限。支付定义使用 Saturn `Cost.LIST_CODEC`，数量由 `amount` 提供，不受产出模板的堆叠数量限制。

AI 签到先按模块可选项及条件筛选奖励，再按玩家与日期固定打乱，最多取 32 项作为当日候选。传给 AI 的只有请求内临时 ID、名称和简短内容摘要，不包含物品组件或可执行效果；实际候选数还受工作流的输入字节数及剩余总预算限制。AI 只返回其中一个临时 ID，服务端映射回原奖励，不能修改条目数量。生效离线模式使用 Evaluate 与玩家 P90 / P95 筛选同一数据包奖励池，无需模型；奖励根级可选 `offline_value` 为有限正数，表示整个包的离线价值，非物品或无法自动估值时需提供。配置、边界和阶段校准见[每日签到](sign-in.md)。两分支共用每日唯一记录并保存完整奖励快照，修改目录不会改写已获得权益。成就固定奖励不使用每日选择流程。

## 数据包目录

完整可复制的示例位于 [examples/commerce-pack](../examples/commerce-pack)。把该目录复制到服务器存档的 `datapacks/` 下，启用后重开世界或重启服务器，使原生注册表读取条目。示例同时向默认商城 / 抽奖 tag 追加新增条目，不会自动安装，也不进入发布 JAR。`pack.mcmeta` 格式根据当前依赖的 Minecraft 26.1.2 `version.json` 设置为 101.1。

| 注册表 | 文件路径 | tag 文件路径 |
| --- | --- | --- |
| `system:signin_reward` | `data/<namespace>/system/signin_reward/<path>.json` | `data/<namespace>/tags/system/signin_reward/<path>.json` |
| `system:shop_offer` | `data/<namespace>/system/shop_offer/<path>.json` | `data/<namespace>/tags/system/shop_offer/<path>.json` |
| `system:lottery_pool` | `data/<namespace>/system/lottery_pool/<path>.json` | `data/<namespace>/tags/system/lottery_pool/<path>.json` |
| `system:collection` | `data/<namespace>/system/collection/<path>.json` | `data/<namespace>/tags/system/collection/<path>.json` |
| `system:achievement` | `data/<namespace>/system/achievement/<path>.json` | 按可信事件计数，不使用 tag 筛选 |
| `system:teleport_destination` | `data/<namespace>/system/teleport_destination/<path>.json` | `data/<namespace>/tags/system/teleport_destination/<path>.json` |
| `system:online_job` | `data/<namespace>/system/online_job/<path>.json` | `data/<namespace>/tags/system/online_job/<path>.json` |
| `system:fact_reward` | `data/<namespace>/system/fact_reward/<path>.json` | 不使用 tag 筛选，按事件／来源／对象匹配 |

三个目录的所有有效资源按条目 ID 汇总：不同路径会新增条目，同路径按数据包优先级覆盖。JAR 内置奖励为 `system:food`、`system:mining`、`system:building`，商品为 `system:torch_bundle`，奖池为 `system:supplies`，默认内容和自定义内容使用同一加载流程。`{"enabled":false}` 可以覆盖并禁用单个条目；需要空目录时，禁用该类所有有效条目，或通过数据包过滤移除资源。移除覆盖文件会恢复下层数据包的同路径条目；若最终没有有效资源则保持空目录，不从配置补回。奖励包、商品、奖池及奖品总数和资源文件大小不设额外硬上限；每个奖励包内的两种产出列表分别使用上述 0～36 项限制，付款材料列表不设额外项数上限。

tag 使用原版 `values` / `replace`、嵌套 `#tag`、可选成员 `{ "id": "...", "required": false }`，以及 NeoForge 的 `remove`。三张表各有自己的 `#system:default`，即使 tag 名称相同，也不跨表匹配。禁用条目仍占用原生注册表 ID，但不会参与任何目录展示或发奖。若通过资源过滤移除条目，也要调整引用它的必需 tag 成员。

## 模块目录可选项

在 `data/<namespace>/system/system_module/<path>.json` 的 `server` 内指定该模块可使用的内容：

| 模块类型 | 字段 | 引用注册表 |
| --- | --- | --- |
| `system:signin` | `rewards` | `system:signin_reward` |
| `system:shop` | `offers` | `system:shop_offer` |
| `system:lottery` | `pools` | `system:lottery_pool` |
| `system:collection` | `collections` | `system:collection` |
| `system:online_jobs` | `jobs` | `system:online_job` |
| `system:anchor` | `destinations` | `system:teleport_destination` |

```json
{
  "server": {
    "type": "system:shop",
    "offers": ["system:torch_bundle", "#example:starter"]
  },
  "client": "system:shop"
}
```

单条可简写为 `"offers": "system:torch_bundle"` 或 `"offers": "#example:starter"`；签到和抽奖分别替换为 `rewards` / `pools`。省略字段表示允许该表全部有效条目，`[]` 表示不允许任何条目。未知 ID、空 / 未定义 tag 和已禁用条目不会回退为全表；重复 ID 或重叠 tag 只显示一次。条目按 ID 排序，同一玩家启用多个同类型模块时，合并各模块允许的集合；没有启用对应模块时集合为空。

例如上面的商城 tag 定义在 `data/example/tags/system/shop_offer/starter.json`：

```json
{
  "values": ["system:torch_bundle", "system:coin_food"]
}
```

固定 `system:shop` 模块还可配置可选的 `server.rotation`（周期、数量、个人／共享和盐），从 `offers` 中定期选品；省略则保留全池，见[商城周期轮换](shop-rotation.md)。`system:online_jobs` 的 `server.queue_size` 控制共用队列容量（默认 4，上限 32），见[在线作业与队列](online-jobs.md)。

内置 `system:signin` / `system:shop` / `system:lottery` 模块都显式引用对应表的 `#system:default`。新增条目后，可以向默认 tag 追加它，也可以自建模块与 tag，再在系统定义中启用这个模块。仅新增条目而不修改默认 tag，不会自动加入默认系统。

字段复用 Saturn `RegistryCodecs.keyOrTagList`，不在 Codec 解码阶段展开 tag；模块同步时只传资源键和 tag 名，不要求客户端加载服务端目录。服务端列表、签到候选与提交、签到状态、报价和最终扣款均检查玩家当前允许的集合；直接输入 `/buy` / `/draw` 的 ID 或点击旧报价也不能绕过限制。移除模块或 tag 成员后，在确认与扣款前重新检查来源；成就事件的固定奖励仍读取完整奖励注册表，不受签到候选范围限制。

修改目录条目或模块可选字段需重开世界 / 重启服务器；tag 成员按原版 `/reload` 更新，后续查询读取当前 tag，旧报价在来源不再可用时拒绝执行。已入账奖励和订单保留原快照。

## 条目内容

商品、奖池、签到奖励和在线作业都支持根级 `display_condition`（是否展示）与 `condition`（是否允许执行），均使用 Saturn EntityCondition，省略时成立。列表隐藏前者不满足的条目，后者不满足则保留预览并提示锁定；服务端在产生新权益前再次检查。字段表、示例与已付款奖励 / 已开始作业的保留规则见[条目条件与奖励效果](catalog-conditions.md)。效果通过奖励 `contents` 的 Saturn action 在领取时执行。

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
    { "type": "system:currency", "currency": "system:coin", "amount": 16 }
  ]
}
```

`costs` 通过 Saturn 的 Cost 类型注册表分派，可以混合物品、资源及其他已注册的费用；列表各项都需要支付。启用的商品必须提供非空 `costs`。`saturn:item.items` 中的匹配项是可替代关系，例如 `["minecraft:cobblestone", "#minecraft:planks"]` 表示总计消耗 `amount` 个圆石或木板，不是每种各扣一次。普通物品 ID 只匹配物品类型，可能消耗带名称、附魔或其他组件的同类物品；组件条件使用 Saturn 注册的 matcher。实际读取玩家 `Inventory` 暴露的槽位，匹配、跨槽位选材和数量取整由 Saturn 执行。

商品可选 `trade` 默认为 `purchase`。设置 `"trade":"buyback"` 即固定收购，`costs` 必须全部为 `saturn:item`；`items` / `contents` 作为收购报酬。列表展示收取物品，出售需单独确认，限额仍使用 `purchase_limit`，完整字段、示例和入口见[固定物品收购](shop-buyback.md)。

系统币使用经济模块的 `system:currency` 费用，例如：

```json
{
  "label": "系统币补给",
  "items": [{ "id": "minecraft:bread", "count": 6 }],
  "costs": [
    { "type": "system:currency", "currency": "system:coin", "amount": 20 }
  ]
}
```

内置 `system:coin` 已在 `data/system/system/currency/coin.json` 定义，支付从玩家的经济模块钱包扣款，不使用 Saturn Resource 余额。示例包的 `system:coin_food` 商品使用同一币种，不另建一份“系统币”资源。初始余额为 0，不会自动发币；管理员可用 `/system economy add <玩家> system:coin 100` 发币，数据驱动奖励可用 `system:add_currency`。自定义币种、名称翻译和权限要求见[经济系统](economy.md)。注册表及商品费用在世界加载时读取，添加或修改后需重开世界 / 重启服务器，不能只 `/reload`；其他 Saturn 资源仍可独立使用 `saturn:resource` 费用。

`amount` 交由 Saturn `NumberProvider` 解析，支持其常量、公式和注册类型。浏览及生成报价均在服务端游戏线程求值，确认报价保留本次求值后的账单，两分钟内确认不会重新计算动态金额；确认仍会重新检查当前支付条件。客户端和模型只收到展示快照及报价引用，不能提供可执行 Cost。新增费用类型在 Saturn 注册，无需修改 System 的支付分支。

Saturn 逐项模拟后按列表顺序扣款，不合并重复或重叠的需求。因此同一种材料 / 资源尽量合并成一条 Cost，避免多个独立费用共享同一份余额导致部分扣款。若真实扣款失败或抛出异常，System 将订单标为 `PAYMENT_UNKNOWN` 并锁住奖励，不自动重试或退款，需管理员核对；不保证跨多个 Cost 的原子回滚。

`label` 非空，字符串直接作为翻译键，例如 `"label": "example.shop.torch"`，由客户端按当前语言解析。币种的 `name` / `description`、在线作业的 `label` / `description`、签到和商品的 `label`、奖池及嵌套奖励的 `label` 统一使用 Saturn `MiscCodecs.TRANSLATABLE_COMPONENT`。需要显式字面量或样式时仍可写原版 Component 对象，例如 `{"text":"火把补给"}`；找不到翻译时，字符串显示原值。服务端的关键词搜索和路由摘要只取 `label.getString()`，未加载对应翻译时会得到键名。

无效物品 ID、不在原版范围内的数量、无法解析的组件、任一产出列表超过 36 项、签到 / 商品两种产出均为空或缺失价格都会由注册表 Codec 报错，拒绝加载该数据包注册表。依赖最终物品组件的校验留到运行时；商品产出无法创建时会在扣款前失败，奖励无法创建时保留为待领取。未知字段遵循原版 Codec 行为忽略。目录版本按服务端实例生成，旧会话报价不跨重启；正在推理的签到决策提交前仍校验版本及当前候选。已入账的签到/商城奖励、订单和当日唯一标记均保留。

成就 `rewardId` 引用当前奖励池中的完整资源 ID，例如 `system:mining`。

## 商品限购

在 `system:shop_offer` 商品中添加可选的 `purchase_limit`。省略该字段表示不限购，例如每天每位玩家最多购买 3 次：

```json
{
  "label": "每日口粮",
  "items": [{ "id": "minecraft:bread", "count": 6 }],
  "costs": [
    { "type": "system:currency", "currency": "system:coin", "amount": 20 }
  ],
  "purchase_limit": {
    "count": 3,
    "period": "daily"
  }
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `purchase_limit` | 对象 | 不限购 | 每位玩家独立，按商品注册表 ID 统计 |
| `purchase_limit.count` | 正整数 | 必填 | 一个周期内允许购买的次数，每次购买一个完整商品包 |
| `purchase_limit.period` | 字符串 | `lifetime` | `lifetime` 累计、`daily` 每日、`weekly` 每周、`monthly` 每月 |

`{"count":1}` 表示每位玩家累计只能购买一次。`daily` 在每天零点重置，`weekly` 在周一零点重置，`monthly` 在每月 1 日零点重置；均使用服务端 `system-agent.json` 的 `timeZone`（默认 `Asia/Shanghai`），不按游戏内日夜或购买后的 24 小时计算。按确认购买时预留订单的时间归属周期，跨周期的旧报价仍须通过新周期的校验。

额度直接从玩家商店模块的持久化订单账本统计，重新登录、重启、清除对话记忆、移除再添加模块都不会重置。同一商品被多个商店模块或 tag 引用时共用额度，不同商品 ID 独立；商品过去未限购时的订单也参与统计。更换商品 ID 会视为另一件商品。

商品货架的物品 tooltip 和商品详情、确认卡片展示限购次数及剩余次数。额度耗尽时仍可查看商品，但不会生成可确认的购买报价；确认旧报价时服务端会在持久化预留订单的同一事务内重新校验，多个报价或连续点击不能绕过限购。同一订单重复确认只返回原结果，不再次占用额度。

待支付、成功和付款结果未知的订单均占用其所属周期的额度；确定未扣款的失败订单不占用。结果未知时仍遵循原有锁单与人工核对规则，不因重启释放额度。奖励是否从系统空间领取不影响计数。卡片显示的是查询时的快照，购买后可刷新商城查看最新剩余次数。限购定义随商品在世界加载时读取，修改后需重开世界 / 重启服务器。活动订单账本达到 128 条时自动归档较早的已结清订单，保留最新 64 条可归档记录；归档后的累计次数和最近 35 天的下单时间继续参与限购统计。未领取、支付 / 领取未知和进行中订单保留；活动账本仍有 4096 条保护上限。管理员可查看、手动归档及核对未知支付，见[订单维护](commerce-orders.md)。

完整示例见 [coin_food.json](../examples/commerce-pack/data/system/system/shop_offer/coin_food.json)。开发测试源集中的钻石商品设为累计一次，火把商品设为每日三次，发布包内置商品默认不限购。

## 抽奖奖池

奖池使用原生 `system:lottery_pool` 数据包注册表，奖池 ID 来自 `data/<namespace>/system/lottery_pool/<path>.json` 的路径。默认 `system:supplies` 消耗 1 系统币，奖品分别为 16 个火把（60%）、8 个面包（30%）、4 个铁锭（10%）；可覆盖同路径文件或用 `{"enabled":false}` 禁用。可复制的示例在 [supplies.json](../examples/commerce-pack/data/example/system/lottery_pool/supplies.json)。

```json
{
  "label": "补给奖池",
  "costs": [
    { "type": "system:currency", "currency": "system:coin", "amount": 1 }
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

`label` 非空，字符串直接作为翻译键，也支持原版 `Component` 对象，奖池内每个 `reward.label` 同样如此；启用的奖池至少有一项 `entries`。`weight` 默认为 1，必须为正整数；单项概率等于该项权重除以奖池总权重，上例为 90% 与 10%。每抽选择一个完整奖励包，默认无保底、不限抽数，可配置硬保底、连抽档位、周期限额与冷却，不额外调用 AI。`reward` 复用奖励 Codec：`id` 为 `Identifier`，`items` 为可选的 0～36 项原版 `ItemStackTemplate`，`contents` 为可选的 0～36 项 SpaceStack（见[奖励结果](rewards.md)），两者都为空表示空奖；奖品内联定义，不依赖签到奖励池。默认和示例均只引用必装的原版物品。

`costs` 为每次单抽的消耗，使用与商城相同的 Saturn `Cost.LIST_CODEC`，支持物品、资源和扩展费用。省略或设为 `[]` 表示免费，免费奖池可重复抽取。无法完成 Cost 定价的奖池可查看失败原因，不提供确认按钮；余额不足在确认时拒绝支付。旧的 `price` / `ITEMS` / `SYSTEM_COIN` 格式已移除，请按新格式重写，模组尚未发布，不提供旧数据迁移。

完整界面进入“系统抽奖”，或输入 `/lottery [关键词]`；`/draw <奖池ID> [次数]` / `单抽 <奖池ID> [次数]` 只预览，显示奖品概率和费用后，必须单独点击或用 `选择 编号` 确认。浏览返回一张奖池货架卡片，每批最多 128 个奖池；点击奖池或使用 `/draw` 加载奖品货架，每批最多 64 个奖品。超出部分通过正文内独立的“上一批 / 下一批”加载并替换同一卡片，保留查询词；每批内部继续使用通用网格本地分页。范围文字显示当前批次位置，概率始终按整个奖池总权重计算。翻批不扣费、不抽奖、不调用模型。确认引用有效期两分钟，绑定玩家、操作类型及目录版本；同一玩家、类型、来源的新报价替换旧报价，旧确认卡需刷新；确认和扣款前仍检查当前模块及 tag 是否允许该奖池。模型和本地语义路由只能浏览或预览，不能直接确认扣款、修改权重或指定中奖奖品。

服务端在确认时抽样并持久化中奖快照，共用商城的订单、扣款与系统空间结算流程；重复确认同一引用只返回原结果，不重复扣款或重抽。待支付与结果未知订单不展示未结算奖品。扣款前在游戏线程验证整个奖池的物品模板，任何奖品无法构建时均失败；资源重载阶段不会构建 `ItemStack`。已结算奖品保留快照，目录修改不会改写；仍需从系统空间领取。已全部领取的订单可自动归档，历史确认和领取按钮仍返回原结果；未知支付通过管理员指令人工核对，见[订单维护](commerce-orders.md)。

抽奖新增字段 `draw_counts`（默认 `[1,10]`）、`pity`（阈值和目标奖励 ID）、`draw_limit`（次数与周期）及 `cooldown`（秒）。`entries[].max_wins` 为非负整数，默认 `0` 不限次，`1` 最多获得一次，`2` 最多两次；按每玩家／奖池／奖励 ID 累计，连抽逐次计数，达到上限后移出候选，剩余可抽取次数不足时扣费前拒绝。硬保底逐抽计算，连抽统一预览总费用和确认支付；次数按实际抽数预留。目录、详情与回执可打开个人记录，系统输入框也可使用 `/lotteryhistory [奖池ID]`。完整 JSON 字段表、示例与恢复语义见[抽奖规则](lottery.md)。

## 成就规则

把 [成就规则示例](../examples/agent/system-advancements.json) 的 `rules` 填入 `config/system/system-advancements.json`。默认文件为 `{"rules":[]}`，示例不会自动启用。每条规则的字段为：

| 字段 | 内容 |
| --- | --- |
| `id` | 稳定规则 ID，普通调整不要换 ID 来重复发奖 |
| `advancementId` | Minecraft 成就 ID；只监听全部条件完成 |
| `action` | 完整 AgentAction 对象，`moduleId` 指定已注册且允许事件入口的工作流，其余键为动作自身参数 |

`AdvancementRule.id` 和 `advancementId` 在 Java 中使用 `Identifier`，由原版 `Identifier.CODEC` 解析，JSON 仍写资源 ID 字符串。服务端直接用该类型查询和比较成就；持久化基线、事件任务与模块文本参数在边界转为字符串。

内置 `system:advancement_followup` 接受 `featureId` 与 `rewardId`，执行确保解锁标记存在、将候选目录中的固定奖励入账。适配器另外注入 `ruleId` 与 `advancementId`，不接受玩家伪造事件。完整调用单独嵌套到 action，避免动作参数与规则元数据重名，不保留旧 moduleId/input 结构的兼容解析：

```json
{
  "id": "system:first_diamond",
  "advancementId": "minecraft:story/mine_diamond",
  "action": {
    "moduleId": "system:advancement_followup",
    "featureId": "system:diamond_milestone",
    "rewardId": "system:mining"
  }
}
```

已有成就默认只建立基线，不追溯补发。规则/成就去重记录不随聊天删除，也不随重启清除。更改规则目标、移除规则或删除去重记录会改变业务语义，需要管理员审查；当前无自动历史补发和热重载。

## 策略覆盖

`system-agent.json` 中 `policies` 的键是模块 ID，值必须提供全部 `ExecutionPolicy` 字段。不同模块有自己的模型数、工具数、写步骤、tokens 与期限。`0` 模型次数表示禁止，不能写负数表示无限。未知模块配置会导致助手初始化失败并停止奖励写入。

配置错误不会回退到不校验的发奖方式。诊断在服务端日志中；修改配置文件或奖励、商品、奖池注册表条目需要正常重启，提示词和 tag 使用原版重载流程。FTB 自身任务配置仍是独立入口。

## 无限出售与无限收购

新增 `system:value_shop`、`system:value_buyback` 服务端模块类型，放入独立的 `system_module` 文件，客户端共用 `system:shop`。前者按玩家 P90 × `ceiling_multiplier` 解锁物品，后者不受 P90 限制；两者支持币种、汇率、价格倍率、物品准入与排除列表，以及按固定额度动态去重。库存仅在搜索时生成，参数表和完整示例见[无限交易](value-commerce.md)。

## 签到里程碑、收藏与事实奖励

`system:signin` 模块的可选 `rules` 配置固定 `daily_cycle` 及按累计／连续／周／月统计的里程碑，见[签到字段表](sign-in.md#连续累计与固定规则)。固定循环仍必须属于该模块可用的奖励目录，并满足奖励条件。

新增服务端数据包注册表 `system:collection` 定义系列、物品匹配谓词、图标和里程碑；`system:collection` 模块的 `collections` 按所选工作流配置筛选 ID／tag／混合数组，不与其他配置取并集，见[收藏字段表](collections.md)。

`system:fact_reward` 把可信事件映射成固定奖励，支持终生每对象、每事件、日、周、月频率。内置 `system:quest_completed` 规则给新完成的普通 FTBQ 任务生成 `system:quest_point` 积分权益，未安装 FTBQ 时不会产生事件，也不引用其可选模块类型。规则字段、持久化和恢复边界见[事实奖励](progress-rewards.md)。

## 累计成就、材料作业与收费传送

累计成就使用独立的 `system:achievement` 注册表，并复用核心可信事实和奖励账本；它与 `system:advancement` 的原版进度事件工作流不同。字段表、系列统计与历史语义见[成就总览](achievements.md)。

`system:online_job` 根级新增可选 `costs`，使用 Saturn 通用费用。开工先报价，玩家确认后支付，取消不退款；参数与异常核对见[在线作业](online-jobs.md)。

`system:anchor` 模块新增 `costs` 和 `destinations`，公共目的地由服务端 `system:teleport_destination` 定义；模块费用与目的地附加费用相加。字段表、tag 和安全规则见[传送锚点](teleport-anchors.md)。

购买／收购报价输入支持 `count`（1～100，默认 1），费用与产物按份数缩放，限购同样按份数结算，见[批量交易](shop-buyback.md#批量购买与出售)。本轮网络协议为 `3`，客户端与服务端需同步更新。
