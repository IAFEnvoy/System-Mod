# 开发与架构

## 当前版本

ASR 使用无 MC 依赖的 `speech/asr/AsrProvider` 和 `AliyunAsrProvider`，客户端 `AssistantRecognition` 管理录音、取消及草稿填充，`RecognitionUsage` 与 AI / TTS 共用 SavedData；网络协议 2。可选 SVC 录音后端与 F8 按住说话共用原有 ASR 入口，详见[语音识别](asr.md)。

语音供应商接口及客户端录音和播放分别位于 `speech.asr` 与 `speech.tts`，注册表位于 `registry.speech`，协议实现位于 `speech.aliyun`；配置集中在 `config`。阿里云默认使用 `qwen3-tts-flash`，额外兼容设计音色绑定的 Qwen-Audio-TTS HTTP 协议；复用 `HttpTransport`，没有新增依赖或模型文件。详见 [语音合成与音色设计](tts.md)。

| 组件 | 配置 |
| --- | --- |
| 模组与 Agent Java toolchain / 字节码 | 25 / Java 25 |
| Gradle / ModDev | 9.2.1 / 2.0.148 |
| Minecraft / NeoForge | 26.1.2 / 26.1.2.114 |
| ApricityUI | 1.2.6 |
| Jupiter | CurseMaven 8569832 |
| Simple Voice Chat | 2.6.26+26.1.2 / CurseMaven 9307181；可选客户端录音，compileOnly + localRuntime，不打入发布 JAR |
| Gson | 2.13.2；主项目直接声明依赖，Minecraft 提供运行时类 |
| FTB Quests / Library / Teams | 26.1.2.8 / 26.1.2.9 / 26.1.2.4，可选 compileOnly |
| LangChain4J | 已移除；使用 JDK HTTP 网关 |
| 本地意图路由 | `lexical-tfidf-v1`，JDK + 已有 Gson；无新增第三方依赖、native 或模型文件 |
| 产物体积上限 | 完整 System JAR 不超过 10,000,000 字节，`jar` 任务强制检查 |

Agent 统一放在独立外包 `com.iafenvoy.system.agent`：根包集中引擎、执行器、执行上下文、模块/工具接口、注册表、指令与请求结果；`builtin`、`routing`、`model`、`store`、`commerce`、`prompt` 按职责组织，每个职责的接口、数据与实现放在一起。通用 Codec、Schema 和 JSON 工具直接归入外层 `com.iafenvoy.system.util`，配置统一位于外层 `config`。游戏生命周期、界面、网络、语音和可选模组桥接继续位于模组的对应包；所有代码共用主源码集、依赖和 Java 25 工具链，不单独发布 Agent 核心库。

```powershell
.\gradlew.bat compileJava testModClasses jar
```

所有 Agent 类直接进入 `build/libs/system-1.0.0.jar`。FTB 查询、详情和领奖直接实现统一的类型化 Tool，没有独立的服务接口，也不经过 stdio、HTTP MCP 或模拟客户端包。可选传输适配器如需接入，也应复用同一 Tool 契约。

模型请求统一使用 OpenAI Chat Completions 格式，默认保留 DeepSeek；专用思考参数按提供商隔离。配置与消息边界见 [模型接口与 DeepSeek 兼容](model-api.md)。

## 目录与职责

