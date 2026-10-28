# 注册表速查

所有正式注册表的键集中在 [`SystemResourceKeys`](../src/main/java/com/iafenvoy/system/registry/SystemResourceKeys.java)，实例集中在 [`SystemRegistries`](../src/main/java/com/iafenvoy/system/registry/SystemRegistries.java)。代码定义通过 NeoForge `NewRegistryEvent` 加入内置根注册表，通过 `DeferredRegister` 登记条目，由模组加载流程统一冻结。协议类型与卡片节点使用正式注册表；ASR / TTS 使用独立的普通工厂 Map，见下文。

## System 自有内置注册表

下表字段名同时对应 `SystemResourceKeys` 的键与 `SystemRegistries` 的实例。所有注册表都在两端创建并统一登记到根注册表，声明不再按物理端拆分；客户端实现类的加载隔离放在条目装配入口。

| 注册表 ID | 公开字段 | 条目类型 | 内置条目声明 |
| --- | --- | --- | --- |
| `system:server_module` | `SERVER_MODULE` | `ServerModule` | [`SystemServerModules`](../src/main/java/com/iafenvoy/system/registry/SystemServerModules.java) |
| `system:card_data_type` | `CARD_DATA_TYPE` | `MapCodec<? extends CardData>` | [`SystemCardDataTypes`](../src/main/java/com/iafenvoy/system/registry/SystemCardDataTypes.java) |
| `system:action_type` | `ACTION_TYPE` | `MapCodec<? extends AgentAction>` | [`SystemActionTypes`](../src/main/java/com/iafenvoy/system/registry/SystemActionTypes.java) |
| `system:agent_request_type` | `AGENT_REQUEST_TYPE` | `MapCodec<? extends AgentRequest>` | [`SystemRequestTypes`](../src/main/java/com/iafenvoy/system/registry/SystemRequestTypes.java) |
| `system:agent_response_type` | `AGENT_RESPONSE_TYPE` | `MapCodec<? extends AgentResponse>` | [`SystemResponseTypes`](../src/main/java/com/iafenvoy/system/registry/SystemResponseTypes.java) |
| `system:client_module` | `CLIENT_MODULE` | `ClientModule` | [`SystemClientModules`](../src/main/java/com/iafenvoy/system/registry/SystemClientModules.java) |
| `system:card_node_type` | `CARD_NODE_TYPE` | `MapCodec<? extends CardNode>` | [`SystemCardNodeTypes`](../src/main/java/com/iafenvoy/system/registry/SystemCardNodeTypes.java) |

共 7 张代码注册表。共用条目（包括服务端生成回复所需的卡片载荷）的 DeferredRegister 由 `SystemMod` 绑定 mod event bus；客户端模块、布局节点的 DeferredRegister 由 `Dist.CLIENT` 的 `SystemClientMod` 绑定。专用服务端保留这两张表的空表，不加载客户端实现。公共声明只在泛型签名中使用客户端契约，不构造实现、不引用实现的 class literal 或静态 Codec。

当前 NeoForge 的 `RegistryBuilder.sync(true)` 同步条目的数字 ID 映射，默认关闭，不传输 Java 实现或 Codec。本模组这些代码注册表均按名称编码，保留默认设置，客户端独有条目不会参与数字 ID 同步。共用协议类型仍须由两端安装的代码提供相同定义；`system:system` 数据包注册表另用网络 Codec 同步定义，见下文。运行中不能新增内置注册表条目。

### 内置模块

- 服务端：`core`、`binding`、`usage`、`player`、`items`、`signin`、`inbox`、`shop`、`lottery`、`advancement`、`ftb_quests`、`search`。
- 客户端：`core`、`binding`、`conversation`、`settings`、`usage`、`speech`、`player`、`items`、`signin`、`inbox`、`shop`、`lottery`、`ftb_quests`、`search`。

以上条目均属于 `system` 命名空间，使用普通 `DeferredHolder` 始终登记。`ServerModule.available()` 在定义内部声明依赖是否满足；任务模块检测 FTB Quests，缺席时保留模块 ID 与公共 Codec，跳过指令、工作流和运行时。客户端任务模块只使用本模组 DTO，仍可保留卡片展示定义。模块装配、玩家启停及生命周期见[功能模块化](feature-modules.md)。

### 卡片数据与动作载荷

