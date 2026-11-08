# Agent 运行结构与扩展

四种执行策略、发送框模式菜单、管理员与玩家权限，以及自由模式的功能调用见 [执行模式与管理员 AI 权限](free-chat.md)。

`com.iafenvoy.system.agent` 只负责执行、路由、预算、会话记忆与 Agent 契约。具体工作流位于 `module.builtin.<功能>`，Minecraft 业务、主线程桥接和游戏 Tool 位于 `assistant`，数据与加载位于 `data`，任务文案和语音行为位于 `speech`。完整依赖方向及扩展入口见[包职责与数据流](architecture.md)。指令解析器和路由器不包含模块 ID 的业务 switch。

外层内置模块定义位于 `module.builtin`；其业务 DataStorage 位于 `module.builtin.storage`。Agent 执行账本的接口 `AgentJournal` 位于 `agent.storage`，实现 `AgentJournalDataStorage` 与文件存储位于 `data.store`，在服务端装配时注入。`AgentStore` 同样只声明事务接口；引擎不直接依赖 CoreSystemModule、Saturn 存储实现或具体功能模块。

Agent 根包保留 `AgentResult`、`AgentException`、`AgentFailure`、`AgentLifecycle` 公共契约，其他类型按职责分包：

| 子包 | 内容 |
| --- | --- |
| `execution` | AgentEngine、交互/事件执行器、ExecutionContext、ExecutionPolicy、有界任务队列与会话读写服务；紧密协作的内部方法仍使用包可见性 |
| `module` | AgentAction、AgentModule、AbstractAgentModule 和 ModuleRegistry，声明工作流、动作类型及入口 |
| `tool` | ToolRegistry、ToolContext 和 ToolObservation，负责工具类型、实例、作用域及类型化观察 |
| `command` | AgentCommand、CommandRegistry 和 CommandArguments，负责 Brigadier 入口与参数解析 |
| `react` | ReActLoop、ReActContext 和 ReActTool，负责通用有界循环与循环独立状态 |
| `storage` | AgentStore、AgentJournal 和 ConversationMemory，负责存储契约与历史上下文投影；具体持久化仍由 data.store 实现 |
| `routing` / `model` | 混合路由、语义索引与缓存；模型接口、网关与协议实现 |
| `request` / `response` | 请求/响应基接口、实现与 MapCodec |

`AbstractAgentModule` 为具体工作流提供注册 ID、Schema 与工具白名单的共用实现。业务输入输出统一放在 `data.input` / `data.output`，共享工具键放在 `module.builtin.BuiltinTools`。共用 Codec、Schema 和 HTTP 工具继续放在外层 `util`。这里的 `agent.module` 是 Agent 工作流契约，与外层 `module` 的功能安装和生命周期管理分工不同。

执行预算、模型连接、语义路由与成就规则由 `AgentConfig`、`ModelConfig`、`SemanticConfig`、`AdvancementConfig` 的 ConfigInstance 管理，读取使用 `INSTANCE.get()`，提交使用 `INSTANCE.set(next)` 并自动标记保存。引擎构造只接收 AgentStore、ModelClient 与事务账本解析函数；商城目录、搜索、数据库与任务口播由 AssistantServer 装配，通过 ModuleContext 注入需要它们的模块。配置的动态读取与启动时快照仍遵循各使用方原有行为。

## 一次注册，多种入口

`ModuleRegistry` 是当前服务器能力的来源。`engine.registerModule(module)` 同时处理：

1. 核对已共享注册的 `MapCodec<I>`、模块 Identifier 与工具白名单。
2. 校验所有指令别名和工具实例冲突，然后注册 Module 的工具 List。
3. 绑定指令参数解析器、语义样例和参数提取器，不再重复填写模块 ID。
4. 记录模型可见性和只读快捷入口，供模型目录、澄清提示与帮助使用。

`engine.start()` 校验策略和事件引用，冻结注册表并建立语义索引。启动后禁止继续注册工具、模块或 profile。未安装的可选模块不注册，因此不会出现在指令、帮助、补全、语义可执行候选或模型目录中。

纯服务端 `ToolModule` 由 ModuleManager 先安装到引擎 ToolRegistry，保留所属功能 ID；不注册工作流或玩家入口。消费方用 `ModuleContext.tool(type)` 取得共享工具实例并加入工作流白名单；`ExecutionContext.tool` 在原有预算、事件和写入检查之外，检查导出工具所属功能是否对当前玩家启用。具体模型规划由工作流负责，纯工具不会成为意图路由目标。

