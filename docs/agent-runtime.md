# Agent 运行结构与扩展

Agent 代码统一在 `com.iafenvoy.system.agent`。新增能力只需要声明参数类型、单行为 Tool、组合流程的 Module，以及 Module 自己的入口信息。指令解析器和路由器不包含模块 ID 的业务 switch。

根包集中引擎、执行器、上下文、Module/Tool 契约、注册表、指令与请求结果。路由、模型、存储、商城和提示词分别归入 `routing`、`model`、`store`、`commerce`、`prompt`，各包同时包含自身的接口、数据与实现。内置 Module 位于 `builtin`，单行为工具及输入输出分别位于 `builtin.tool`、`builtin.input`、`builtin.output`。共用 Codec、Schema 和 JSON 工具直接位于外层 `com.iafenvoy.system.util`，配置位于 `com.iafenvoy.system.config`。

执行配置、模型连接、语义路由与成就绑定分别由 `AgentSettings`、`ModelSettings`、`SemanticSettings`、`AdvancementSettings` 管理，各配置类保存 `ConfigInstance<T> INSTANCE`。容器统一复制缺失的默认文件并解析，读取使用 `INSTANCE.get()`，提交使用 `INSTANCE.set(next)` 并自动 `markDirty()`，由 tick 或关闭事件保存。资源重载会更新容器；引擎、模型客户端与 MC 事件桥接仍持有启动时快照，完整应用其变化需要重启。引擎接收执行配置、语义配置和已经过数据包校验的 `CommerceCatalog`；模型客户端只接收 `ModelSettings`，不再通过 `AgentSettings` 访问业务定义。

## 一次注册，多种入口

`ModuleRegistry` 是当前服务器能力的来源。`engine.registerModule(module)` 同时处理：

1. 核对已共享注册的 `TypedPayload.Type<I>`、模块 Identifier 与工具白名单。
2. 校验所有指令别名和工具实例冲突，然后注册 Module 的工具 List。
3. 绑定指令参数解析器、语义样例和参数提取器，不再重复填写模块 ID。
4. 记录模型可见性和只读快捷入口，供模型目录、澄清提示与帮助使用。

`engine.start()` 校验策略和事件引用，冻结注册表并建立语义索引。启动后禁止继续注册工具、模块或 profile。未安装的可选模块不注册，因此不会出现在指令、帮助、补全、语义可执行候选或模型目录中。

普通模块仍需先在公共初始化中注册 `ResultTypes.ACTIONS` 类型键；按钮、模型和 Module 复用同一键。类型与 Codec 必须在两端可用，定义不得引用未安装的可选模组类。

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

结构化按钮、精确指令、本地语义与模型选择最终都生成相同的 `TypedPayload<?>`。交互与事件共享 `AgentEngine.execute`，权限、策略选择与部分成功报告集中处理；事件恢复继续使用持久化策略和已完成操作回执。

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

游戏聊天的 `/system <instruction>` 使用通用参数节点转发给同一 Agent 指令解析器，补全实时读取注册表。因此扩展不需要再改 Brigadier 指令树。平台仍单独负责 `grant`、`revoke`、`usage`、`routing`、`ask`、`simple` 和 `full`；扩展指令应避开这些名字及客户端本地控制别名。

## 请求、响应与类型分派

`AgentRequest.CODEC` 与 `AgentResponse.CODEC` 使用 `util.codec.DispatchRegistry`：注册 `Identifier` 和 `MapCodec<T>` 后取得类型键，实例的 `type()` 返回同一个键。编码和解码共用原版 `Codec.dispatch`；重复 ID、未知 ID 或伪造的同名类型键会被拒绝。注册在两端公共初始化阶段完成，定义中不能引用可选模组的类。

以下 JSON 仅示意 Codec 结构；网络实际通过 `ByteBufCodecs.fromCodecWithRegistries` 编码类型对象：

```jsonl
{"type":"system:chat","text":"查看我的背包"}
{"type":"system:invoke","call":{"moduleId":"system:player_query","input":{}}}
{"type":"system:control","action":"STATE"}
{"type":"system:preferences","history":true,"external":true}
```

响应类型为 `system:reply`、`system:notice`、`system:open` 和 `system:binding`。回复关联请求 UUID，通知直接进入消息队列；导航携带 `CURRENT/SIMPLE/FULL`，绑定变更携带 `BOUND/REVOKED/INVALIDATED`。请求 ID 只作关联，不再传递 `bound/open/event/revoked` 等操作字符串。网络协议为 3，两端需一起更新。

服务端请求执行、客户端响应处理、卡片状态更新、任务列表来源、摘要投影、展示和口播使用 `util.TypeDispatch` 的类型处理 Map；控制动作、消息文案和 NLS 事件使用显式 Map。它们负责运行行为，不承担编解码。未注册的请求处理器报 `UNSUPPORTED`；通用卡片展示沿用默认格式，摘要和口播标题提取只读取已登记的数据。UI 动画、按键和播放状态机继续使用自身状态判断。

任务查询的 `QuestLookupOutput` 按 `system:quest_page` / `system:quest_detail` 分派，不再用 `detail` 布尔值推断子类型。模型路由仍保留对外的 `capabilityId` 字段，解码 Map 只包含当前可选择的 Module 与 `clarify`，不会因共享输入类型存在就开放未安装模块。

新增业务能力继续通过 Module 注册。若要新增协议请求，增加请求类型及其 `MapCodec` 注册，再在 `AssistantRequests.registerRequests()` 登记执行处理器；新增响应时同步登记类型 Codec 和客户端处理器，不再增加字符串判断或类型 switch。类型实现保持无默认方法的接口，Codec 注册集中于嵌套 `Types`，避免子类静态初始化时递归读取尚未赋值的 Codec。
