# 注册表速查

所有正式注册表的键集中在 [`SystemResourceKeys`](../src/main/java/com/iafenvoy/system/registry/SystemResourceKeys.java)，实例集中在 [`SystemRegistries`](../src/main/java/com/iafenvoy/system/registry/SystemRegistries.java)。代码定义通过 NeoForge `NewRegistryEvent` 加入内置根注册表，通过 `DeferredRegister` 登记条目，由模组加载流程统一冻结。协议类型与卡片节点使用正式注册表；ASR / TTS 使用独立的普通工厂 Map，见下文。

注册表 ID 与公开键字段统一使用单数，数据包定义和 tag 目录沿用注册表路径，例如 `system:shop_offer` 对应 `system/shop_offer/` 与 `tags/system/shop_offer/`。

## 客户端模块绑定缓存

公开入口为 `module.client.ClientModuleCaches.BINDINGS` 和 `COMMAND`。`ClientModuleBindingsCache` 直接保存 Workspace 列表与本地卡片动作 Map；`ClientCommandCache` 直接保存命令声明和编译后的树。SystemClientMod 在客户端构造阶段调用 `ClientModuleCaches.init()`，提前创建缓存，不提前读取模块注册表。

绑定缓存统一协调两份数据的生命周期：收到 `TagsUpdatedEvent.ClientPacketReceived` 时按客户端注册表重建，客户端登出同时清空。这里不继承 Saturn 的共享静态 RegistryCache：其 shouldUpdateStaticData 过滤会跳过单人连接的客户端同步，服务端加载时提前构建的内容又可能被世界切换清空。客户端贡献只监听客户端事件，不能依赖服务端标签事件代为初始化。

首次读取优先使用当前连接的 registryAccess；无连接、也尚未收到标签同步时才装配本地常驻功能。标签包已提供注册表、但本地玩家尚未创建时保留该快照；连接的注册表实例变化会按需重建，避免空注册表缓存一直占用。每轮只遍历模块一次，命令树编译后释放临时节点 Builder，Workspace 和本地动作发布为不可变集合。缓存不再包装另一个 Bindings / Registry 对象；旧 ClientModuleBindings 和 ClientCommandRegistry 已删除。内置本地卡片动作在对应 ClientModule.register 中通过 localAction 声明，扩展也使用同一入口；CardController 只执行分派。玩家状态与界面 Document 不属于此缓存，资源包 JSON 继续由 CardTemplates 的资源重载监听器维护。

## System 自有内置注册表

下表字段对应 SystemResourceKeys 的键与 SystemRegistries 的实例。代码类型在两端创建，由 NewRegistryEvent / DeferredRegister 登记并冻结；除请求 / 响应两张协议表外不启用数字 ID 同步，按名称 Codec 编解码。

| 注册表 ID | 公开字段 | 条目类型 | 内置声明 |
| --- | --- | --- | --- |
| `system:server_module` | `SERVER_MODULE` | `MapCodec<? extends ServerModule>` | SystemServerModules |
| `system:card_data_type` | `CARD_DATA_TYPE` | `MapCodec<? extends CardData>` | SystemCardDataTypes |
| `system:action_type` | `ACTION_TYPE` | `MapCodec<? extends AgentAction>` | SystemActionTypes |
| `system:agent_request_type` | `AGENT_REQUEST_TYPE` | `StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>` | SystemRequestTypes |
| `system:agent_response_type` | `AGENT_RESPONSE_TYPE` | `StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>` | SystemResponseTypes |
| `system:card_node_type` | `CARD_NODE_TYPE` | `MapCodec<? extends CardNode>` | SystemCardNodeTypes |

共 6 张代码注册表。只有卡片节点的 DeferredRegister 在 SystemClientMod 绑定；专用服务端保留其空表。ClientModule 已移出 MC 注册表，SystemRegistries 与 SystemResourceKeys 不再引用该接口。

### 内置模块

服务端类型包括 core、binding、usage、player、items、economy、workshop、online_jobs、signin、inbox、shop、lottery、advancement、ftb_quests、web_search，命名空间均为 system。每类提供 CODEC / codec()，无参数类型使用 MapCodec.unit 单例；ServerModule.CODEC 从 SERVER_MODULE 按 type dispatch。

