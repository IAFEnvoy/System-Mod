# 功能模块化

公共接口与运行时位于 `com.iafenvoy.system.module`，客户端缓存位于 `module.client`。内置服务端定义和客户端实现分别为 `module.builtin.*SystemModule`、`*SystemClientModule`，存储类集中在 `module.builtin.storage`。具体 Agent 工作流位于 `module.builtin.<功能>`；Minecraft 单行为工具位于 `assistant.tool`，本地知识与搜索工具随工作流放置。`agent` 保留 AgentModule、AbstractAgentModule、ToolRegistry 等通用契约和执行机制。执行账本实现独立放在 `data.store`。

核心额外提供 `system:reasoning` 组合多个本地只读工具，普通操作继续走原有确定性工作流。扩展工具可通过 `.forReasoning(description)` 主动加入该能力；参数必须提供 SchemaMapCodec，写操作不能加入。通用 ReActLoop 与搜索共用，循环、工具清单和观察均为请求内独立上下文，预算仍归原 ExecutionContext。入口与 `reactIterations` 配置见 [通用 ReAct](agent-react.md)。

## 两层模块

模块实现类型与模块配置实例分开：

| 入口 | 内容 |
| --- | --- |
| `SystemRegistries.SERVER_MODULE` / `system:server_module` | 静态 `MapCodec<? extends ServerModule>` 类型注册表，由 `SystemServerModules` 注册 |
| `SystemResourceKeys.SYSTEM_MODULE` / `system:system_module` | 原生同步数据包注册表，条目是 `data.module.SystemModule`，按 `server.type` 解码服务端配置 |
| `SystemClientModules` | 仅客户端初始化的普通实现 Map；数据包的 `client` 只保存该表的 Identifier |
| `SystemResourceKeys.SYSTEM` / `system:system` | 系统名称、介绍与模块 ID/tag/condition；引用上述动态模块实例 |

例如 `data/example/system/system_module/daily.json`：

```json
{
  "order": 80,
  "icon": { "id": "minecraft:book" },
  "server": { "type": "system:signin" },
  "client": "system:signin"
}
```

此配置实例 ID 为 `example:daily`，服务端实现类型为 `system:signin`。系统的 `modules` 写 `"example:daily"`；`/system module add/remove/list`、玩家手动覆盖与 `has_module` 也使用配置实例 ID。模块 tag 路径为 `data/<namespace>/tags/system/system_module/<path>.json`。

顶层 `order` 是客户端入口的显示优先级，越高越靠前，默认 0；缺失或格式错误时采用默认值。ClientModuleBindingsCache 在发布 Workspace 快照前统一降序排列，同值保持原有顺序；共用 client 实现的配置取最高 order，本地常驻入口读取同 ID 的定义。内置玩家信息为 100，排在其他内置模块之前。此字段不影响模块权限、初始化依赖或服务端工作流候选选择。

顶层可选 `icon` 使用 Saturn `IconReference`，由 `SystemModule.icon()` 返回 `Optional<IconReference>`，缺失或解析失败时为空。字符串表示纹理 Identifier，例如 `"example:textures/gui/module.png"`；物品图标使用 ItemStackTemplate 对象，例如 `{"id":"minecraft:book"}`。字段随模块注册表同步，完整界面的左侧入口在名称前显示 20px 图标，复用 AUI 图片和原生物品渲染。多份配置合并为同一入口时，按 order 从高到低取第一个已配置的 icon；没有图标则保留文字入口。图标不单独接收鼠标事件，点击区域仍为整个按钮；右上角设置类入口保持文字。

`server` 和 `client` 均可省略：纯服务端模块只写 server，纯客户端贡献只写 client。client 始终是 ID，不在服务端解码 ClientModule、客户端 Codec 或 UI 类。专用服务端只加载服务端模块类；原有 ClientHooks 已拆成相邻的客户端实现类。