动作输入实现 `AgentAction`，`codec()` 返回本类 MapCodec；先通过 DeferredRegister 在 `SystemResourceKeys.ACTION_TYPE` 注册这个 Codec，内置声明由 `SystemActionTypes` 公开。Module 的 `inputCodec()` 返回同一实例；按钮、指令和模型路由都直接生成输入对象。类型与 Codec 必须在两端可用，定义不得引用未安装的可选模组类，详见[注册表速查](registries.md)。

## 职责边界

| 类型 | 负责 |
| --- | --- |
| `AgentEngine` | 执行生命周期、公开入口、统一模块执行与失效代次；不拥有具体业务服务 |
| `AgentStore` / `AgentJournal` | 玩家事务与执行回执接口，由宿主注入实现 |
| `AgentLifecycle` | 撤销、断线和权限变化钩子，业务自行清理缓存 |
| `AssistantBindings` / `CommerceTransactions` | 绑定与删除业务数据、交易及其报价；均位于 assistant |
| `ModuleContext` | 固定提供 engine() / modules()，安装时通过 ContextType<T> 注入扩展服务，装配前发布 ModuleContextEvent |
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

执行结果 `AgentResult` **不再继承** Saturn 的结果类型：Saturn 1.2 的 `Result<T>` 是 `sealed interface`，只允许它自己的 `SuccessResult` / `FailureResult` 实现，因此携带额外字段的类型改为自己持有状态、由 `Status` 派生 `isSuccess()` / `isFailure()`（`PARTIALLY_SUCCEEDED` 算失败）。Agent 自己保留文案、静默输出和 Status 枚举；SUCCEEDED、FAILED、PARTIALLY_SUCCEEDED 按稳定小写字符串编码。`partial()` 标记部分成功，但仍属于失败；`withPresentation(...)` 只生成展示副本。跨网络与存档继续使用 `AgentResult.CODEC`，不传输 Failure 的实现类。商城的 `CommerceCosts.Prepared` 用 `Optional<Failure>` 表达拒绝：报价无论定价成功与否都要给出展示价格，所以价格单独持有，只有「拒绝原因」是可选的。搜索条目、模型回复及音频流解析状态属于数据载荷，不套用操作成功/失败契约。

`AgentException` 只保存 Saturn `Failure`，通过 `failure()` 读取，不再接收字符串 code。内置原因由 `AgentFailure implements Failure` 枚举集中声明，保留 `system.error.*` 翻译键。扩展可直接传自己的 Failure，不必加入内置枚举。异步边界用 `AgentException.failure(Throwable)` 从异常链提取原值，未知异常映射为 `MODULE_FAILED`；包装时保留 cause。`AgentResult.error(Failure)` 使用原值的 key / message，携带资源名等参数的文本也能传入卡片。ReAct 恢复集合与降级回调使用 Failure；只有搜索供应商的错误码和已持久化的订单回执名称由 `AgentFailure.fromCode` 转换，未知名称统一降为 MODULE_FAILED。网络仍发送可翻译的消息键或 Component，不序列化 Failure 实现类。

外层 ServerModule 注册工作流时记录 ownerId，ModuleManager 在模块成功激活后发布按玩家计算的权限快照；帮助、补全、语义候选、模型目录与执行器共用可用性过滤，ExecutionContext 在工具和模型入口再次检查权限。基础设施与无 owner 的独立扩展工作流不属于可选业务模块。结构化按钮、精确指令、本地语义与模型选择最终都生成相同的 `AgentAction`。交互与事件共享 `AgentEngine.execute`，权限、策略选择与部分成功报告集中处理；事件恢复继续使用持久化策略和已完成操作回执。

## 玩家模块存储

玩家私有记录位于 `<存档>/system-agent/<UUID>.json`，对话与请求回放缓存在同目录的 `chat/<UUID>.dat`（NBT）；两者独立设上限并各自原子替换，缓存写不动不会阻塞业务写。PlayerData.moduleData 使用 Saturn `DataStorageHolder<Identifier>`；Holder 键为 ServerModule ID，具体 Java 类型区分同一模块的多项存储，Codec 类型 ID 独立。