卡片类型共 23 个：`text`、`selection`、`binding`、`shop_offer`、`shop_quote`、`player`、`items`、`delivery_state`、`reward`、`entitlement`、`purchase`、`quest`、`claim`、`sign_in_status`、`shop_order`、`lottery_pool`、`lottery_prize`、`quest_list`、`quest_notice`、`quest_startup`、`starter_gift`、`quest_submit`、`web_search`。状态与历史不再注册为卡片。

动作类型共 20 个：`help`、`player_query`、`inbox`、`sign_in`、`item_query`、`deliver`、`shop`、`shop_quote`、`shop_purchase`、`lottery`、`lottery_preview`、`lottery_draw`、`quest_query`、`quest_detail`、`quest_claim`、`starter_gift`、`starter_gifts`、`quest_submit`、`advancement_followup`、`web_search`。

内置条目均以 `public static DeferredHolder` 暴露。卡片实现 `CardData`，动作实现 `AgentAction`，都直接创建和传递数据对象；各实现的 `codec()` 返回自己的已注册 MapCodec：

```java
CardData card = snapshot;
AgentAction call = QuestDetailInput.byId(id);
```

`ResultTypes` 只保留共用 DTO，不再承担类型注册。卡片实现提供 `MapCodec`、`codec()` 和 `buildCard()`，模板与展示内容由 CardBuilder 一次描述，根 `CardData.CODEC` 使用正式注册表的 `byNameCodec().dispatch("type", ...)`，数据字段平铺。`AgentAction.CODEC` 从 ACTION_TYPE 按 `moduleId` 分派动作自己的 MapCodec，参数直接平铺：

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

这些根接口通过 `codec()` 返回已注册的 MapCodec 实例；根 `CODEC` 从对应注册表的 `byNameCodec()` 按 `type` 分派。没有额外的 `DispatchRegistry.Type` 包装或 ID Map。未知 ID、未登记的 Codec 由原版注册表 Codec 拒绝，网络版本仍为 `1`。

请求与响应分别位于 `agent.request`、`agent.response` 的独立文件，各自定义 `CODEC`，`SystemRequestTypes` / `SystemResponseTypes` 只引用并登记这些实例。`ControlRequest` 保留枚举形式，其 MapCodec 编解码 `action` 字段；导航枚举为 `OpenResponse.View`，绑定变更枚举为 `BindingResponse.Change`。根接口只保留统一分派入口及公共契约。