| 接口 | 职责 |
| --- | --- |
| `ServerModule` | 提供 `codec()`、依赖可用性、存储、运行时工厂和安装钩子 |
| `ToolModule` | 导出 `List<Tool>`，仅供 Agent 工作流内部调用，无玩家指令或意图入口 |
| `WorkflowModule` | 提供 `List<AgentModule<?>>`，包括成就等服务端事件工作流 |
| `SharedModule extends WorkflowModule` | 额外提供 `serverCommands(root)`；不再继承 ClientModule |
| `CoreModule extends SharedModule` | 基础设施；已安装的对应配置对绑定玩家始终启用且不可移除 |
| `CatalogModule<T> extends SharedModule` | 声明目录注册表与可选 ID/tag；签到、商城、抽奖合并玩家目录权限，在线作业使用所选工作流配置 |
| `ClientModule` | 仅客户端的页面、命令和本地卡片动作 |

类型由接口表达，`available()` 仅检查可选依赖。核心、绑定、用量实现 CoreModule，玩家、物品、签到、收件箱、商城、抽奖和任务实现 SharedModule，成就实现 WorkflowModule，搜索实现 ToolModule。聊天、设置、语音以及基础界面操作由客户端本地目录常驻，不依赖进入世界后的注册表同步。

## 服务端装配与指令

`ServerModule.CODEC` 通过静态类型注册表 `byNameCodec().dispatch("type", ServerModule::codec, codec -> codec)` 分派。无参数内置类型用 `MapCodec.unit(new ...())` 返回单例；签到、商城和抽奖是带 rewards / offers / pools 的不可变 record，使用 RecordCodecBuilder.mapCodec。动态注册表保存独立的 SystemModule 包装，不将同一单例重复注册为多个 MC 注册表值。

类型 Codec 的扩展示例：

```java
public static final DeferredRegister<MapCodec<? extends ServerModule>> TYPES =
        DeferredRegister.create(SystemResourceKeys.SERVER_MODULE, "example");
public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ExampleModule>> EXAMPLE =
        TYPES.register("example", () -> ExampleModule.CODEC);
```

扩展模组在公共入口绑定 TYPES。没有参数时使用 MapCodec.unit；需要参数时实现不可变 record，并用 RecordCodecBuilder.mapCodec 读取字段，codec() 返回同一个已注册实例。参数位于数据文件的 server 对象内，和 type 平铺；不增加第二套 JSON 手动解析。客户端也会解码同步的 server 数据，因此模块类及 Codec 必须不引用 net.minecraft.client 或可选模组实现类。

ModuleManager 从当前服务器 RegistryAccess 读取动态配置，筛选 available() 后先安装纯工具，再安装工作流，最后创建各实例的 PlayerModule。每份配置有独立 ModuleContext，提供当前条目 `id()`；通用基础依赖 AgentEngine 和模块注册表由构造器传入，以固定字段提供 `engine()` / `modules()`，不放入服务 Map。其他服务由 AssistantServer 创建，以 `put(ContextType<T>, T)` 注入，模块通过 `get(ContextType<T>)` 取得对应类型，无需强制转换。内置服务键位于 ContextTypes，包括 GAME、COMMERCE、CATALOGS、DATABASES、SEARCH、NARRATION 和 DISPLAYS。不要把服务器、玩家或运行时对象保存在全局单例定义里。

新增服务只需声明类型键并注入对象，不修改 ModuleContext 的字段、构造器或 getter。例如：

```java
public static final ContextType<MyService> SERVICE = new ContextType<>("example:service");

// Inject before installing modules; lookup returns MyService directly.
context.put(SERVICE, service);
MyService installed = context.get(SERVICE);
```

ContextType 按对象身份区分，名称仅用于诊断；注册和读取必须共用同一个键常量，同名的新键不会覆盖原键，也不会混淆泛型参数。put 不接受 null，get 遇到未注入服务会给出包含键名的错误。各配置上下文共用本次安装的服务 Map，id 和工具缓存仍各自独立；服务创建和关闭继续由 AssistantServer 或 ModuleManager 负责，Context 本身不接管生命周期。

内置服务注入后，ModuleManager 在服务端线程通过 `NeoForge.EVENT_BUS` 发布一次 `ModuleContextEvent`，之后才分发配置上下文、安装工具和工作流。扩展模组可在此同步添加自己的服务，随后所有配置上下文都能取得同一实例：

```java
@SubscribeEvent
public static void moduleContext(ModuleContextEvent event) {
    event.context().put(SERVICE, new MyService());
}
```