| 路径 | 职责 |
| --- | --- |
| `agent` | 引擎与执行器、预算和授权上下文、Module/Tool 契约、类型/模块/工具/指令注册表、请求与结果 |
| `agent/builtin` | 内置 Module、安装入口和签到模型决策结构 |
| `agent/builtin/input、agent/builtin/output` | 内置模块/工具参数及查询结果，一类一文件 |
| `agent/builtin/tool` | 每个行为各自一个 Tool（含游戏适配与内部入账工具），以及共享工具类型 |
| `agent/routing` | 混合路由、本地匹配与缓存、语义声明、路由结果及诊断 |
| `agent/model` | 模型接口、统一请求与计量入口、OpenAI 兼容协议及报文 |
| `agent/store` | 存储接口、文件事务存储、玩家业务记录与对话投影 |
| `agent/commerce、agent/prompt` | 商城目录、报价和系统币接口；提示词快照与占位符模板 |
| `config` | Agent/路由配置，以及模组客户端、语音和计费配置 |
| `command/client、command/server` | 两端命令注册、分派与补全，调用 Agent 根包中的类型化指令声明与解析 |
| `util` | Codec/Schema/JSON 工具、共享 HTTP/SSE 与统一 config/system 路径 |
| `runtime/server、runtime/reload` | 服务端生命周期、主线程队列、提示词和商品数据包重载 |
| `runtime/usage、data/usage` | 独立 SavedData、AI/TTS/ASR 归并与不可变快照，不随 revoke 删除 |
| `runtime/client、screen/gui、screen` | 客户端对话、助手界面、设置、用量与通知队列 |
| `speech/aliyun、speech/tts、speech/asr、registry/speech、data/speech` | 语音接口及供应商协议、工厂注册、报文、流式播放会话、缓存与可取消录音 |
| `compat/ftb、compat/voicechat` | 可选 FTB 工具/事件和 SVC 收音，检测后才加载相关实现 |
| `event、network、network/payload` | 扩展/按键事件、协议 2 注册、Handler 与有界载荷 |

上表路径均相对于 `src/main/java/com/iafenvoy/system/`。Agent 与游戏接入代码处于同一主项目；模块、工具和服务接口仍保留职责划分，工具使用稳定逻辑 ID，方便后续扩展业务。

## Codec 边界

`CodecIO` 统一限长 JSON 解析、Codec 编解码及配置文件原子写入；HTTP 响应可通过 `Document` 一次解析后分别解码用量与正文，保证正文无效仍记录已计费用量。`ObjectCodec.of` 为存档和恢复日志保持严格字段解析；配置使用 `ObjectCodec.lenientOf(factory, validator)`，按字段以 `lenientOptionalFieldOf` 读取，并在独立默认对象上校验该设置。缺失、null、类型错误、无效枚举、越界值或嵌套 Codec 构造异常只回退该字段，其他有效配置保留，默认可变集合由构造器重新创建；玩家文件仍使用 FileAgentStore 的原有 force/原子替换事务。

`AgentRequest` 表达聊天、模块调用、控制请求和偏好；C2S 的原字段在网络边界转换。模型路由返回值按 capabilityId 分派输入 Codec，签到仅接收 SignInDecision，发送的玩家投影不包含其他在线玩家。ConversationMemory 使用有界、允许字段组成的对象投影。

固定业务数据、配置、存档、模型与语音报文使用 Codec。JSON 只保留在动态 JSON Schema 与通用界面格式化边界；工具写入回执使用原版 Dynamic/PASSTHROUGH 保存暂不解释的结果，回放时由对应工具结果 Codec 解码，不在业务中按键取值。

配置容错只适用于已经解析成对象的合法 JSON，语法损坏、重复键、超出文件大小限制或文件不可读仍报错。列表、映射及严格业务参数中的错误可使所属配置字段整体回退，其他顶层设置继续保留；不为奖励、事件和付款引用编造 ID。收费表无法确定单价的条目跳过并标为未估价，其余有效条目继续使用；配置读取不会覆盖用户文件。

## 网络约定

主注册类只负责载荷及处理器注册。C2S 由服务端 Handler 在主线程验证 UUID、从连接读取玩家并分派到服务端能力；S2C 由客户端 Handler 解码并分派到客户端状态或界面。客户端处理器通过 NeoForge 的客户端注册事件绑定，专用服务端不解析客户端界面类。连接代次、迟到回包防护、结果长度限制与用量权限检查保持原有规则。

| 载荷类 | 网络 ID |
| --- | --- |
| `AssistantRequestC2S` | `system:assistant_request_c2s` |
| `AssistantResponseS2C` | `system:assistant_response_s2c` |
| `TokenUsageRequestC2S` | `system:token_usage_request_c2s` |
| `TokenUsageResponseS2C` | `system:token_usage_response_s2c` |
| `SpeechUsageReportC2S` | `system:speech_usage_report_c2s` |
| `RecognitionUsageReportC2S` | `system:recognition_usage_report_c2s` |