动态配置独立登记在 `system:system_module`，并通过 `SystemDataPackRegistries.modules(RegistryAccess)` 访问。内置动态条目采用同名 ID，扩展可给同一类型创建不同 ID 与参数；不能用静态 SERVER_MODULE 查询模块配置或取玩家可用条目。

模块定义的顶层 `order` 随注册表同步到客户端，默认 0，越大越靠前。ClientModuleBindingsCache 在装配后统一排序 Workspace；相同客户端实现的多份配置取最高值，同值保留原有顺序。内置玩家信息设为 100，优先显示。

顶层可选 `icon` 同样随注册表同步，类型为 Saturn `IconReference`，通过 `SystemModule.icon()` 读取；未设置或解析失败时为空。完整界面的左侧模块入口在名称前渲染该图标，纹理使用 Identifier 字符串，物品使用 ItemStackTemplate 对象。共享 client 入口按 order 降序取首个已配置图标。

客户端实现位于 SystemClientModules 普通 Map，通过客户端入口初始化、public static 字段公开。数据条目的 client 是该 Map 的 Identifier；服务端不会解析客户端实现。内置双端功能拆为相邻的 *SystemModule / *SystemClientModule，不再共用 ClientHooks 或同一个实现类。聊天、设置、语音与基础界面贡献在本地常驻，其余按动态配置 ID 检查权限。

| 接口 | 用途 |
| --- | --- |
| ToolModule | 服务端纯工具集合，无玩家主动请求入口 |
| WorkflowModule | 服务端工作流与生命周期，成就使用此类型 |
| SharedModule | 继承 WorkflowModule，增加服务端游戏指令；不继承 ClientModule |
| CoreModule | 继承 SharedModule，绑定后必需且不能移除 |
| CatalogModule | 继承 SharedModule，提供三类业务目录的注册表键与可选 ResourceKey/TagKey 列表 |
| ClientModule | 客户端界面、本地动作及 clientCommands |

分类使用接口，available() 仅检查依赖。纯工具和工作流都按配置 ID 保存 owner。Agent 工作流和工具目录支持同类型配置的多个候选，根据当前玩家已启用的模块选择实现；客户端缓存按 client ID 分组，仅装配一份 UI。详见[功能模块化](feature-modules.md)。

### 卡片数据与动作载荷

卡片类型共 35 个：`chat`、`text`、`selection`、`binding`、`shop_offer`、`shop_catalog`、`shop_quote`、`player`、`items`、`item_sources`、`item_inspection`、`repair_quote`、`online_jobs`、`wallet`、`delivery_state`、`reward`、`entitlement`、`purchase`、`quest`、`quest_guide`、`claim`、`sign_in_status`、`shop_order`、`lottery_catalog`、`lottery_pool`、`lottery_prize`、`quest_list`、`quest_unlocked`、`quest_notice`、`quest_startup`、`starter_gift`、`quest_submit`、`web_search`、`knowledge_answer`、`quest_pin`。`quest_unlocked` 使用 QuestUnlockedOutput 与现有 quest_list 模板，汇总同一批解锁任务；公共载荷不引用 FTB 类。状态与历史不再注册为卡片。

动作类型共 32 个：`free_chat`、`help`、`player_query`、`inbox`、`balance`、`sign_in`、`item_query`、`item_sources`、`item_inspect`、`repair_quote`、`repair_confirm`、`online_jobs`、`job_start`、`job_claim`、`job_cancel`、`deliver`、`shop`、`shop_quote`、`shop_purchase`、`lottery`、`lottery_preview`、`lottery_draw`、`quest_query`、`quest_guide`、`quest_detail`、`quest_claim`、`starter_gift`、`starter_gifts`、`quest_submit`、`advancement_followup`、`question`、`reasoning`。

内置条目均以 `public static DeferredHolder` 暴露。卡片实现 `CardData`，动作实现 `AgentAction`，都直接创建和传递数据对象；各实现的 `codec()` 返回自己的已注册 MapCodec：

```java
CardData card = snapshot;
AgentAction call = QuestDetailInput.byId(id);
```