监听器注册到游戏事件总线，不是模组加载总线；固定字段通过 `event.context().engine()` / `modules()` 读取。该事件的上下文尚无配置 id，不能调用 id()，也不要异步延后注入或将服务保存到静态变量跨存档使用。需要生命周期管理的服务可实现 PlayerModule，并通过 sharedRuntime 创建；普通 put 不自动关闭对象。事件抛出异常会中止本次助手装配并沿用初始化清理路径。原 AssistantRegistrationEvent 保留在内置模块装配之后，用于直接向 AgentEngine 添加工作流和工具。

安装期间可使用 `context.sharedRuntime(type, factory)` 让同类配置共用 PlayerModule。该表只属于本次 ModuleContext，按运行时 Class 索引，不以服务器为键、不跨存档保留。ModuleManager 按对象身份去重执行 validate/tick/close，初始化中断时也会反序关闭已创建实例；`runtime(type)` 供可信服务端入口查找已安装的实例。每份配置的玩家 Session 仍独立创建与关闭，共享运行时应自行合并玩家级资源的引用。

不同配置可以提供相同动作 ID 的同类工作流。ModuleRegistry 按配置实例 ID 保存候选，命令/模型/语义目录只声明一次，执行时选择该玩家已启用的候选与对应工具。入口 Codec、工作流类型、版本、交互/事件属性、工具契约和只读权限必须一致；参数可影响执行和预算，不应让同一动作类型声明不同的路由契约。同一玩家同时启用多个候选时按模块实例 ID 排序选择第一个；数据包通常应让同类变体的条件互斥。流程选中后保留 owner，移除该配置会取消其在途和排队操作，不会中途换用另一份参数。

三类目录的可选项另由 CatalogModule 声明，按玩家启用的同类配置取并集，不仅限于执行工作流时选中的第一份配置。对应注册表为 system:signin_reward、system:shop_offer、system:lottery_pool；字段支持 ID、tag 或混合数组，复用 Saturn keyOrTagList，省略为全表、空数组为空，内置模块显式使用对应 #system:default。`assistant.commerce.CommerceCatalogs.available(player)` 是统一的玩家目录入口，通过 ModuleContext 注入工作流和交易服务，列表、模型候选、状态、报价与扣款共用筛选。CommerceCatalogs 保存当前世界注册表的 `Holder<SystemModule>`，从同一引用取得配置 ID 和 CatalogModule 实现，不再另外维护 ID → 实现的 Map；相同实现的不同配置仍按各自 ID 检查玩家权限。详见[目录选项](datapacks.md#模块目录可选项)。

在线作业同样实现 CatalogModule，但目录由所选工作流配置的 jobs 筛选，不进入交易目录的并集。它使用独立的 system:online_job 数据包注册表和共用的玩家作业账本，详见[在线作业](online-jobs.md)。

每个服务端实现类型的游戏指令只装配一次。SystemCommand 只创建 `/system` 根节点、收集 SharedModule.serverCommands，再连接 Agent 通用指令。绑定、用量和核心模块分别负责 grant/revoke/reset、usage 与打开界面/ask/routing/module 管理。补全和模块管理从当前世界的 system_module 注册表读取 ID。

### 纯工具模块

工具模块实现 ToolModule，提供 codec() 和 `tools(context)`，每个 Tool 只做一个行为，一组行为通过 List 组合。工作流在装配时用 `context.tool(type)` 获取已导出的工具，并列入自身工具白名单；执行仍统一走 ExecutionContext.tool。

ToolRegistry 保存按模块实例区分的处理器。工作流工具使用当前 owner 的实例；外部导出工具在调用时根据玩家启用的导出模块选择。预算、事件授权、写入回执与工具类型身份检查保留。工具模块不能同时实现 WorkflowModule；未启用的工具不能通过直接发请求绕过权限。

必需工具缺失会禁用工作流；Descriptor.optionalTools 则声明可选只读依赖，缺少对应功能不影响工作流的本地路径。执行时仍检查工具所属模块，不因为声明可选项就自动启用该模块。FTB 模块也可在安装时导出其说明书 Tool，供通用问答复用同一权限与检索处理器。

### 物品来源查询