当前协议版本为 **2**，结果卡片和动作使用类型分派的新结构，客户端与服务端需一起更新；C2S 请求字段保持原样；当前项目未发布，存档只读取当前 Codec 结构。新增载荷时在 `network/payload` 定义带方向后缀的类型，在对应 Handler 增加处理方法，再由 `NetworkManager` 注册。

## 扩展模块

实现 `AgentModule<I>`，提供已注册的 `TypedPayload.Type<I>`、描述信息和 `List<ToolRegistry.Tool<?, ?>> tools()`，在 `execute` 中处理不可变参数。内置模块继承 `BuiltinModule<I>`：构造时传入共享输入类型键 `TypedPayload.Type<I>`、`SchemaMapCodec<I>` 和工具列表，描述中的输入 schema 从 Codec 获取，工具白名单从列表生成，避免重复声明。模块是受信 Java 代码，Codec 不替代业务授权，也不是同 JVM 沙箱。

模块 ID 使用原版 `Identifier`：`Descriptor.id`、输入类型键 `TypedPayload.Type.id` 及模块注册表的键均为该类型。先用 `ResultTypes.ACTIONS.register(identifier, inputCodec)` 注册共享类型键，模块的 `inputType()`、按钮、指令与路由必须复用这个键；引擎拒绝 ID 不一致或未注册的类型键。`BuiltinModule` 从传入类型键取得 Identifier。配置和网络仍编码为 `namespace:path` 字符串。

每个 Tool 只负责一个行为，`ToolRegistry.Type<I, O>` 保存稳定 ID、参数 `MapCodec<I>` 和结果 `Codec<O>`，`Tool<I, O>` 保存写入、幂等、事件权限和单一 Handler。模块直接调用 `context.tool(type, stepId, typedInput)` 获得类型化结果；一个流程需要多个行为时使用模块的 `List.of(toolA, toolB)` 组合。同一工具被多个模块使用时共享同一实例，重复的不同实现或同 ID 不同类型键会被拒绝。

扩展在 `AssistantRegistrationEvent` 中只需调用 `engine.registerModule(module)`：注册表核对描述白名单与工具列表，检查模块 ID、指令别名和工具实例冲突，再统一收集工具、指令、语义 profile 和快捷入口。不再提供先注册工具、再注册模块的两阶段入口。FTB 也使用此路径，查询、详情和领奖由三个独立 Tool 及对应模块组成，不需要专用服务接口。

`PayloadRegistry` 参考原版 `Recipe` / `PlacementModifier` 的类型分派，统一使用 `Codec.dispatch` 按 `moduleId` 选择共享参数 Codec；调用封装为 `{"moduleId":"system:item_query","input":{"query":"stone"}}`。`ModuleRegistry.call` 根据同一类型键定位当前服务器已安装模块、校验参数并生成可执行调用，不再维护另一套输入 dispatch Codec。模型目录和模型返回值解码都由当前模块注册表约束；未安装模块、事件专用模块、`structuredOnly()` 模块不会成为模型可选能力。网络、配置和事件存档在边界使用 Codec，内部直接传 `TypedPayload<?>`。

`AgentCodecs.strict` 在原版 Codec 基础上额外拒绝未知键和显式 null；精确整数 Codec 拒绝小数和溢出值，避免原版 `Codec.INT` 的截断行为影响索引。业务参数的缺省值由严格 `optionalFieldOf` 提供，类型错误不静默回退；配置字段统一使用 `lenientOptionalFieldOf` 回退默认值，两者互不影响。Codec 解析失败不接受部分结果。写工具回放要求指纹完全一致，再由对应工具的结果 Codec 恢复对象；不匹配时拒绝执行，不归一旧格式，也不重新执行已完成写操作。

### 调度、路由与执行职责

