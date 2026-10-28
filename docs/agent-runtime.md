# Agent 运行结构与扩展

Agent 代码统一在 `com.iafenvoy.system.agent`。新增能力声明参数类型、单行为 Tool、组合流程的 `AgentModule`，以及工作流自己的入口信息；功能的服务端装配、平台指令、生命周期与客户端贡献由外层 `ServerModule` / `ClientModule` 统一组织，见[功能模块化](feature-modules.md)。指令解析器和路由器不包含模块 ID 的业务 switch。

根包集中引擎、执行器、上下文、Module/Tool 契约、注册表、指令与请求结果。路由、模型、存储、商城和提示词分别归入 `routing`、`model`、`store`、`commerce`、`prompt`，各包同时包含自身的接口、数据与实现。内置工作流与工具位于 `builtin/<功能>`；共享类型键、输入及输出分别保留在 `builtin.tool`、`builtin.input`、`builtin.output`。共用 Codec、Schema 和 JSON 工具直接位于外层 `com.iafenvoy.system.util`，配置位于 `com.iafenvoy.system.config`。

执行配置、模型连接、语义路由与成就绑定分别由 `AgentSettings`、`ModelSettings`、`SemanticSettings`、`AdvancementSettings` 管理，各配置类保存 `ConfigInstance<T> INSTANCE`。容器统一复制缺失的默认文件并解析，读取使用 `INSTANCE.get()`，提交使用 `INSTANCE.set(next)` 并自动 `markDirty()`，由 tick 或关闭事件保存。资源重载会更新容器；引擎、模型客户端与 MC 事件桥接仍持有启动时快照，完整应用其变化需要重启。引擎接收执行配置、语义配置和已经过数据包校验的 `CommerceCatalog`；模型客户端只接收 `ModelSettings`，不再通过 `AgentSettings` 访问业务定义。

## 一次注册，多种入口

`ModuleRegistry` 是当前服务器能力的来源。`engine.registerModule(module)` 同时处理：

1. 核对已共享注册的 `TypedPayload.Type<I>`、模块 Identifier 与工具白名单。
2. 校验所有指令别名和工具实例冲突，然后注册 Module 的工具 List。
3. 绑定指令参数解析器、语义样例和参数提取器，不再重复填写模块 ID。
4. 记录模型可见性和只读快捷入口，供模型目录、澄清提示与帮助使用。

`engine.start()` 校验策略和事件引用，冻结注册表并建立语义索引。启动后禁止继续注册工具、模块或 profile。未安装的可选模块不注册，因此不会出现在指令、帮助、补全、语义可执行候选或模型目录中。

普通模块先通过 DeferredRegister 在 `SystemResourceKeys.ACTION_TYPE` 注册类型键；内置键由 `SystemActionTypes` 公开，注册后通过 `.get()` 访问。按钮、模型和 Module 复用同一键。类型与 Codec 必须在两端可用，定义不得引用未安装的可选模组类，详见[注册表速查](registries.md)。

## 职责边界

| 类型 | 负责 |
| --- | --- |
| `AgentEngine` | 服务与玩家生命周期、公开入口、统一模块执行 |
| `InteractiveExecutor` | 交互队列、玩家独占、取消、指纹去重与历史写回 |
| `EventExecutor` | 持久事件、规则授权、状态变更、恢复和事件去重 |
| `ConversationService` | 清空历史、偏好与有界历史读取 |
| `TaskExecutor` | 共用有界线程池、拒绝、关闭和 Future 完成机制 |
| `ModuleRegistry` / `CommandRegistry` | 能力注册与由声明生成的入口、指令、帮助和补全 |
| `HybridRouter` | 选择模块，不执行业务流程 |
| `SemanticRouter` / `SemanticIndex` | 只读本地匹配、置信门槛、玩家隔离的意图缓存 |
| `ExecutionContext` | 每任务预算、授权、当前语言、模型请求和单工具调用 |
| `AgentModule<I>` | 在自己的策略内组合流程与多个工具 |
| `ToolRegistry.Tool<I, O>` | 一个工具类型、一个 Handler、一个行为 |

执行结果 `AgentResult` 复用 Saturn `ResultWithData<List<CardUpdate>>`：调用方统一用 `isSuccess()` / `isFailure()` 分支，Agent 自己保留文案、静默输出和 Status 枚举；SUCCEEDED、FAILED、PARTIALLY_SUCCEEDED 按稳定小写字符串编码。`partial()` 标记部分成功，但仍属于失败；`withPresentation(...)` 只生成展示副本。跨网络与存档继续使用 `AgentResult.CODEC`，不传输 Failure 的实现类。商城的 `CommerceCosts.Prepared` 同样复用 `ResultWithData<Price>`，同时保留原始定价失败原因与相关资源。搜索条目、模型回复及音频流解析状态属于数据载荷，不套用操作成功/失败契约。