`system:items` 装配三个工作流：`system:item_query`（名称/ID 检索）、`system:item_sources`（单个物品的来源）、`system:item_inspect`（主手实际物品鉴定，见[鉴定说明](item-inspection.md)）。来源查询提供 `/system recipe <物品ID> [范围]`、语义路由、稳定动作 ID 和物品检索卡片的点击操作：每个物品格子携带对应注册 ID，点击直接查看其获取方式，不需要玩家重抄；工具 ID 为 `game.item_sources`，输入是 `item_id` 加可选的 `scope`。`ItemSourceOutput` 同时是工具结果与已注册的 CardData，工作流通过 `AgentResult.data` 发送卡片，不再把序列化 JSON 拼进消息正文。以下仅为工具快照的编码示例：

```json
{"item":"minecraft:iron_ingot","scope":"loot","sources":[
  {"kind":"loot","id":"minecraft:entities/zombie","type":"loot_table",
   "outputs":[{"items":["minecraft:iron_ingot"],"count":1}],"notes":["weight 1"]}],"truncated":false}
```

物品检索卡片复用商城的 `grid`、`offer_cell_content` 和 `CardPagination`：图标与名称居中，注册 ID、堆叠上限放在原版物品 tooltip 的附加信息里，取消重复的文字清单。默认完整模式 6 列、最多 3 行，单页按内容收缩；本地翻页仅创建当前页 DOM。服务端仍分批读取最多 20 个匹配项，未读完时单独显示「更多物品」，继续使用原查询和偏移请求下一批，与本地上 / 下页区分。每项操作先于批次操作加入按钮表，保留原始索引；通知点击以 PUSH 展示来源并可返回，简约模式可按物品名称 / 编号选择；空结果显示本地化提示。

`scope` 缺省为 `all`，即完整查询，跑所有已注册来源；也可以只写某个来源的路径（`recipe`、`loot`、`tag`）或它的完整 ID（`system:recipe`）。范围由 `ItemSourceProviders` 在装配时枚举，新增 Provider 后自动出现在补全里；未注册的范围会被拒绝（`INVALID_INPUT`），不会静默返回空列表。命令行写作 `/system recipe minecraft:diamond loot`，省略第二个词即为完整查询；语义路由只在请求里出现「掉落 / 战利品 / 配方 / 合成 / 标签」这类关键词时收窄到对应范围，其余情况保持完整查询。语义路由优先取请求里的显式注册 ID（`CommandArguments.singleItemId`），没有 ID 时再用 `ItemNameIndex` 在本地显示名表里做最长匹配（「如何获取钻石镐」→ `minecraft:diamond_pickaxe`，更长的名字优先于更短的前缀词）；该表在服务器启动装配 `system:items` 时构建一次，名称取自服务端语言，专用服务端只用服务端资源，中文名可能匹配不到，此时退回模型路由。

默认值不会写进 JSON：`type` 缺省为空串，`station`、`inputs`、`notes` 缺省为空数组，`count` 缺省为 1，`scope` 缺省为 `all`，`width` 缺省为 0。槽位的 `items` 使用 ItemStackTemplate.CODEC（可以是 ID 简写，也可以带 components），数量单独保存在 count；候选是可替换项，不是需要一起消耗的材料。`width > 0` 表示有序合成，inputs 保留空格与重复材料，不再按旧的 16 槽上限截断。每槽最多 8 个候选示例，掉落最多检查 4096 张表，标签最多 64 个。汇总结果优先配方、其次掉落与标签，同类按 ID 排序，再截取前 64 条并设置 `truncated`。

客户端 `item_sources.json` 使用 `system:item_sources` 节点，正文先按「配方 / 掉落 / 标签」显示三个带数量的分类按钮，空分类置灰；默认选中第一个非空分类。配方默认每页 4 条、完整模式两列排列，简约 / 通知每页最多 2 条并单列显示；加工站以标题旁的紧凑图标表示。掉落默认每页 12 条，显示表 ID 与合并的条目备注；标签默认每页 24 条，用自动换行的紧凑标签排列。掉落 / 标签只显示一次分类说明，不逐项重复查询物品的图标。模板可用 recipe_page_size / loot_page_size / tag_page_size 调整容量，字段均为 lenientOptionalFieldOf。