- `AgentEngine` 提供统一入口，持有服务和玩家生命周期，并通过同一个 `execute` 校验模块权限、选择策略、执行流程、报告部分成功。
- `InteractiveExecutor` 管理每玩家一个交互请求、取消代次、请求指纹与结果回放；入队、初始化和写回都检查取消代次。
- `EventExecutor` 管理持久事件、规则授权、去重和恢复；恢复使用已保存策略、预算与工具回执。
- `ConversationService` 负责历史读取、清空和偏好修改，不重复实现执行或队列逻辑。
- `TaskExecutor` 统一有界调度、队列满错误、关闭和 Future 收尾；关闭后未执行的任务也得到 `CANCELLED`。
- `HybridRouter` 只编排结构化调用 → 指令注册表 → 本地语义/缓存 → 一次模型回退。每个模块通过 `ModuleRouting<I>` 提供自己的指令、例句、参数提取和可选只读快捷入口。
- `ExecutionContext` 是执行权限、预算、模型与工具调用的共同入口。Module 组合流程，Tool 每次只执行一个行为。

`engine.start()` 校验模块策略和事件引用，随后冻结模块、工具、指令和语义注册信息。扩展必须在 `AssistantRegistrationEvent` 中完成注册；当前不支持运行中增删模块。管理员生命周期指令与客户端界面操作各留在对应端，Agent 能力通过统一注册表调用。游戏聊天的 `/system <instruction>` 是通用转发入口，补全实时读取 Agent 注册表；助手输入框通过状态快照获得同一组指令，不需要为扩展修改客户端指令表。详细扩展示例见 [Agent 运行结构](agent-runtime.md)。

### 内置输入与输出

`agent.builtin.input` 与 `agent.builtin.output` 中每个类型独立成文件，直接导入后使用 `QuestDetailInput`、`PlayerSnapshotOutput` 等名称，不再通过聚合外类引用。输入使用带校验和 Schema 元数据的 MapCodec，输出使用 Codec；`QuestLookupOutput` 是由 QuestPageOutput / QuestDetailOutput 实现的 sealed interface。奖励入账参数 `RewardCreditInput` 归入输入包，公共物品和奖励 Codec 直接使用 PlayerData.Item.CODEC / PlayerData.Reward.CODEC。

### 从 Codec 定义生成输入 schema

`SchemaCodecs.record((i, f) -> ...)` 继续使用原版 `RecordCodecBuilder`，在声明字段时同时记录 JSON Schema 元数据，返回可直接用于原版 dispatch 的 `SchemaMapCodec`。例如：

```java
public record Search(String query, int offset) {
    public static final SchemaMapCodec<Search> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.string(1, 128)).forGetter(Search::query),
            f.optional("offset", SchemaCodecs.intRange(0, 100000), 0).forGetter(Search::offset)
    ).apply(i, Search::new));
}
```

`Search.CODEC.schema()` 返回包含 `type`、`properties`、`required`、`default`、字符串长度、整数范围和 `additionalProperties: false` 的 schema。`SchemaCodecs.BOOL` 支持布尔字段，`matching` 同时提供正则校验与 `pattern`，`object` 和 `Value.listOf()` 支持嵌套对象与数组，`described` 为字段或对象补充说明。空参数使用 `SchemaCodecs.unit`。schema 只在定义时组装，不增加网络调用或依赖；路由器沿用模块描述入口读取生成结果，模块构造函数不再接收手写 schema。

这不是对任意原版 Codec 的反射反编译：原版没有通用结构访问接口，`xmap` 和 `validate` 也可能包含任意 Java 逻辑。`SchemaMapCodec` 的 `xmap`、`flatXmap`、`validate` 和生命周期包装保留已有结构元数据，但不会自动翻译额外谓词。例如任务选择器三选一仍在 Codec 中校验，并通过 `described` 向模型说明。接入外部 Codec 时可显式构造 `SchemaCodecs.Value(codec, schema)`，或用 `SchemaMapCodec(codec, schema)` 包装外部对象 Codec；该显式 schema 的准确性由扩展负责。生成结果用于提示模型，执行时仍以 Codec 和业务授权为准，不启用新的模型侧 structured-output 协议。