任务读取不需要类型注册表：`ftb.query` / `ftb.find` 固定返回 `List<QuestBriefOutput>`，`ftb.detail` 固定返回 `QuestOutput`，新手礼包工具固定返回 `StarterGiftOutput`，各自直接使用对应 Codec。名称歧义选择和礼包展示由 `QuestDetailModule` 组合工具处理，详情见[FTB 适配与边界](development.md#ftb-适配与边界)。

Codec 在第一次编解码时才访问注册表，避免静态初始化时提前解析尚未绑定的 Holder。声明新的递归 Codec 时继续使用 `Codec.lazyInitialized`，不要在静态字段中提前调用其他 DeferredHolder 的 `.get()`。

### 卡片节点

内置节点：`system:text`、`system:header`、`system:context`、`system:container`、`system:row`、`system:column`、`system:divider`、`system:items`、`system:actions`、`system:button`。

JSON 使用完整 ID，例如 `{"type":"system:items","items":"{{result_items}}"}`。节点自身实现 `codec/css/render/text`，`CardNode.CODEC` 通过正式注册表分派。资源包能调整布局与参数，不能新增 Java 节点类型。格式见[客户端 JSON 卡片](cards.md)。

### 语音供应商 Map

| 工厂入口 | 内置键 | 扩展登记 | 创建实例 |
| --- | --- | --- | --- |
| [`TtsProviders`](../src/main/java/com/iafenvoy/system/speech/TtsProviders.java) | `aliyun` | `TtsProviders.register(id, factory)` | `TtsProviders.create(config.provider())` |
| [`AsrProviders`](../src/main/java/com/iafenvoy/system/speech/AsrProviders.java) | `aliyun-nls` | `AsrProviders.register(id, factory)` | `AsrProviders.create(config.provider())` |

内部的 `Map<String, Supplier<...>>` 使用 ConcurrentHashMap，由公开的 `register` 方法维护，不参与 MC 注册、冻结或同步，也不需要绑定 mod event bus。供应商按配置中的完整字符串精确查找工厂，每次调用返回新的实例，按原 session 生命周期关闭；不会自动补命名空间。默认键为 `aliyun` 和 `aliyun-nls`，扩展可自行使用 `example:voice` 等名称。收费表另将无命名空间的供应商名补为 `system:` 后匹配。未知供应商仍报告 `provider` 错误。

## 数据包注册表与外部注册表

| 来源 / 注册键 | 声明入口 | 查询 / 用途 |
| --- | --- | --- |
| System 数据包注册表 `system:system` | `SystemDataPackRegistries`、`SystemResourceKeys.SYSTEM` | `SystemDataPackRegistries.get/current(RegistryAccess, ...)`；系统名、介绍与模块定义 |
| Saturn `SaturnResourceKeys.ENTITY_ACTION_TYPE` | `SystemEntityActions` | `system:add_module`、`system:remove_module` |
| Saturn `SaturnResourceKeys.ENTITY_CONDITION_TYPE` | `SystemEntityConditions` | `system:has_module`、`system:has_system` |
| Saturn `SaturnRegistries.DATA_STORAGE_TYPE` | `SystemDataStorages` | 模块权限、成就、Agent 日志、交易、收件箱、签到的 DataStorage Codec |
| 原版 `Registries.SOUND_EVENT` | `SystemSoundEvents` | `system:notification` |
| 客户端按键注册事件 | `SystemKeyMappings` | `CATEGORY`、`TOGGLE_INTERFACE`、`VOICE_INPUT`；使用 NeoForge 专用事件登记 |

系统定义通过 `RegistryAccess` 查询，随服务端资源重载和同步更新；不能用静态 `SystemRegistries` 查询世界数据包条目。文件路径为 `data/<条目命名空间>/system/system/<路径>.json`，格式见[数据包文档](datapacks.md)。

系统定义的 `modules` 可引用 `SERVER_MODULE` 条目或 tag，例如 `"system:signin"`、`"#example:basic"`、`{ "id": "#example:basic", "condition": ... }`。模块 tag 文件位于 `data/<namespace>/tags/system/server_module/<path>.json`；复用 Saturn `RegistryCodecs.keyOrTag` / `matchesKeys`，解码保留 ID/tag，服务端权限求值时读取成员。客户端继续接收实际启用的模块 ID 列表，不自行展开 tag。格式与覆盖规则见[模块 tag](datapacks.md#模块-tag)。

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

工作流 `inputCodec()` 与输入对象 `codec()` 必须返回同一个已注册实例；业务调用直接传 `new ExampleInput(...)`。`BuiltinModule` 从传入的 SchemaMapCodec 同时取得注册 ID 与输入 Schema，不再额外传类型包装。无参数动作也各自定义 Codec，内置使用 HelpInput、PlayerQueryInput、InboxInput、SignInInput、StarterGiftsInput；工具内部仍可共享未注册为动作的 EmptyInput。

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

`ModuleRegistry`、`ToolRegistry`、`CommandRegistry` 是每个 Agent 服务实例的工作流、工具和指令索引；`ClientCommandRegistry`、`ClientModuleBindings` 装配本地命令与页面。ModuleRouting.button 提供按钮字符串参数解码，CardController 的 Local Map 提供本地动作处理器。这些只负责运行行为，不承担 Codec 类型注册。ClientCards/ServerCards 保存连接内消息及版本，ASR/TTS 使用供应商工厂 Map。卡片布局直接取 CardView.template，不维护额外渲染或卡片行为登记表。

`TypeDispatch` 是按 Java 类选择内部处理器的工具，资源模板 Map 是重载快照，玩家/session/缓存 Map 是业务状态。这些不属于 MC 的内置注册表，也不应随本次迁移冻结进全局注册表。

## 搜索模块入口

服务端 `SystemServerModules.SEARCH` 和客户端 `SystemClientModules.SEARCH` 注册 ID 均为 `system:search`。动作 `SystemActionTypes.SEARCH_ACTION` 与卡片 `SystemCardDataTypes.WEB_SEARCH` 的 ID 均为 `system:web_search`，分别对应 WebSearchInput / WebSearchOutput；模板为 `assets/system/cards/web_search.json`。工具键 `BuiltinTools.WEB_SEARCH` / `WEB_FETCH` 对应 `web.search` / `web.fetch`，只执行一种操作。

搜索内部 SearchStep 以本地 Map 存三个 MapCodec，按 `type=search|fetch|answer` dispatch；它是单个工作流的模型决策协议，不是 MC 注册表或全局 AgentAction。ModuleRouting.explicit 声明保守的当前消息解析器，ModuleRegistry 统一解析并校验；该工作流 explicitOnly，不进入自动模型目录。