上页 / 页码 / 下页独立于通知底部按钮，单页和首尾不可用方向置灰。节点复用 CardPagination，分类选择与各分类页码分别保存在当前卡片的 CardUiState，切换分类或翻页只重建当前页 DOM，不发包、不请求 AI 或重播语音。配方仍显示材料格 → 产物与数量；有序合成保留位置，原版大小补齐 3×3，物品悬停使用原版 tooltip，多个候选标注 +N 并在 tooltip 列出示例。未知 Provider 的 kind 归入配方分类并保留完整来源展示，未知模组配方保留类型 ID 和可读取的材料，不猜测机器条件。没有来源或被截断时使用本地化提示。

来源由 `ItemSourceProvider` 提供，`ItemSourceProviders` 按 ID 排序执行并逐个捕获异常，一个可选集成出错不影响其余来源。内置三个：

| Provider | `kind` | 内容 |
| --- | --- | --- |
| `system:recipe` | `recipe` | 读服务端 `RecipeManager`，按每个 RecipeDisplay 生成独立来源；识别有序 / 无序、炉类、锻造与切石展示，其他展示回退 placementInfo 材料。ItemStackTemplate 保留物品组件，type 仍为配方类型 ID，width 保留有序布局 |
| `system:loot` | `loot` | 把每张战利品表用 `LootTable.DIRECT_CODEC` 编码一次并扫描 item/tag 条目，索引按表实例放在 WeakHashMap 里，数据包重载后旧表被回收即自然失效 |
| `system:tag` | `tag` | 该物品所属的物品标签，一条一个标签；这是分类维度，不是制作步骤，所以只有 `outputs` |

其他模组可在构造阶段调用 `ItemSourceProviders.register(...)` 增加自己的来源，可以返回自定义 `kind`，新的 Provider 路径会同时成为合法的 `scope`。

三个内置来源都有覆盖边界，结果不会假装完整：`Recipe#display()` 默认返回空列表，只被 9 个原版配方类覆盖，因此修复物品、地图扩展、旗帜复制、烟花等 `CustomRecipe` 子类和未实现 `display()` 的模组配方查不到输入输出；酿造不是 `Recipe`，也不在 `RecipeManager` 里。战利品这边只解析静态条目，tag 会展开为物品（每表最多 8 个），嵌套 `minecraft:loot_table`、`minecraft:dynamic` 和 `minecraft:empty` 直接跳过，`set_count` 的随机数量不会折算进 `count`。标签来源只报归属关系，不说明该标签从哪获得。这些配方缺口需要 JEI 的类别投影才能补齐，而它只在客户端，服务端工具不依赖它。

## 玩家生命周期

ServerModule.runtime(context) 返回该模块配置的 PlayerModule，也可显式共享同一实例：

1. 全部工作流装配后执行 validate。
2. binding(player) 在主线程捕获首次绑定数据，返回在 PlayerData 事务内执行的回调。
3. 模块启用时创建玩家 Session，事务内创建声明的 DataStorage 并调用 initialize。
4. 初始化成功且连接与激活代次仍有效时，发布整批可用 ID，再调用 ready。
5. 只给已就绪玩家 Session 分发 tick；PlayerModule.tick 是该配置的服务器维护入口。
6. 条件失效、移除、退出、撤销或失败时关闭 Session；关闭服务器时反序关闭运行时。

纯客户端数据条目也参与权限和状态同步，但没有服务端工作流、存储或业务运行时。

ServerModule.storages() 返回 ModuleStorage 列表，包含 owner、存储类型 ID、Java 类型、MapCodec 和工厂。Codec 另行注册到 Saturn DATA_STORAGE_TYPE；业务在事务中调用 ModuleData.get(data, STORAGE)。存储仍按 ModuleStorage.owner 隔离：内置同类配置共用该功能的账本，不会因配置条目改名获得第二份每日签到或重复订单。扩展需要分开账本时应声明不同 owner。

管理器按声明的 storage.owner tick，并对相同 owner 去重，不能使用动态模块 ID 代替存储 owner。自动检测重写的存储 tick，状态改变时 markDirty；copy 深复制可变成员。逐 tick 操作用内存事务，不阻塞等待文件锁；脏数据每秒合并异步保存。revoke/reset 删除玩家业务文件，独立用量统计保留。