### 结果卡片与动作

`AgentResult`、`Card` 和 `Action` 各自提供 `CODEC`。`Card.data` 为 `TypedPayload<?>`，携带类型键及不可变 Java 值；`Action.call` 同样携带类型化模块参数，其类型键 ID 就是模块的 `Identifier`。`PayloadRegistry` 使用原版 `Codec.dispatch`：卡片按 `type` 选择结果 Codec，动作按 `moduleId` 选择输入 Codec。`titleKey` 仅用于本地化显示，不参与新结果的类型判断。

```java
AgentResult result = AgentResult.data("system.result.player", ResultTypes.PLAYER.of(snapshot));
AgentResult.Action deliver = new AgentResult.Action("system.action.deliver", ResultTypes.DELIVER_ACTION,
        new EntitlementInput(entitlementId));
```

卡片数据编码为 `{"type":"system:player","value":{...}}`；动作编码为 `{"labelKey":"system.action.deliver","call":{"moduleId":"system:deliver","input":{"entitlementId":"..."}}}`。新业务直接创建输入/结果对象，不再为卡片或按钮手工拼接 `JsonObject`。客户端任务行、奖励选项、指令补全、设置状态和口播通过类型访问数据；通用文本格式化及 C2S 发送才调用 `TypedPayload.json()`，作为展示和网络边界。历史压缩继续只提取允许字段及有限领奖引用，不把整个卡片序列化后塞进模型上下文。

新增结果类型在公共初始化阶段调用 `ResultTypes.CARDS.register(identifier, codec)`，返回的类型键用 `.of(value)` 创建数据。新增模块按钮参数同样注册 `ResultTypes.ACTIONS`，复用模块输入 Codec。注册必须在两端、处理结果之前完成，类型 ID 唯一，未知 ID 或未注册的同名类型键在编解码时拒绝。共享结果定义不得引用可选模组类；FTB 的普通输入/结果 Codec 即使未安装 FTB 也能读取历史卡片，实际模块仍只在检测到 FTB 后注册，点击动作仍由服务端模块权限及引用校验约束。

网络、历史和嵌入玩家存档的结果统一使用 `AgentResult.CODEC`，文本边界通过 `CodecIO.parse/write` 处理。项目尚未发布，已移除旧卡片形状、旧动作结构、旧事件授权字段和写回执格式的兼容分支；不提供历史开发存档迁移。结果类型 ID 和当前格式自身的任务恢复/写入去重机制保留。

游戏访问继续通过受限主线程队列，禁止在主线程等待模型或工具 Future。扩展模块在 `routing()` 中声明 `ModuleRouting<I>`，由同一次模块注册自动建立指令与 `RoutingProfile`，参见 [轻量本地意图路由](semantic-routing.md)。

工具声明是否写入、是否幂等、是否允许事件主体调用。事件专用模块不进入聊天目录。可恢复写步骤使用稳定的 `stepId`；恢复重复执行模块入口时，已完成的写步骤按回执跳过。不要在工具分派之外产生隐藏副作用、调用模型或运行无限循环，Java 接口不能强制隔离恶意扩展。

## 独立策略

`AgentSettings.policies` 按模块 ID 覆盖整个 `ExecutionPolicy` 对象。路由单独用 `routingPolicy`，已消耗的模型调用与 tokens 转入被选模块；模块总期限从最初收到请求计时。

| 字段 | 含义 / 安全范围 |
| --- | --- |
| `modelCalls` | 实际模型尝试数，0..16 |
| `toolCalls` | 工具尝试数，0..64 |
| `steps` | 模型/工具总步骤尝试，1..128 |
| `writeSteps` | 写工具尝试，0..16 |
| `inputBytes` | 每次提示词+输入的 UTF-8 字节上限，1024..32768 |
| `outputTokens` | 每次最大输出，128..4096 |
| `totalTokens` | 全任务保守输入字节估计+输出预留及实际用量差额，1024..131072 |
| `ttlMillis` | 从接受时起的任务期限，1000..600000 |
| `modelTimeoutMillis` | 单次模型期限，不超过任务期限 |