`agent` 包的共用 DTO（`ClientState`、`History`、`SignInStatus`、`ShopOrder`、`QuestList`、`QuestNotice`、`QuestEvent`）各自一个文件，只保存数据，不承担类型注册。卡片实现提供 `MapCodec`、`codec()` 和 `buildCard()`，模板与展示内容由 CardBuilder 一次描述，根 `CardData.CODEC` 使用正式注册表的 `byNameCodec().dispatch("type", ...)`，数据字段平铺。`AgentAction.CODEC` 从 ACTION_TYPE 按 `moduleId` 分派动作自己的 MapCodec，参数直接平铺：

```jsonl
{"type":"system:player","name":"..."}
{"moduleId":"system:quest_detail","questId":"0123456789abcdef"}
```

动作类型已登记不代表可执行：`ModuleRegistry` 仍检查当前服务端是否安装对应工作流、MapCodec 实例是否一致，以及玩家功能权限。FTB 的 DTO/Codec 两端始终登记，实际 FTB 工具与工作流依赖安装检测。

`SystemCardDataTypes` 只负责卡片数据 Codec。`CardData.buildCard()` 返回 CardBuilder，build 后得到包含 template、参数、按钮和播报的 CardView。客户端 CardPresentation 解析 Component/物品名称并读取 assets 模板，不登记渲染映射、参数构建器或客户端卡片行为。数据 ID 与资源 ID 可以不同，见[客户端 JSON 卡片](cards.md#数据与渲染绑定)。

### 协议类型

| 注册表 | 内置条目 ID | 对应类型 |
| --- | --- | --- |
| `system:agent_request_type` | `system:chat`、`system:invoke`、`system:control`、`system:preferences`、`system:card_action` | `ChatRequest`、`InvokeRequest`、`ControlRequest`、`PreferencesRequest`、`CardActionRequest` |
| `system:agent_response_type` | `system:reply`、`system:notice`、`system:open`、`system:binding`、`system:state`、`system:history` | `ReplyResponse`、`NoticeResponse`、`OpenResponse`、`BindingResponse`、`StateResponse`、`HistoryResponse` |

这两张协议表登记的是 `StreamCodec`：根 `STREAM_CODEC` 用 `ByteBufCodecs.registry(...)` 读取同步的数字 ID，再由 `StreamCodec.dispatch` 直接调用条目自己的 `streamCodec()`，不经过 `Codec` / JSON，也没有按 `type` 字符串或 ID Map 分派。两张表因此启用数字 ID 同步（`sync(true)`），两端登记顺序一致；未知 ID 由流式解码阶段抛出。网络版本仍为 `1`。

请求与响应分别位于 `agent.request`、`agent.response` 的独立文件，各自定义 `STREAM_CODEC`，`SystemRequestTypes` / `SystemResponseTypes` 只引用并登记这些实例。`ControlRequest` 保留枚举形式，按序号编码；导航枚举为 `OpenResponse.View`，绑定变更枚举为 `BindingResponse.Change`，同样按序号编码。根接口只保留统一分派入口及公共契约。

任务读取不需要类型注册表：`ftb.query` / `ftb.find` 固定返回 `List<QuestBriefOutput>`，`ftb.detail` 固定返回 `QuestOutput`，新手礼包工具固定返回 `StarterGiftOutput`，各自直接使用对应 Codec。名称歧义选择和礼包展示由 `QuestDetailModule` 组合工具处理，详情见[FTB 适配与边界](development.md#ftb-适配与边界)。

Codec 在第一次编解码时才访问注册表，避免静态初始化时提前解析尚未绑定的 Holder。声明新的递归 Codec 时继续使用 `Codec.lazyInitialized`，不要在静态字段中提前调用其他 DeferredHolder 的 `.get()`。

### 卡片节点

SystemCardNodeTypes 声明 18 个 MapCodec：text、markdown、title；image、item、player、ftb_icon；flex、grid；container、groups、details；button、link；divider、progress；player_information、recipe。ID 均以 system: 为命名空间，按功能放入 screen.card.node 的对应子包。仅物理客户端绑定 DeferredRegister；专用服务端不加载 AUI 或节点实现。

CardNode 只提供 codec/css/render；文字投影、纯文本资格、标题、children 和集合索引分别由能力接口提供。递归 children Codec 使用 CardTemplate.NODES 延迟初始化。一个客户端内置注册表即可，无需按分类增加注册表。

CardData 与节点各自使用独立注册表。例如 SystemCardDataTypes.ITEM_SOURCES 仍是物品来源快照，客户端用 groups / grid / recipe 组合展示，不再注册业务列表节点。FtbIconNode 接受单个 FtbIconData，适配器仅在安装 FTB 的客户端反射加载。

button 直接声明 click/value：link 在本地打开 URL，return-val 发送带 source 的字符串，local 只调用可信 callback。服务端 CardButtonEvent 支持扩展字符串协议，未处理事件按内置 CardEventValue 规则进入 ModuleRouting.button；不新增按钮类型注册表。正文按钮与通知代码管理的确定、返回、底部操作分开。

任务 pin 通过 QuestCardBindings 的客户端构建事件扩展注入 callback 和状态绑定；DisplayCardCache 保存弱引用并在 pinRevision 变化时更新连接中的按钮。progress 保持独立通用节点。任务列表按现有查询结果分组，页码、分组与折叠统一保存在 CardUiState，不改变服务端数据包注册表。

JSON 示例：`{"type":"system:item","item":"{{result_items.0}}"}`。完整字段、能力接口、资源路径与示例见[客户端 JSON 卡片](cards.md)。

### 语音供应商 Map

| 工厂入口 | 内置键 | 扩展登记 | 创建实例 |
| --- | --- | --- | --- |
| [`TtsProviders`](../src/main/java/com/iafenvoy/system/speech/TtsProviders.java) | `aliyun` | `TtsProviders.register(id, factory)` | `TtsProviders.create(config.provider())` |
| [`AsrProviders`](../src/main/java/com/iafenvoy/system/speech/AsrProviders.java) | `aliyun-nls` | `AsrProviders.register(id, factory)` | `AsrProviders.create(config.provider())` |

内部的 `Map<String, Supplier<...>>` 使用 ConcurrentHashMap，由公开的 `register` 方法维护，不参与 MC 注册、冻结或同步，也不需要绑定 mod event bus。供应商按配置中的完整字符串精确查找工厂，每次调用返回新的实例，按原 session 生命周期关闭；不会自动补命名空间。默认键为 `aliyun` 和 `aliyun-nls`，扩展可自行使用 `example:voice` 等名称。收费表另将无命名空间的供应商名补为 `system:` 后匹配。未知供应商仍报告 `provider` 错误。

## 数据包注册表与外部注册表

| 来源 / 注册键 | 声明入口 | 查询 / 用途 |
| --- | --- | --- |
| System 模块数据包注册表 `system:system_module` | `SystemDataPackRegistries`、`SystemResourceKeys.SYSTEM_MODULE` | `modules/moduleIds(RegistryAccess)`；模块配置、server 类型分派与 client ID |
| System 数据包注册表 `system:system` | `SystemDataPackRegistries`、`SystemResourceKeys.SYSTEM` | `SystemDataPackRegistries.get/ids(RegistryAccess, ...)`；系统名、介绍与模块定义 |
| 签到奖励注册表 `system:signin_reward` | `SystemDataPackRegistries`、`SystemResourceKeys.SIGNIN_REWARD` | 服务端 SignInRewardEntry；模块 rewards 筛选 |
| 商城商品注册表 `system:shop_offer` | `SystemDataPackRegistries`、`SystemResourceKeys.SHOP_OFFER` | 服务端 ShopOfferEntry；模块 offers 筛选 |
| 抽奖奖池注册表 `system:lottery_pool` | `SystemDataPackRegistries`、`SystemResourceKeys.LOTTERY_POOL` | 服务端 LotteryPool；模块 pools 筛选 |
| 在线作业注册表 `system:online_job` | `SystemDataPackRegistries`、`SystemResourceKeys.ONLINE_JOB` | 服务端 OnlineJobEntry；模块 jobs 允许 ID/tag/混合数组，tag 路径 tags/system/online_job |
| 币种注册表 `system:currency` | `SystemDataPackRegistries`、`SystemResourceKeys.CURRENCY` | 同步 CurrencyDefinition 元数据；路径 system/currency，不同步玩家余额 |
| Saturn `SaturnResourceKeys.COST_TYPE` | `SystemCosts` | `system:currency`；从经济模块钱包扣款，复用 CostTransaction |
| Saturn `SaturnResourceKeys.ENTITY_ACTION_TYPE` | `SystemEntityActions` | `system:add_module`、`system:remove_module`、`system:add_currency` |
| Saturn `SaturnResourceKeys.ENTITY_CONDITION_TYPE` | `SystemEntityConditions` | `system:has_module`、`system:has_system`、`system:currency` |
| Saturn `SaturnRegistries.DATA_STORAGE_TYPE` | `SystemDataStorages` | 模块权限、成就、Agent 日志、交易、收件箱、签到、在线作业、钱包及常驻卡片的 DataStorage Codec；`ECONOMY` 对应 system:economy，`ONLINE_JOBS` 对应 system:online_jobs，`DISPLAY_CARDS` 对应 system:display_cards |
| 原版 `Registries.SOUND_EVENT` | `SystemSoundEvents` | `system:notification` |
| 客户端按键注册事件 | `SystemKeyMappings` | `CATEGORY`、`TOGGLE_INTERFACE`、`VOICE_INPUT`；使用 NeoForge 专用事件登记 |
| Saturn HUD 登记表 | `SystemHudEntries` | `NOTIFICATIONS` / `DISPLAY_CARDS`；布局键 `system.notification` / `system.cards`，物理客户端登记一次，共用 AuiHudEntry 坐标桥接和 Saturn 编辑器 / 布局存储 |

系统与模块定义通过 `RegistryAccess` 查询，在世界加载时读取、连接时同步；修改定义或参数需要重开世界或重启服务器，tag 仍遵循原版资源重载规则。不能用静态 `SystemRegistries` 查询世界数据包条目。系统文件路径为 `data/<条目命名空间>/system/system/<路径>.json`，格式见[数据包文档](datapacks.md)。

三张目录表仅服务端加载，不注册网络 Codec；路径为 data/<namespace>/system/signin_reward、shop_offer、lottery_pool，tag 分别位于 data/<namespace>/tags/system/<对应表名>/<path>.json。模块选择字段使用 Saturn RegistryCodecs.keyOrTagList / matchesKeys，支持 ID、#tag、混合数组，配置同步只编码资源键。内置三模块引用各表的 #system:default，tag 不跨表混用；具体语义与示例见[模块目录可选项](datapacks.md#模块目录可选项)。不再登记 CatalogReloadListener，修改条目需重新加载世界。

系统定义的 modules 可引用动态 SYSTEM_MODULE 条目或 tag，例如 "system:signin"、"#example:basic"、{ "id": "#example:basic", "condition": ... }。ModuleDefinition 的 Either 为 Holder<SystemModule> 或 TagKey<SystemModule>，由 Saturn RegistryCodecs.holderOrTag / matches 在当前世界注册表求值。模块 tag 路径为 data/<namespace>/tags/system/system_module/<path>.json；客户端状态同步实际启用的配置 ID。完整数据格式见[数据包](datapacks.md#系统模块定义)。

`SystemDataPackRegistries.ids(access)` 提供当前存档的系统 ID 列表，`get(access, id)` 查找指定定义；`/system grant <系统ID> [玩家]` 使用前者补全、后者校验。玩家保存自己的绑定 ID，模块求值和客户端展示均按该 ID 读取，不再提供固定默认选择的 `current(access)`。

## 扩展注册方式

### 新卡片或动作类型

扩展模组在两端加载的入口声明并绑定自己的 DeferredRegister：

```java
public static final DeferredRegister<MapCodec<? extends CardData>> CARDS =
        DeferredRegister.create(SystemResourceKeys.CARD_DATA_TYPE, "example");
public static final DeferredHolder<MapCodec<? extends CardData>, MapCodec<ExampleOutput>> EXAMPLE =
        CARDS.register("example", () -> ExampleOutput.CODEC);

// In the common mod constructor:
CARDS.register(bus);
```

`ExampleOutput` 实现 `CardData`，`codec()` 返回登记的 `ExampleOutput.CODEC` 实例，`buildCard()` 返回描述模板、参数和按钮的 CardBuilder；业务代码直接创建 `ExampleOutput`。动作类型实现 `AgentAction`，向 `SystemResourceKeys.ACTION_TYPE` 直接登记实现类的 MapCodec：

```java
public static final DeferredRegister<MapCodec<? extends AgentAction>> ACTIONS =
        DeferredRegister.create(SystemResourceKeys.ACTION_TYPE, "example");
public static final DeferredHolder<MapCodec<? extends AgentAction>, MapCodec<ExampleInput>> EXAMPLE_ACTION =
        ACTIONS.register("example", () -> ExampleInput.CODEC);

// In the common mod constructor:
ACTIONS.register(bus);
```

工作流 `inputCodec()` 与输入对象 `codec()` 必须返回同一个已注册实例；业务调用直接传 `new ExampleInput(...)`。`AbstractAgentModule` 从传入的 SchemaMapCodec 同时取得注册 ID 与输入 Schema，不再额外传类型包装。无参数动作也各自定义 Codec，内置使用 HelpInput、PlayerQueryInput、InboxInput、SignInInput、StarterGiftsInput；工具内部仍可共享未注册为动作的 EmptyInput。

### 新卡片节点

只在客户端入口绑定：

```java
public static final DeferredRegister<MapCodec<? extends CardNode>> NODES =
        DeferredRegister.create(SystemResourceKeys.CARD_NODE_TYPE, "example");
public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<BadgeNode>> BADGE =
        NODES.register("badge", () -> BadgeNode.CODEC);

// In a client-only mod constructor:
NODES.register(bus);
```

`BadgeNode.codec()` 返回 `BadgeNode.CODEC`，JSON 写 `"type":"example:badge"`。不要再在 `ClientModule.register(context)` 或资源重载时登记节点，届时内置注册表已经冻结。

### 新语音供应商

在客户端初始化时通过工厂入口登记名称和构造器引用，无需 DeferredRegister：

```java
TtsProviders.register("example:voice", ExampleTtsProvider::new);
AsrProviders.register("example:recognition", ExampleAsrProvider::new);
```

对应配置的 `provider` 填 `example:voice` 或 `example:recognition`。同名 `register` 会替换已有工厂，扩展应使用自己的命名空间，并在开始语音会话前完成配置。新增协议/鉴权字段、设置表单和计费单位仍由对应供应商适配，不因添加工厂而自动支持。

### 查询已有条目

```java
var type = SystemRegistries.CARD_DATA_TYPE.getValue(Identifier.parse("system:player")); // nullable
var optional = SystemRegistries.CARD_DATA_TYPE.getOptional(Identifier.parse("system:player"));
var id = SystemRegistries.CARD_DATA_TYPE.getKey(SystemCardDataTypes.PLAYER.get());
SystemRegistries.CARD_DATA_TYPE.forEach(entry -> { /* inspect registered types */ });
```

协议扩展也使用相同 DeferredRegister 模式，泛型改为对应 `MapCodec<? extends Root>`，根对象的 `codec()` 返回登记的实例。登记 Codec 只解决序列化；新请求/响应仍须在对应 Handler 提供行为，新卡片通过自身 `buildCard()` 描述内容，并提供 assets 模板。

## 仍保留的运行时登记表

`ModuleRegistry`、`ToolRegistry`、`CommandRegistry` 是每个 Agent 服务实例的工作流、工具和指令索引。客户端命令由 `ClientCommandCache` 装配，页面和本地动作由 `ClientModuleBindingsCache` 装配。ModuleRouting.button 提供按钮字符串参数解码，CardController 从绑定缓存分派本地动作；CardButtonEvent 处理服务端字符串。这些只负责运行行为，不承担 Codec 类型注册。ClientCards/ServerCards 保存连接内消息及版本，ASR/TTS 使用供应商工厂 Map。卡片布局直接取 CardView.template，不维护额外渲染或卡片行为登记表。

`ClientModuleCaches.COMMAND` 保存助手执行树、补全树与游戏客户端 `/system` 节点，注册、编译、访问和清理逻辑均在 `ClientCommandCache` 内。绑定缓存负责调用 begin / finish / clear，避免两个独立 Saturn 回调重复装配或依赖事件先后；get 确保整轮绑定装配完成，revision 在清理和重建时递增，界面以版本号拒绝旧异步补全。RegisterClientCommandsEvent 从此缓存复制节点，不再重新遍历模块。缓存只存声明和回调，权限、按钮与任务候选实时求值。MC 26.1.2 客户端配置完成时先应用标签并发布 TagsUpdatedEvent.ClientPacketReceived，随后登录/命令包合并触发客户端指令注册；服务端命令构造则早于 ServerDataLoad 标签事件，不能假设两端顺序相同。

`TypeDispatch` 是按 Java 类选择内部处理器的工具，资源模板 Map 是重载快照，玩家/session/缓存 Map 是业务状态。这些不属于 MC 的内置注册表，也不应随本次迁移冻结进全局注册表。

常驻卡片的数据源 Map 位于每个服务端的 `AssistantCardDisplays`，模块通过 `context.get(ContextTypes.DISPLAYS)` 登记 source ID、所属模块配置 ID 和只读 reader；不放入全局 MC 注册表。引用保存在核心 `DisplayCardsDataStorage`，刷新 / 登录 / 关闭由 ModuleManager 驱动；客户端 `DisplayCardCache` 直接持有镜像、版本及 pending 请求。该显示链路独立于 ClientCards/ServerCards 的通知授权与聊天历史。

ModuleContext 的 AgentEngine 与模块注册表为固定字段，以 engine() / modules() 读取；其他服务 Map 由 `ContextType<T>` 键关联类型化对象，内置键集中在 `module.ContextTypes`。该 Map 不是 MC 注册表、不序列化或跨存档共享；扩展可在游戏总线的 ModuleContextEvent 中自行声明键，通过 put 注入、get 读取，无需增加统一字段或类型注册。

## 搜索模块入口

服务端类型 Codec 为 SystemServerModules.SEARCH，客户端 Map 实现为 SystemClientModules.SEARCH；开发动态条目同时指定 server.type 和 client 为 system:web_search，但不再追加到默认模块 tag。搜索类型仅导出工具，不注册独立 AgentAction。核心动作 `SystemActionTypes.QUESTION_ACTION` 的 ID 为 `system:question`，对应 QuestionInput；本地答复卡片为 `SystemCardDataTypes.KNOWLEDGE_ANSWER` / `system:knowledge_answer`，联网答复继续使用 `WEB_SEARCH` / `system:web_search`。模板分别为 `knowledge_answer.json` 与 `web_search.json`。工具键 DATABASE_QUERY / QUEST_GUIDE / WEB_SEARCH / WEB_FETCH 对应 `database.query` / `ftb.guide` / `web.search` / `web.fetch`。

WebResearch 已迁入通用 ReActLoop，每次循环按工具上下文构建本地 MapCodec 表，以 `type=web.search|web.fetch|answer` dispatch，工具参数位于 input；不是 MC 注册表，也不另加全局 AgentAction。跨能力只读工作流另注册 `SystemActionTypes.REASONING_ACTION` / `system:reasoning`，工具清单从当前玩家启用模块中主动声明 forReasoning 的操作建立，观察结果同样按工具 Type 的 Codec dispatch。QuestionModule 的 ModuleRouting.explicit 解析当前明确联网句式；普通问题由模型路由选择 system:question，allowWeb 默认 false，只有非常确信当前授权外部查询时才为 true。问答语义样例仅作候选提示，不缓存授权；执行时固定先查库，本地为空且可选工具已启用时才开放联网门禁。`explicitOnly()` 与 `structuredOnly()` 仍保留为通用路由声明。详见 [ReAct 循环](agent-react.md)。

自由聊天动作 `system:free_chat` 使用 FreeChatInput，回复卡片 `system:chat` 使用 ChatOutput 与 chat.json；核心模块安装工作流，structuredOnly 将其从模型意图目录排除。完整自由模式从原工作流表及 ToolRegistry 装配可用能力，ToolProvider 可追加已注册的只读类型，目录按 LOCAL / WORKFLOW / PAID 排序；不是新的 MC 注册表。管理员 / 玩家权限、类型化观察及扩展接口见 [自由聊天](free-chat.md)。