存储由 `ServerModule.storages()` 返回 ModuleStorage 声明，包含 owner、Codec ID、Java 类型、MapCodec 和工厂；声明从当前世界 system:system_module 的 server 配置读取；静态 SystemRegistries.SERVER_MODULE 只注册 MapCodec 类型。内置 Codec 由 SystemDataStorages 的 DeferredRegister 注册到 Saturn DATA_STORAGE_TYPE，扩展的 Codec 需另行注册。不同模块可以复用同一存储类型与 Codec；数据按 ModuleStorage.owner 区分，内置同类配置共用该功能账本。业务访问统一用 `ModuleData.get(data, SomeSystemModule.STORAGE)`，不要在 PlayerData 加功能访问器。完整声明与生命周期见[功能模块化](feature-modules.md#玩家生命周期)。

除位于 `data.store` 的 Agent 执行账本外，内置业务 Storage 位于 `module.builtin.storage`。Storage 的集合字段保持 private，完整集合 getter 供 Codec 使用；业务在事务中通过 `getTask`、`putDelivery`、`streamPurchases` 等方法查询、写入和遍历，容量检查也封装在对应 Storage。集合增删、任务状态与预算、步骤回执、订单状态及发奖状态均通过 Storage 的修改方法调用 `markDirty()`；重复解锁、重复发奖和相同状态赋值不产生新的脏标记。事件任务与步骤回执同时受条数和编码字节数限制：超限时按插入顺序回收已终态条目，`QUEUED`/`RUNNING` 等崩溃恢复标记永不回收，只有整表都是未终态条目时才报 `STORAGE_FULL`。读取返回的账本对象仅用于查询，新建对象在写入前可直接填充。跨存储的签到与发奖、扣款与订单提交继续在原事务中由调用方统一编排。

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

无参数指令使用 `AgentCommand.literal(input, aliases...)`，资源 ID 参数使用 `AgentCommand.identifier(name, parser, aliases...)`；`argument` 把必选 `<query>` / 可选 `[query]` 转为原生 greedyString 节点。复杂命令可直接构造 `new AgentCommand<>(aliases, name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name).then(...))`，由原生 ArgumentType、requires 和 suggests 描述参数与权限。指令回调只把实现 AgentAction 的输入交给 `source.action(input)`，不在解析时执行业务；CommandRegistry 核对动作归属和 Codec，再进入原来的工作流执行。

CommandRegistry 每服务实例只构建一棵 Brigadier CommandDispatcher，解析、帮助和同步给 `/system` 的节点共用声明。服务端原生命令早于 AgentRuntime 构建，初次启动后由 `SystemCommand.install` 附加工作流节点，资源重载的 RegisterCommandsEvent 使用已有 runtime 装配；平台管理指令优先。同步到客户端的参数须使用 MC 已登记的 ArgumentType，助手专用自定义参数不自动登记为网络类型。模块启用变化和 revoke/reset 后重新发送按当前权限过滤的原生命令树。

`/view`、`/system view` 等明确指令语法错误会返回输入错误，未知斜杠指令不触发模型；普通文本未完整匹配时继续进入语义和模型路由。英文 literal 别名忽略大小写，任务名称和参数保持原文。客户端本地指令与补全树通过独立 `ClientCommandCache` 缓存，任务候选和按钮仍实时取当前结果，见[功能模块化](feature-modules.md)。

`RoutingProfile.read` 是本地直达申请，注册表还要求模块可交互、写预算为零、所有声明工具只读，并通过相似度、分差、覆盖率和语句保护。相似度不是正确概率。写流程使用 `RoutingProfile.hints` 提供样例，仍由模型决定或由明确入口调用。

路由低置信度且模型未配置或玩家关闭 AI 时，`RouteDecision` 使用 `LOCAL_FALLBACK` 来源并携带只读候选动作，交互执行器显示 `SelectionCardData`。`ModuleRegistry.suggestions` 依据已登记的快捷入口、profile 参数提取器及可选的 `.choice(labelKey)` 生成选项；参数只从本次原文解析，没有合适候选时退回通用快捷入口。点击是一次新的结构化请求，经原卡片控制器和服务端授权执行，不再调用模型路由；候选本身不会执行或写入成功路由缓存。模型主动要求澄清时也优先展示本次候选。

`.shortcut(labelKey, input)` 声明澄清提示中的只读按钮；不设置则不展示。模型选择策略通过 ModuleRouting.Selection 统一保存：

| 声明 | 模型能力目录与返回值解码 | 本地语义 / 意图缓存直达 |
| --- | --- | --- |
| `defaults()` | 正常参与匹配，`explicitOnly: false` | 沿用只读、参数、相似度等门槛 |
| `explicitOnly()` | 仍可选择，`explicitOnly: true`；非常确信当前意图才使用，否则澄清 | 禁止，只能给模型提供候选提示 |
| `structuredOnly()` | 不开放 | 禁止 |

`explicitOnly` 是传给模型的通用标记，不要求固定关键词，也不增加一次确认模型调用；提示词要求基于当前请求判断，而不是把本地相似度当作确信度。付款确认等操作继续使用 `structuredOnly()`，通过已注册的明确指令或结构化按钮触发。执行入口统一检查来源，不由每个模块重复判断。

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

事件任务和写步骤回执共用 `State` 枚举（`data.store` 包）。事件缺省为 `QUEUED`，执行中为 `RUNNING`，玩家离线时为 `WAITING`，这三种状态允许恢复；终态按执行结果记录为 `SUCCEEDED`、`FAILED` 或 `PARTIALLY_SUCCEEDED`。步骤缺省为 `RUNNING`，表示已记录执行意图但未确认结果；`DONE` 表示结果已持久化、可按原结果 Codec 回放。存档仍使用同名字符串；显式无效状态由 Codec 拒绝，不把未知业务状态当作新操作重试。未完成的非幂等工具仍报告 EFFECT_UNKNOWN。

Descriptor 的 `tools` 为必需工具，`optionalTools` 为可选只读工具 ID，二者不能重叠。可选项只在导出模块已启用时才能调用，缺席不会禁用本地工作流，也不自动注册处理器或授予模块。ExecutionContext.tool 同时检查声明、导出模块权限与只读属性；必需和可选调用共用原有工具 / 步数预算。知识问答使用该机制接入 FTB 说明书与网页搜索。

## 两端指令与补全

助手输入框通过状态快照取得服务端注册表的指令候选。`HelpModule` 从同一注册表生成模块指令说明，FTBQ 未安装时不会显示任务指令。客户端界面切换、设置、历史和当前动作选择仍由客户端处理；它们的执行与补全共用本地指令表。

游戏聊天的 `/system <instruction>` 使用通用参数节点转发给同一 Agent 指令解析器，补全实时读取注册表。平台的 `grant/revoke/reset`、`usage`、`routing/ask/simple/full` 已分别收进绑定、用量及核心功能的 `commands(root)`。新增 Agent 指令只改工作流声明；需要独立的 Brigadier 节点时由所属功能贡献。客户端本地指令、补全及 `/system settings` 也从客户端功能装配，扩展应避开已有别名。

## 请求、响应与类型分派

`AgentRequest.STREAM_CODEC` 与 `AgentResponse.STREAM_CODEC` 从正式内置注册表 `SystemRegistries.AGENT_REQUEST_TYPE / AGENT_RESPONSE_TYPE` 读取同步的数字 ID，并由 `StreamCodec.dispatch` 直接调用条目自己的 `streamCodec()`；实例的 `streamCodec()` 返回同一个已注册 StreamCodec。内置条目通过 `SystemRequestTypes / SystemResponseTypes` 的 DeferredRegister 在两端登记，不再维护 DispatchRegistry 或自建类型 Map。未知 ID 在流式解码阶段抛出，定义中不能引用可选模组的类。卡片数据实现公共 card 包的 `CardData`，根 Codec 按相同方式分派各实现的 MapCodec。buildCard 返回包含模板、参数、按钮和 SpeechContent 的 CardBuilder，客户端再 build 成 CardView；不查客户端模块表或业务服务。动作同样实现 AgentAction 并由注册 MapCodec 分派，卡片按钮通过 CardActionRequest 携带来源 CardRef 与动作 ID/字符串参数，ModuleRouting.button 负责适配并校验原输入 Codec。条目列表见[注册表速查](registries.md)。

请求实现为 `agent.request` 下的 `ChatRequest`、`InvokeRequest`、`ControlRequest`、`PreferencesRequest` 和 `CardActionRequest`，响应实现为 `agent.response` 下的 `ReplyResponse`、`NoticeResponse`、`OpenResponse`、`BindingResponse`、`StateResponse` 和 `HistoryResponse`。每个实现维护静态 `STREAM_CODEC`，`SystemRequestTypes` / `SystemResponseTypes` 只负责注册；根接口不再内联实现。`ControlRequest` 是枚举，按序号编码；导航与绑定枚举分别由对应响应持有。

以下是各请求的语义字段；线上不发送 JSON，`type` 对应的只是注册表数字 ID，其余字段按各自的流式编解码器写入：

```jsonl
{"type":"system:chat","text":"查看我的背包"}
{"type":"system:invoke","call":{"moduleId":"system:player_query"}}
{"type":"system:control","action":"STATE"}
{"type":"system:preferences","history":true,"external":true}
```

响应类型为 `system:reply`、`system:notice`、`system:open`、`system:binding`、`system:state` 和 `system:history`。状态与历史不占用卡片类型；历史恢复为只读，不播报、不执行按钮；历史条目按 `role` 判别，`assistant` 直接携带 `AgentResult` 对象，不再嵌套 JSON 字符串。回复关联请求 UUID，通知直接进入消息队列；导航携带 `CURRENT/SIMPLE/FULL`，绑定变更携带 `BOUND/REVOKED/INVALIDATED`。请求 ID 只作关联，不再传递 `bound/open/event/revoked` 等操作字符串。网络协议为 1，两端需一起更新。

服务端请求和客户端响应使用 util.TypeDispatch 分派运行行为；控制动作和 NLS 事件使用 Map。卡片本身通过 buildCard 提供展示与播报，ClientCards 按 ID/版本处理 SHOW/REPLACE/REMOVE，不再登记按类型刷新或口播处理器。ReplyResponse 完成请求与是否含卡片无关；客户端跟踪页面归属和超时，迟到更新不能抢新页。详见[卡片数据流](card-flow-design.md)。

FTB 查询 Tool 使用固定返回类型：`ftb.query` 列出当前可执行或可领奖任务、`ftb.find` 搜索可读任务，均返回 `List<QuestBriefOutput>`；`ftb.detail` 按 ID 或玩家上次列表编号返回一个 `QuestOutput`，不存在或不可查看时抛出业务错误。`QuestDetailModule` 负责名称搜索、歧义候选展示和单个详情读取；详情快照的 `starterGift` 只标记任务分类，遇到礼包时由 Module 再调用共享的礼包列表工具。不再为任务查询定义多态结果或类型注册表。任务对象和队伍进度只在兼容层的服务端线程读取，传给 Agent 的是带 Codec 的数据快照。

模型路由仍保留对外的 `capabilityId` 字段，解码 Map 只包含当前可选择的 Module 与 `clarify`，不会因共享输入类型存在就开放未安装模块。

新增业务能力继续通过 Module 注册。若要新增协议请求，增加请求类型及其 `MapCodec` 注册，再在 `AssistantRequests.registerRequests()` 登记执行处理器；新增响应时同步登记类型 Codec 和客户端处理器，不再增加字符串判断或类型 switch。类型实现保持无默认方法的接口，Codec 声明在实现类，注册集中于 `registry` 包，根 Codec 延迟访问注册表，避免子类静态初始化时递归读取尚未赋值的 Codec。

## 数据库优先的问答与联网回退

核心模块装配 `QuestionModule`，动作是 `system:question` / QuestionInput，必需工具只有 `database.query`。先查询当前 UUID 私有和共享 `system:knowledge` 表及已启用的 FTB 授权说明书；有资料时最多一次模型整理并展示本地来源，发送前重新查库复核。未配置模型、关闭外部 AI 或可恢复的整理失败仍保留原文，不转入联网；查库异常也不作为空结果。

SearchSystemModule 仅实现 ToolModule，导出 `web.search` / `web.fetch`，由数据包或管理员明确授予；开发默认 tag 也不自动启用。问答仅在本地为空、allowWeb=true、工具导出模块已启用且外部服务允许时打开本次 ExecutionContext 的联网门禁；两种网络工具每次都检查该门禁。`/question` 使用 false，`/search` 及搜索页明确提交使用 true；普通问题由模型识别但默认 false，仅非常确信当前请求授权外部查询时才设 true。语义样例仅作提示，不直接路由或缓存授权；事件、历史和资料正文不能开启联网。

获准后的 `WebResearch` 提供紧凑的 SearchObservation 和独立工具上下文，由通用 ReActLoop 按本次工具 MapCodec dispatch 选择 `web.search`、`web.fetch` 或 `answer`，最终整理只解码 Answer。仅允许抓取本次检索返回的 URL，引用编号由服务器核对。整个问答默认 6 轮循环 / 6 次模型 / 9 次工具 / 20 步 / 90 秒，单次模型 20 秒；数据库查询、内部 FTB 查询、复核和联网共用预算，按 `system:question` 覆盖。重复检索或可恢复的探索失败改为整理已有资料，不重试收费工具。每次网络工具都检查取消、模块权限与外部服务偏好，经 SearchGateway 计量；模型 Token 继续由 ModelGateway 记录。普通跨能力分析由核心 `system:reasoning` 复用相同循环，默认 3 轮，只开放已启用模块主动声明的只读工具，不自动授予联网。详见[通用 ReAct](agent-react.md)与[问答与联网](search-api.md)。

模块实现与配置分离：SystemModule 数据包条目提供 server/client，系统引用动态配置 ID 或 tag；ServerModule.CODEC 按 type 分派注册的 MapCodec。不同配置的同类型工作流与工具按玩家启用的 owner 选择，执行上下文保存选中 owner 并持续检查权限。ClientModule 只在客户端 Map 中登记，服务端类不再实现或引用客户端接口；客户端按 client ID 合并 UI、按关联配置 ID 进行权限判断。