外层 ServerModule 注册工作流时记录 ownerId，ModuleManager 在模块成功激活后发布按玩家计算的权限快照；帮助、补全、语义候选、模型目录与执行器共用可用性过滤，ExecutionContext 在工具和模型入口再次检查权限。基础设施与无 owner 的独立扩展工作流不属于可选业务模块。结构化按钮、精确指令、本地语义与模型选择最终都生成相同的 `TypedPayload<?>`。交互与事件共享 `AgentEngine.execute`，权限、策略选择与部分成功报告集中处理；事件恢复继续使用持久化策略和已完成操作回执。

## 玩家模块存储

玩家私有记录位于 `<存档>/system-agent/<UUID>.json`。PlayerData.moduleData 使用 Saturn `DataStorageHolder<Identifier>`；Holder 键为 ServerModule ID，具体 Java 类型区分同一模块的多项存储，Codec 类型 ID 独立。

存储由 `ServerModule.storages()` 返回 ModuleStorage 声明，包含 owner、Codec ID、Java 类型、MapCodec 和工厂；声明从正式的 `SystemRegistries.SERVER_MODULE` 注册表读取。内置 Codec 由 SystemDataStorages 的 DeferredRegister 注册到 Saturn DATA_STORAGE_TYPE，扩展的 Codec 需另行注册。不同模块可以复用同一类型与 Codec，各自持有独立数据。业务访问统一用 `ModuleData.get(data, SomeSystemModule.STORAGE)`，不要在 PlayerData 加功能访问器。完整声明与生命周期见[功能模块化](feature-modules.md#玩家生命周期)。

ModuleManager 统一处理模块启停、Session 创建/初始化/ready/tick/close、Saturn DataStorage tick 与异步保存。只向 Agent 和客户端发布已初始化的模块；旧连接、旧系统代次或已移除模块的回调不能重新激活会话。主线程存储 tick 只尝试内存事务，文件写入在维护线程合并执行；普通业务写事务继续原子落盘。读取和事务结果不暴露可变缓存引用，失败事务不污染已提交内存数据。停用保留签到、订单和幂等记录；revoke/reset 删除玩家文件及缓存，用量 SavedData 独立保留。

`TaskExecutor` 不改变各队列的规模：交互 4/32、事件 2/64、维护 2/32。队列满立即返回 `BUSY`，关闭时排队但尚未执行的 Future 返回 `CANCELLED`。这些队列不在游戏线程等待模型；游戏 API 继续由平台队列安排到主线程。

## 给 Module 声明入口

例如查询模块的 `routing()`：

```java
@Override
public ModuleRouting<ItemQueryInput> routing() {
    return ModuleRouting.<ItemQueryInput>defaults()
            .command(AgentCommand.argument("<query>", text ->
                    new ItemQueryInput(text, 0),
                    "item", "物品"))
            .semantic(RoutingProfile.read(text -> {
                String id = CommandArguments.singleItemId(text);
                return id == null ? null : new ItemQueryInput(id, 0);
            }, List.of("查询物品 minecraft:diamond"),
               List.of("Look up item minecraft:diamond")));
}
```

无参数指令使用 `AgentCommand.literal(input, aliases...)`。指令解析器返回 Java 参数对象，由注册表绑定到所属 Module 的类型键并校验 Codec。参数缺失、歧义或非法时，明确指令返回输入错误；语义提取器返回 `null` 时回退到模型。

`RoutingProfile.read` 是本地直达申请，注册表还要求模块可交互、写预算为零、所有声明工具只读，并通过相似度、分差、覆盖率和语句保护。相似度不是正确概率。写流程使用 `RoutingProfile.hints` 提供样例，仍由模型决定或由明确入口调用。

`.shortcut(labelKey, input)` 声明澄清提示中的只读按钮；不设置则不展示。`.structuredOnly()` 用于付款确认等操作：不允许声明文本指令，不进入模型能力目录或返回值解码，只接受结构化操作。执行入口再检查来源，避免每个模块各写一次来源判断。

拒绝样例单独使用 `engine.modules().reject(identifier, RoutingProfile.hints(...))` 注册。其 ID 与模块不能冲突，没有可执行入口，只参与本地分差与回退判断。

## 保持 Tool 单行为

模块构造器传入工具实例并用 `List` 组合：

```java
super(INPUT_TYPE, "Perform a bounded workflow.", Input.CODEC,
        List.of(queryTool, creditTool), true, false, policy);
```

执行时分别调用：

```java
Snapshot state = context.tool(QUERY_TYPE, "snapshot", input);
Receipt receipt = context.tool(CREDIT_TYPE, "credit", creditInput);
```

不要给 Tool 增加 `operation` 字段来分派一组行为。同一工具由多个模块使用时，复用同一个实例和 `ToolRegistry.Type`。写工具声明幂等及事件权限；恢复时稳定的 `stepId` 对应既有回执，已完成操作不会重复执行。存档只保留一份全局操作回执，不额外保留未使用的任务内步骤表。

## 两端指令与补全

助手输入框通过状态快照取得服务端注册表的指令候选。`HelpModule` 从同一注册表生成模块指令说明，FTBQ 未安装时不会显示任务指令。客户端界面切换、设置、历史和当前动作选择仍由客户端处理；它们的执行与补全共用本地指令表。

游戏聊天的 `/system <instruction>` 使用通用参数节点转发给同一 Agent 指令解析器，补全实时读取注册表。平台的 `grant/revoke/reset`、`usage`、`routing/ask/simple/full` 已分别收进绑定、用量及核心功能的 `commands(root)`。新增 Agent 指令只改工作流声明；需要独立的 Brigadier 节点时由所属功能贡献。客户端本地指令、补全及 `/system settings` 也从客户端功能装配，扩展应避开已有别名。

## 请求、响应与类型分派

`AgentRequest.CODEC` 与 `AgentResponse.CODEC` 从正式内置注册表 `SystemRegistries.AGENT_REQUEST_TYPE / AGENT_RESPONSE_TYPE` 的 `byNameCodec()` 按 `type` 分派；实例的 `codec()` 返回同一个已注册 MapCodec。内置条目通过 `SystemRequestTypes / SystemResponseTypes` 的 DeferredRegister 在两端登记，不再维护 DispatchRegistry 或自建类型 Map。未知 ID 和未注册的 Codec 由原版注册表 Codec 拒绝，定义中不能引用可选模组的类。卡片数据实现公共 card 包的 `CardData`，根 Codec 按相同方式分派各实现的 MapCodec。buildCard 返回包含模板、参数、按钮和 SpeechContent 的 CardBuilder，客户端再 build 成 CardView；不查客户端模块表或业务服务。动作仍以 TypedPayload 执行，卡片按钮通过 CardActionRequest 携带来源 CardRef 与动作 ID/字符串参数，ModuleRouting.button 负责适配并校验原输入 Codec。条目列表见[注册表速查](registries.md)。

请求实现为 `agent.request` 下的 `ChatRequest`、`InvokeRequest`、`ControlRequest`、`PreferencesRequest` 和 `CardActionRequest`，响应实现为 `agent.response` 下的 `ReplyResponse`、`NoticeResponse`、`OpenResponse`、`BindingResponse`、`StateResponse` 和 `HistoryResponse`。每个实现维护静态 MapCodec，`SystemRequestTypes` / `SystemResponseTypes` 只负责注册；根接口不再内联实现。`ControlRequest` 是枚举，其 Codec 保留 `action` 字段；导航与绑定枚举分别由对应响应持有。

以下 JSON 仅示意 Codec 结构；网络实际通过 `ByteBufCodecs.fromCodecWithRegistries` 编码类型对象：

```jsonl
{"type":"system:chat","text":"查看我的背包"}
{"type":"system:invoke","call":{"moduleId":"system:player_query","input":{}}}
{"type":"system:control","action":"STATE"}
{"type":"system:preferences","history":true,"external":true}
```

响应类型为 `system:reply`、`system:notice`、`system:open`、`system:binding`、`system:state` 和 `system:history`。状态与历史不占用卡片类型；历史恢复为只读，不播报、不执行按钮。回复关联请求 UUID，通知直接进入消息队列；导航携带 `CURRENT/SIMPLE/FULL`，绑定变更携带 `BOUND/REVOKED/INVALIDATED`。请求 ID 只作关联，不再传递 `bound/open/event/revoked` 等操作字符串。网络协议为 1，两端需一起更新。

服务端请求和客户端响应使用 util.TypeDispatch 分派运行行为；控制动作和 NLS 事件使用 Map。卡片本身通过 buildCard 提供展示与播报，ClientCards 按 ID/版本处理 SHOW/REPLACE/REMOVE，不再登记按类型刷新或口播处理器。ReplyResponse 完成请求与是否含卡片无关；客户端跟踪页面归属和超时，迟到更新不能抢新页。详见[卡片数据流](card-flow-design.md)。

FTB 查询 Tool 使用固定返回类型：`ftb.query` 列出当前可执行或可领奖任务、`ftb.find` 搜索可读任务，均返回 `List<QuestBriefOutput>`；`ftb.detail` 按 ID 或玩家上次列表编号返回一个 `QuestOutput`，不存在或不可查看时抛出业务错误。`QuestDetailModule` 负责名称搜索、歧义候选展示和单个详情读取；详情快照的 `starterGift` 只标记任务分类，遇到礼包时由 Module 再调用共享的礼包列表工具。不再为任务查询定义多态结果或类型注册表。任务对象和队伍进度只在兼容层的服务端线程读取，传给 Agent 的是带 Codec 的数据快照。

模型路由仍保留对外的 `capabilityId` 字段，解码 Map 只包含当前可选择的 Module 与 `clarify`，不会因共享输入类型存在就开放未安装模块。

新增业务能力继续通过 Module 注册。若要新增协议请求，增加请求类型及其 `MapCodec` 注册，再在 `AssistantRequests.registerRequests()` 登记执行处理器；新增响应时同步登记类型 Codec 和客户端处理器，不再增加字符串判断或类型 switch。类型实现保持无默认方法的接口，Codec 声明在实现类，注册集中于 `registry` 包，根 Codec 延迟访问注册表，避免子类静态初始化时递归读取尚未赋值的 Codec。