首批快速模块通常 0..2 次模型调用、14.5 秒，成就模块默认 5 分钟且确定性路径不调用模型。自定义模块可在自己的执行逻辑中做更多有界决策，不会改变快速模块策略。配置示例见 [examples/agent](../examples/agent)。

当前使用固定有界线程池：4 个交互工作者/32 等待槽、2 个事件工作者/64 槽、2 个维护工作者/32 槽；同玩家一个活动交互任务。模型并发还受全服信号量限制，队列容量与工作者数暂未配置化。平台主线程队列每 tick 尝试最多 16 项、2ms 调度预算，单项 API 无法被抢占；大型查询仍需观察并进一步拆片。

## 持久化与恢复

存储位于 `<存档>/system-agent/<UUID>.json`。不同世界使用各自目录，原型只支持单进程本地文件系统：写临时文件、`force(true)` 后原子替换。文件上限 4MiB；文件系统不支持原子替换时拒绝写，不退化为不安全覆盖。没有 SQLite 或多服务器共享写入。

长期 AI / TTS / ASR 用量独立使用服务器全局 SavedData，保存在 `<存档>/data/system/token_usage.dat`，不进入上述业务文件或玩家附件，也不随 revoke 清理。所有 AI 请求统一经过 `ModelGateway`，按 API 实际 usage 累计；客户端 TTS 报告经 `SpeechAccounting` 归并，字符和 Token 分开累计，协议 2。详情见 [AI / TTS / ASR 用量统计](token-usage.md)。

每玩家数据包含对话、独立每日唯一标记、收件箱、业务操作、事件任务及触发基线。对话清理不清除业务唯一标记。配置不进入玩家文件；密钥始终来自服务端环境变量。请用宿主机权限保护存档、临时文件和备份。

事件参数按 moduleId/input 分派，事件授权只使用任务自身保存的 ruleId。结果、语音统计和 ASR 统计按当前 Codec 结构读取，不从旧记录猜测或回退字段。

## 绑定与撤销

`PlayerData.bound` 默认 `false`。平台在鉴权后的管理员命令中调用 `engine.grant(UUID, baselines)`；只有新绑定落盘成功才发送绑定 HUD 消息，重复授予发送普通通知，不自动打开界面、不清空记录。未绑定的交互任务与事件任务在读取或创建业务状态前被拒绝。MC 适配器在绑定时建立已完成成就基线，不补发绑定前的奖励。

`engine.revoke(UUID)` 先切换该玩家的运行代次，取消活动交互、禁用事件规则并清空短期报价；在维护线程通知 `onRevoke` 监听器后，用 `AgentStore.delete(UUID)` 删除玩家 JSON 与临时文件。FTB 适配器通过监听器清理该玩家的领取引用。成功回包会关闭系统界面并清空客户端会话；失败会报告真实错误。

业务任务和平台工具必须使用 `context.store()`；平台异步工作可在提交前获取 `engine.playerStore(UUID)`。这个受限视图会在实际事务锁内核对提交时的运行代次；撤销前的工具回调、事件回执与对话结果不能重建已删除数据。`context.check()` 同时拒绝失效代次，因此撤销后的排队游戏回调也不会执行。`engine.store()` 为受信平台的原始管理接口，不应在异步业务回调中绕过视图使用；第三方 Java 扩展仍不是沙箱。

服务器同时更换该玩家的回包代次，丢弃旧交互回复；事件通知和登录初始化另检查运行代次，重复初始化使用独立标记。删除失败时原运行任务仍被取消，客户端解除旧请求等待，但不声称清理成功。撤销不回滚已经发生的 MC 背包变化、FTB 进度或外部扣款；在途外部支付也可能晚于本地撤销结束，需由管理员确认后再使用这一测试重置操作。

`grant/revoke [玩家]` 只允许 `COMMANDS_GAMEMASTER`；省略目标时使用执行者本人，显式目标必须为单个在线玩家。网络包和模型不能调用这两个管理操作。玩家数据格式仍为 1，新增布尔字段按默认值补齐。