## 客户端贡献

客户端实现通过仅客户端入口注册：

```java
SystemClientModules.register(Identifier.parse("example:daily"), new ExampleClientModule());
```

数据包填 `"client": "example:daily"`，无需给公共 MC 注册表增加 ClientModule 类型。内置客户端实现集中在 registry.SystemClientModules 公开静态字段；专用服务端不会加载该类或 module.builtin.*ClientModule。未知 client ID 只缺少对应界面，不参与服务端业务分派。

ClientModuleCaches.BINDINGS 按同步的动态条目对 client ID 分组，每种 UI 装配一次。ClientModuleContext 保存关联的配置 ID 列表，任一 ID 在当前玩家同步状态中启用即允许操作。Workspace、文本命令、补全和本地动作复用该权限判断。常驻本地目录使用空关联列表，保持进入世界前的设置、关闭通知及基础界面操作可用。

绑定和命令数据均直接保存在各自 Cache 类。ClientModuleBindingsCache 监听客户端标签同步（含单人连接）重建、客户端退出清空，不再使用 Saturn RegistryCache 的共享静态数据过滤；服务端加载事件不构建客户端贡献。首次读取优先使用当前连接的注册表，无连接时才装配本地功能，并保留配置阶段已收到的注册表；连接注册表变化后按需重建。客户端游戏命令从 ClientCommandCache 复制，异步补全按 revision 丢弃旧结果。资源模板继续由自己的资源重载监听器维护。

完整模式始终显示聊天记录，不再提供“对话”入口或导航分组标题。模块声明 workspace、文案、按钮与回调，SETTINGS 类入口统一放到右上角，其他入口直接排列在左侧；界面绑定与显示只遍历缓存，不硬编码功能 switch。有 page 的入口再次点击时由 WorkspaceContext 统一收起工具栏，不再运行模块的打开回调；工具栏显示在记录下方、输入框上方。无 page 的即时查询与独立设置页面仍执行原动作。本地卡片动作从绑定缓存分派，服务端动作仍发送结构化请求。

## 数据包启用与条件

SystemDefinition.modules 的 Either 改为 `Holder<SystemModule> | TagKey<SystemModule>`，读取动态 system_module 注册表。引用格式、condition、tag 合并与手动增删规则不变；详见[数据包](datapacks.md#系统模块定义)。未知直接 ID 会报数据包错误，可选 tag 成员可用 required:false。

可选 FTB 定义的 Codec 保持公共安全，依赖缺席时 available() 为 false，跳过工作流、运行时与存储钩子。FTB 通知检查已启用模块的实现 Codec，因此任务模块改名或自定义条目 ID 不会丢失通知。FTB 适配器仍只在依赖存在时反射加载。搜索仅在测试源集提供模块配置，不再向默认 tag 追加；管理员需明确配置或手动添加。

FTB 适配器通过共享 FtbQuestRuntime 持有唯一的 FtbQuestAccess、知识索引和通知跟踪器。通知监听使用实例注册，随 validate 安装、close 注销，没有静态 SERVERS/ACCESS 表或进程级 installed 标记；客户端回声与其他服务器文件均被过滤。多个 FTB 定义共用一次通知维护，玩家 Session 引用全部释放后才清理查询及奖励引用；显式提交直接调用本实例的通知桥接，`/system ftb reload` 从 ModuleManager 查找同一知识索引。

system 和 system_module 都是原生数据包注册表，在世界加载时读取、登录时同步，不是 retained reload listener；新增或修改参数需正常重启服务器/重新进入世界。tag 的资源重载仍使用原版规则。没有旧模块注册表/tag 路径兼容层。

## 联网搜索模块

服务端类型为 system:web_search，仅导出 Search/Fetch 两个单行为 Tool；客户端实现 SearchSystemClientModule 可独立配对搜索页。开发动态条目保留 server.type / client，但不自动启用。核心 `system:question` 统一处理问题：先查私有 / 共享知识及可读 FTB 说明，命中则按资料回答；仅本地为空且当前请求允许联网时才调用已启用的搜索工具。搜索页提交同样先查库，普通提问不授权网络回退。详见 [问答与联网](search-api.md)。