交互任务不在崩溃后自动续推理，重新请求会校验业务回执。事件任务保存输入、模块版本、策略、累计预算和每个写步骤的稳定回执；重登后恢复 `QUEUED/RUNNING/WAITING`，原期限不刷新。删除或禁用规则后不会授权其旧任务。当前没有通用动态步骤图、租约多工作者协调、自动补偿或独立通知 outbox；恢复通过模块入口与写步骤回执实现。

成就监听使用已读取当前依赖源码确认的 `AdvancementEvent.AdvancementEarnEvent`。每玩家/规则/成就只触发一次；初次建立基线时已有成就不补奖。后续登录用当前完成状态查漏，无法还原撤销/重复历史。游戏事件与文件持久化之间仍可能漏事件，不承诺严格一次事件捕获。

默认成就模块可设置一个持久化 `featureId` 标记、记入固定奖励权益；`featureId` 当前仅为扩展模块可查询的解锁标记，没有旧版页面或数据包解锁机制。需要实际游戏行为时注册相应业务工具，不开放任意控制台命令。

## 商城与目录扩展

`agent/commerce/CommerceCatalog` 是奖励与商品目录的不可变快照；默认使用主配置的 `rewards` / `shopOffers`，通过 `engine.catalog(snapshot)` 发布替代目录。MC 使用已核对依赖源码的 `AddServerReloadListenersEvent.addRetainedListener` 注册加载器；只在成功切换服务器资源后发布新快照，不使用全局跨世界目录。两个数据包目录独立覆盖，格式见 [扩展配置](datapacks.md)。

`system:shop` 分页浏览、`system:shop_quote` 生成预览、`system:shop_purchase` 处理独立确认，后者拒绝模型路由直接进入。支付内容只来自服务端签发的报价，绑定玩家与目录版本；订单按报价 ID 持久化，清空对话不删除订单。`ShopPurchaseTool` 用异步文件事务预留订单与锁定的收件箱权益，先验证存储容量，再进入受限主线程队列检查并扣除材料，最后在同一玩家事务中完成订单并解锁权益。扣款失败与结果未知分别记录；未知订单不自动重试，权益保持锁定。现有玩家文件通过字段默认初始化补上空 `purchases`，存储格式版本仍为 1。

`SystemCoinPayment` 预留 `available()` 和异步 `debit(UUID,long,transactionId)`。默认关闭。可信模组可以在 `AssistantRegistrationEvent` 中使用 `engine.coinPayment(provider)` 接入；适配器必须按交易 ID 持久化幂等扣款，`true` 表示已扣款，`false` 必须保证没有扣款，异常一律视为不确定。`available()` 不得阻塞，`debit` 从维护线程调用，不得直接使用 MC 线程 API。当前没有内置币账本或管理命令。

订单、收件箱各最多 4096 条，文件总体 4MiB；没有历史订单归档工具。默认材料校验用 `ItemStack.isSameItemSameComponents`，仅消费默认组件物品，避免误消耗特殊物品。崩溃时 MC 存档与外部文件无法统一提交，人工核对窗口仍存在。

## FTB 适配与边界

只有 `ModList.isLoaded("ftbquests")` 为真才反射加载 `FtbQuestAdapter`；该类内部直接调用 Java API，反射只用于隔离加载，业务不走 MCP 传输。先检查 `ServerQuestFile.exists()`，再访问不可空的 `getInstance()`。

查询包含已完成但仍可领奖励的任务。每页最多 8 个任务、每个任务 4 个奖励、每个自选奖励 8 个选项，同时按响应大小提前分页；更多奖励和选项由独立按钮继续读取。领取引用为随机短期令牌，绑定玩家、队伍、任务/奖励对象、完成时间、自选表版本，2 分钟失效；重启、换队或变化后重新查询。读取回执不以模型文字认定成功，自选路径校验索引并采用 FTB 的先标记后调用奖励语义。第三方奖励的实际交付不作独立保证。

`FtbQuestAdapter.install` 同时安装 `FtbQuestNotifications` 的进程级监听器，仍只有检测到可选依赖后才加载；其服务端快照按 `MinecraftServer` 隔离，停止时移除。核对当前依赖源码后使用 NeoForge 原生 `FTBQuestsEvent.QuestProgress` 的 `COMPLETED`，并监听 task/quest/chapter/file 的 started/completed 变化。相同事件也会在客户端回显，必须以 `object.getQuestFile() instanceof ServerQuestFile` 过滤，不能重复发送。

FTB 没有专门的解锁事件。按队伍保存可见且 `canStartTasks` 的任务 ID 集合，收到进度事件后标记 dirty，在服务器 tick 结束时合并比较；完成提示按事件顺序先发送，新增且未完成的任务按章节/任务顺序分别发送解锁提示。保留已完成但可推进的 ID 作为基线，避免普通重置直接冒充新解锁；每 20 tick 再扫描一次，以覆盖没有进度事件的锁定、重置与重复任务冷却变化。锁定切换、首次绑定/登录/换队、任务文件 cache clear 重载只建立基线，不批量播报已有任务。FTB 明确关闭通知的进度批次静默更新基线，隐藏章节与不可见任务不发送。

通知接收者为已完成系统绑定初始化的在线队员。`AssistantServer.notificationsEnabled` 复用服务端已完成登录基线作为绑定缓存，不在 tick 中读取玩家文件；发送前重新核对原玩家实例、连接代次、绑定运行代次及当前队伍。新加入或刚绑定的成员不接收加入前的解锁差异，离线队伍不保留积压。单队同 tick 最多保留 256 个不同完成项，客户端仍使用 32 个等待槽的限长 HUD 队列；大批量管理操作不承诺无限保留通知。消息由 `notifyEvent` 直接以 `event` 回包进入队列，没有模型请求、Agent 写任务或新奖励副作用；详情按钮继续走既有授权查询。通知没有独立持久化 outbox，不补播停机或离线期间的历史。FTB 原生 toast 不拦截。

## 当前语义增强与其余限制

本地路由已改为纯 Java 词法匹配，默认新配置 ENFORCE；已有配置的 OFF 保留。使用少量领域同义词、字词及相邻特征、TF-IDF 余弦相似度与覆盖率，没有模型运行库、外部词典或向量服务。低分、小分差、缺参数、陌生主题、复杂表达或不可用时调用配置的模型服务。profile 显式声明、模块工具声明和执行层共同限制只读直达，`RouteSource` 区分结构化、指令、本地匹配、精确缓存、LLM 和事件，自动路由不会绕过商城确认。详见 [轻量本地意图路由](semantic-routing.md) 与 [选型依据和后续计划](semantic-routing-plan.md)。

执行成功的部分 LLM 只读意图可按玩家短期精确缓存，只保存模块 ID，当前参数仍重新解析，动态数据每次查询。JDK 有界 LRU/TTL 替代 Caffeine，关闭保存不读写此缓存。语言从 Minecraft `player.clientInformation().language()` 读取，执行上下文冻结后为每次模型调用注入 `requestContext.playerLanguage`。清空记忆、偏好变化、撤销与掉线清理对应缓存作用域，旧任务不得回填。旧 ONNX 字段和阈值不再读取，也不再需要模型目录或 JVM native 参数。

本地索引只在启动时由可信 profile 和服务端补充例句建立，查询在原有交互工作线程执行，不增加 native 线程池或网络请求。路由和缓存实例绑定本次启动的词表/模块/配置版本；修改后正常重启。全量大小检查覆盖 System 内嵌资源及库，Minecraft、NeoForge 和原有 UI 依赖另计。

原设计稿是目标，不应把所有章节都当作已经实现。当前未包含：配方/整合包加工链、任意他人私有资料查询、多助手/多会话、模型摘要、通用工具自动规划循环、逐模块可配置公平权重/重试、模型供应商故障熔断、事件热重载、数据库迁移、严格一次背包交付、完整观测后端与 MCP 端点。

本轮验证仅为编译、打包与静态检查；没有实际调用模型服务、启动客户端/服务端或验证游戏内布局/领奖。
