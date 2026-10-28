# 开发与架构

## 当前版本

ASR 使用无 MC 依赖的 `speech/asr/AsrProvider` 和 `AliyunAsrProvider`，客户端 `AssistantRecognition` 管理录音、取消及草稿填充。TTS / ASR 用量由 `LocalSpeechUsage` 按客户端账号 UUID 独立保存，不向服务端上报；服务端 SavedData 只记录 AI。当前网络协议 1。可选 SVC 录音后端与 F8 按住说话共用原有 ASR 入口，详见[语音识别](asr.md)。

语音供应商接口及客户端录音和播放分别位于 `speech.asr` 与 `speech.tts`，供应商工厂 `TtsProviders` / `AsrProviders` 位于 `speech`，协议实现位于 `speech.aliyun`；配置集中在 `config`。阿里云默认使用 `qwen3-tts-flash`，额外兼容设计音色绑定的 Qwen-Audio-TTS HTTP 协议；复用 `HttpTransport`，没有新增依赖或模型文件。详见 [语音合成与音色设计](tts.md)。

| 组件 | 配置 |
| --- | --- |
| 模组与 Agent Java toolchain / 字节码 | 25 / Java 25 |
| Gradle / ModDev | 9.2.1 / 2.0.148 |
| Minecraft / NeoForge | 26.1.2 / 26.1.2.114 |
| ApricityUI | 1.2.6 |
| Jupiter | CurseMaven 8569832 |
| Saturn | 1.0-beta.3 / CurseMaven 9349648；客户端和服务端均必装，统一 Cost、模块 DataStorage 与 Codec 工具 |
| Simple Voice Chat | 2.6.26+26.1.2 / CurseMaven 9307181；可选客户端录音，compileOnly + localRuntime，不打入发布 JAR |
| Gson | 2.13.2；主项目直接声明依赖，Minecraft 提供运行时类 |
| FTB Quests / Library / Teams | 26.1.2.8 / 26.1.2.9 / 26.1.2.4，可选 compileOnly |
| LangChain4J | 已移除；使用 JDK HTTP 网关 |
| 本地意图路由 | `lexical-tfidf-v1`，JDK + 已有 Gson；无新增第三方依赖、native 或模型文件 |
| 产物体积上限 | 完整 System JAR 不超过 10,000,000 字节，`jar` 任务强制检查 |

Agent 统一放在独立外包 `com.iafenvoy.system.agent`：根包集中引擎、执行器、执行上下文、模块/工具接口、注册表、指令与请求结果；`builtin/<功能>` 包含该功能的工作流与单行为工具，`routing`、`model`、`store`、`commerce`、`prompt` 按职责组织。外层 `module/<功能>` 组织该功能的两端指令、装配、生命周期与界面贡献，详见[功能模块化](feature-modules.md)。通用 Codec、Schema 和 JSON 工具直接归入外层 `com.iafenvoy.system.util`，配置统一位于外层 `config`。游戏生命周期、界面、网络、语音和可选模组桥接继续位于模组的对应包；所有代码共用主源码集、依赖和 Java 25 工具链，不单独发布 Agent 核心库。

```powershell
.\gradlew.bat compileJava testModClasses jar
```

所有 Agent 类直接进入 `build/libs/system-<mod_version>.jar`。FTB 查询、详情和领奖直接实现统一的类型化 Tool，没有独立的服务接口，也不经过 stdio、HTTP MCP 或模拟客户端包。可选传输适配器如需接入，也应复用同一 Tool 契约。

模型请求统一使用 OpenAI Chat Completions 格式，默认保留 DeepSeek；专用思考参数按提供商隔离。配置与消息边界见 [模型接口与 DeepSeek 兼容](model-api.md)。

## 目录与职责

| 路径 | 职责 |
| --- | --- |
| `agent` | 引擎与执行器、预算和授权上下文、Module/Tool 契约、每服务实例的模块/工具/指令索引、请求与结果 |
| `registry` | 系统定义、Saturn action/condition 与存储类型、按键、声音、两端模块、协议/载荷类型、卡片节点的注册表及 DeferredRegister 注册项，以及语音供应商工厂 Map |
| `module/<功能>` | 功能装配、平台指令、玩家生命周期、客户端页面/卡片/通知/口播贡献 |
| `module` | 功能定义、装配、ModuleManager、玩家权限快照、模块存储声明和生命周期 |
| `module/client` | 客户端功能契约、ClientModuleBindings 贡献装配、指令、三分类动态导航、工作台及类型化卡片映射 |
| `agent/builtin` | 按功能分包的工作流、单行为工具和签到模型决策结构 |
| `agent/builtin/input、agent/builtin/output` | 内置模块/工具参数及查询结果，一类一文件 |
| `agent/builtin/tool` | 共享工具类型键；具体实现位于 `builtin/<功能>` |
| `agent/request、agent/response` | 独立请求/响应实现及各自的 MapCodec，注册声明位于 SystemRequestTypes / SystemResponseTypes |
| `agent/routing` | 混合路由、本地匹配与缓存、语义声明、路由结果及诊断 |
| `agent/model` | 模型接口、统一请求与计量入口、OpenAI 兼容协议及报文 |
| `search` | 独立 Search / Fetch 接口、DeepSeek 协议客户端和类型化结果，尚未注册为 Agent 能力 |
| `agent/store` | 存储接口、文件事务存储、玩家业务记录与对话投影 |
| `agent/commerce、agent/prompt` | 商城目录、Saturn Cost 账单、报价与订单；提示词快照与占位符模板 |
| `config` | Agent/路由配置，以及模组客户端、语音和计费配置 |
| `command、command/client` | 两端指令装配入口与补全桥接；具体指令由功能模块提供 |
| `util、util/codec` | 类型处理 Map、Codec/Schema 工具、共享 HTTP/SSE；配置目录由 ConfigInstance 提供 |
| `assistant、data` | 服务端组装、绑定与玩家会话、请求分派、主线程队列及数据包重载 |
| `assistant/usage、data/usage` | 用量服务、独立 SavedData、AI/TTS/ASR 归并与不可变快照，不随 revoke 删除 |
| `assistant、screen/gui、screen` | 客户端不可变会话状态、导航及副作用、助手界面、设置、用量与通知队列 |
| `screen/card` | CardNode 根接口 / dispatch Codec、平铺 Context、占位符与集合绑定、资源重载和通知快照 |
| `screen/card/node` | 独立节点实现，各自提供 MapCodec、默认 CSS、render 与 text；文字及容器派生类复用实现 |
| `speech/aliyun、speech/tts、speech/asr` | 语音接口及供应商协议、报文、流式播放会话、缓存与可取消录音 |
| `compat/ftb、compat/voicechat` | 可选 FTB 工具/事件和 SVC 收音，检测后才加载相关实现 |
| `event、network、network/payload` | 扩展/按键事件、协议 1 注册、Handler 与有界载荷 |

上表路径均相对于 `src/main/java/com/iafenvoy/system/`。Agent 与游戏接入代码处于同一主项目；模块、工具和服务接口仍保留职责划分，工具使用稳定逻辑 ID，方便后续扩展业务。

注册项通过对应注册类的 `public static final` 字段公开，例如 `SystemKeyMappings.TOGGLE_INTERFACE`、`SystemSoundEvents.NOTIFICATION`、`SystemEntityActions.ADD_MODULE` 和 `SystemServerModules.SIGN_IN`。注册类使用类级别 `@SuppressWarnings("unused")`。`SystemRegistries` 的正式内置注册表在两端统一创建，通过 `NewRegistryEvent` 加入原版根注册表，由对应的 DeferredRegister 注册条目，再由 NeoForge 冻结；完整 ID、字段与扩展入口见[注册表速查](registries.md)。模块由 `SystemServerModules` / `SystemClientModules` 注册，接口分别为 `ServerModule` 和 `ClientModule`，默认 `id()` 从注册表取得。客户端实现的条目由仅在物理客户端加载的 `SystemClientMod` 绑定，专用服务端保留对应空表；公共协议与卡片载荷在两端登记。客户端贡献由 `ClientModuleBindings.get()` 从冻结注册表装配。按键事件处理、网络、指令、资源重载监听器保持原有位置，Agent 内部按实例管理的工作流、工具与指令索引继续位于独立的 `agent` 包。

`AssistantServer` 负责组装引擎、安装模块、转发游戏事件及资源刷新；`AssistantPlayers` 管理绑定与连接，ModuleManager 根据 ServerModule.runtime 创建 PlayerModule 并统一管理模块会话、事件、tick 和关闭；成就基线与派发由成就模块管理，`AssistantRequests` 管理类型化请求分派与回复，`AssistantUsage` 管理用量查询及计量队列。命令、网络和可选桥接通过 `players()`、`requests()`、`usage()` 调用对应服务；异步回复仍核对连接代次，模型计量仍由服务端主线程写入 SavedData。

`AssistantClientState` 统一保存对话、待响应请求、服务端偏好与能力、当前交互结果，每次更新生成不可变快照。`AssistantClient` 只持有一个状态引用并处理导航、网络和语音等副作用；登出或成功撤销时替换为 `AssistantClientState.EMPTY`。界面刷新版本号独立递增，不随状态清空归零。

## Codec 边界

`data.SystemDefinition` 使用 `RecordCodecBuilder` 保存名称/介绍翻译键及模块定义；`ModuleDefinition` 支持 ID 字符串或含 `id/condition` 的对象，条件使用 Saturn EntityCondition。`registry.SystemDefinitions` 在 mod bus 的 `DataPackRegistryEvent.NewRegistry` 注册 `system:system`，加载与网络同步共用 `SystemDefinition.CODEC`；数据路径为 `data/<namespace>/system/system/<path>.json`。服务端通过 `SystemDefinitions.current(server.registryAccess())` 读取当前 `system:default`，客户端通过 `AssistantClient.system()` 从当前连接读取同步快照；未连接或缺少默认条目时使用内置元数据，不跨世界缓存。模块 ID 不触发可选类加载；`module.ModuleManager` 管理运行时启停与 tick，内部 ModuleAccess 在主线程计算条件和手动覆盖，完成存储初始化后向 Agent 发布不可变可用模块快照。手动覆盖由 `ModuleAccessDataStorage` 保存于玩家独立文件的 `moduleData` 列表，成功撤销/重置会随玩家文件一起删除；用量存档独立保留。该原生注册表随世界加载，不属于 `/reload` 的 retained listener；完整格式与资源包翻译见[系统定义](datapacks.md#系统定义)。

配置类各自声明 `FILE_NAME`、`CODEC` 和 `ConfigInstance<T> INSTANCE = ConfigInstance.of(...)`。容器首次初始化时从 `/defaults/<文件名>` 复制缺失的默认文件，再通过 Codec 读取；已有文件不覆盖，加载失败时记录错误并在内存中使用默认值。读取使用 `INSTANCE.get()`，提交不可变新值使用 `INSTANCE.set(next)`，实际变化由容器自动调用 `markDirty()`。若未来使用可变配置并原地修改，则必须显式调用 `INSTANCE.markDirty()`，不能绕过变更标记。

`ConfigReloader` 在资源重载时调用 `reloadAll()`，客户端/服务端 tick 与关闭事件调用 `saveAll()`，只保存有标记的配置。检查标记、保存及加载使用同一实例锁；保存失败恢复标记以便重试，重载前先保存已提交的改动。所有生效配置从对应 `config.file.XxxConfig.INSTANCE.get()` 直接读取；引擎、路由、模型客户端和成就桥接不缓存配置对象，构造函数也不传递全局配置，删除 `settings()` 转发链。

助手输入框的设置指令为 `/settings`，执行与补全共享各 `ClientModule` 贡献的本地指令表；游戏聊天栏通过客户端 `RegisterClientCommandsEvent` 注册 `/system settings`，复用同一设置入口，不发送服务端命令或模型请求，并与服务端 `/system` 的其他子命令合并补全。聊天命令打开设置后返回游戏，助手内打开则保留原页面与草稿。设置控件在 input/change/开关操作时提交本页配置，本地配置先 `INSTANCE.set(next)` 标记修改，再立即 `save()`；服务端偏好沿用请求及确认流程。TTS / ASR 允许先保存不完整凭据，完整参数在试听、合成或开始识别时校验；非法音量保留原值并提示错误，其他设置仍保存。语音页没有 JSON 导入、重新加载或手动保存按钮。TTS 队列只存文本和会话，启动播报时读取配置；模型与语音请求发出后保留该次调用的报文、供应商和计量信息，不因配置变化改写在途请求。模型连接地址、鉴权和请求体在配置实例锁内一起读取，网络请求在锁外执行。路由阈值、缓存容量和新条目 TTL 按当前配置读取；已生成的词法索引和模型并发信号量仍在启动时构建，修改索引例句、索引语言或并发容量后需重启。

`ConfigInstance.getConfigFolder()` 提供所有配置共用的根目录，配置实例和 TTS 缓存分别在其下拼接文件名及固定 `tts-cache` 子目录，不再维护额外的 `SystemConfig` 路径工具。

`AgentConfig` 只保留执行预算、并发、每日模型限额和时区；`ModelConfig` 管模型连接和提供商参数，`SemanticConfig` 管本地路由，`AdvancementConfig` 管成就绑定。对应文件为 `system-agent.json`、`system-model.json`、`system-semantic.json`、`system-advancements.json`，均位于 `config/system/`。奖励、商品和奖池完全由数据包定义，不参与配置解析，不提供旧聚合配置自动迁移。

`CodecIO` 统一限长 JSON 解析及 Codec 编解码；HTTP 响应可通过 `Document` 一次解析后分别解码用量与正文，保证正文无效仍记录已计费用量。存档和恢复日志使用 `RecordCodecBuilder`，业务账本的可选字段通过严格的 `optionalFieldOf` 读取；可丢弃的 `Conversation.requests` 展示/请求回放缓存使用 `lenientOptionalFieldOf`，结果载荷过期或格式无效时仅将该缓存回退为空，不影响绑定、奖励、订单、写步骤和其他玩家数据，也不迁移旧载荷。配置使用不可变 record、`RecordCodecBuilder` 和 record getter，按字段以 `lenientOptionalFieldOf` 读取。范围与字段语义在子 Codec 校验，枚举及嵌套构造异常通过 `ConfigCodecs` 转为解析错误。配置中缺失、null、类型错误、无效枚举、越界值或嵌套 Codec 构造异常只回退该字段，其他有效配置保留；配置集合返回不可变快照，玩家文件仍使用 FileAgentStore 的原有 force/原子替换事务。

`AgentRequest` 表达聊天、模块调用、控制请求和偏好，通过 `SystemRequestTypes` 向 `SystemRegistries.AGENT_REQUEST_TYPE` 登记 `MapCodec`；对象的 `codec()` 返回注册实例，根 Codec 按 `type` 分派。`AgentResponse` 使用对应的正式注册表分派，已删除旧 `DispatchRegistry`；任务查询按 Tool 声明固定返回单个数据对象或 List，不使用多态结果注册表。两端载荷通过原版 `ByteBufCodecs.fromCodecWithRegistries` 复用 Codec，直接传递类型对象；JSON 只用于文本边界。模型路由返回值按 `capabilityId` 从本次可选模块的 Codec Map 分派，签到仅接收 SignInDecision，发送的玩家投影不包含其他在线玩家。ConversationMemory 使用有界、允许字段组成的对象投影。

固定业务数据、配置、存档、模型与语音报文使用 Codec。JSON 只保留在动态 JSON Schema 与通用界面格式化边界；工具写入回执使用原版 Dynamic/PASSTHROUGH 保存暂不解释的结果，回放时由对应工具结果 Codec 解码，不在业务中按键取值。

配置容错只适用于已经解析成对象的合法 JSON；语法损坏、重复键、超出文件大小限制或文件不可读时记录错误，容器在内存中回退该配置的默认值，不自动覆盖坏文件。列表、映射及严格业务参数中的错误可使所属配置字段整体回退，其他顶层设置继续保留；不为奖励、事件和付款引用编造 ID。收费表无法确定单价的条目跳过并标为未估价，其余有效条目继续使用；整个收费表加载失败时使用空表，继续记用量但不估价。

## 网络约定

主注册类只负责载荷及处理器注册。C2S 由服务端 Handler 在主线程验证 UUID、从连接读取玩家并分派到服务端能力；S2C 由客户端 Handler 解码并分派到客户端状态或界面。客户端处理器通过 NeoForge 的客户端注册事件绑定，专用服务端不解析客户端界面类。连接代次、迟到回包防护、结果长度限制与用量权限检查保持原有规则。

| 载荷类 | 网络 ID |
| --- | --- |
| `AssistantRequestC2S` | `system:assistant_request_c2s` |
| `AssistantResponseS2C` | `system:assistant_response_s2c` |
| `TokenUsageRequestC2S` | `system:token_usage_request_c2s` |
| `TokenUsageResponseS2C` | `system:token_usage_response_s2c` |

当前协议版本为 **1**，客户端与服务端需一起更新。C2S 请求为请求 UUID 加 `AgentRequest`；S2C 通过 `AgentResponse` 的类型区分回复、HUD 通知、界面导航和绑定变更，操作不再占用请求 ID。导航可以携带关联请求 UUID，客户端先清除该请求的等待状态，再请求界面状态；用量载荷以独立 `open` 标志表示打开页面。无效或未知类型由载荷的 Codec 在网络解码阶段拒绝。当前项目未发布，不保留旧网络字段兼容。新增载荷时在 `network/payload` 定义带方向后缀的类型，在对应 Handler 增加处理方法，再由 `NetworkManager` 注册。

## 扩展模块

实现 `AgentModule<I>`，提供已注册的 `TypedPayload.Type<I>`、描述信息和 `List<ToolRegistry.Tool<?, ?>> tools()`，在 `execute` 中处理不可变参数。内置模块继承 `BuiltinModule<I>`：构造时传入共享输入类型键 `TypedPayload.Type<I>`、`SchemaMapCodec<I>` 和工具列表，描述中的输入 schema 从 Codec 获取，工具白名单从列表生成，避免重复声明。模块是受信 Java 代码，Codec 不替代业务授权，也不是同 JVM 沙箱。

模块 ID 使用原版 `Identifier`：`Descriptor.id`、输入类型键 `TypedPayload.Type.id` 及模块注册表的键均为该类型。先通过 DeferredRegister 向 `SystemResourceKeys.ACTION_TYPE` 登记共享类型键，内置键由 `SystemActionTypes` 提供。模块的 `inputType()`、按钮、指令与路由必须复用这个键；引擎拒绝 ID 不一致或未注册的类型键。`BuiltinModule` 从传入类型键取得 Identifier。配置和网络仍编码为 `namespace:path` 字符串。

每个 Tool 只负责一个行为，`ToolRegistry.Type<I, O>` 保存稳定 ID、参数 `MapCodec<I>` 和结果 `Codec<O>`，`Tool<I, O>` 保存写入、幂等、事件权限和单一 Handler。模块直接调用 `context.tool(type, stepId, typedInput)` 获得类型化结果；一个流程需要多个行为时使用模块的 `List.of(toolA, toolB)` 组合。同一工具被多个模块使用时共享同一实例，重复的不同实现或同 ID 不同类型键会被拒绝。

功能级扩展用标准 `DeferredRegister<ServerModule>` 注册到 `SystemResourceKeys.SERVER_MODULE`；客户端以相同方式注册到 `SystemResourceKeys.CLIENT_MODULE`，注册入口限定 `Dist.CLIENT`。完整示例见[功能模块化](feature-modules.md)。只有工作流的扩展也可在原有 `AssistantRegistrationEvent` 中调用 `engine.registerModule(module)`：引擎的工作流目录核对描述白名单与工具列表，检查 ID、指令别名和工具实例冲突，再统一收集工具、指令、语义 profile 和快捷入口。不再提供先注册工具、再注册模块的两阶段入口。FTB 也使用此路径，查询、详情、普通领奖、新手礼包领取和明确任务提交各有独立 Tool 及对应模块，不需要专用服务接口。

`TypedPayload.ACTION_CODEC` 通过正式 `SystemRegistries.ACTION_TYPE` 的 `byNameCodec().dispatchMap(...)` 按 `moduleId` 选择共享参数 Codec；调用封装为 `{"moduleId":"system:item_query","input":{"query":"stone"}}`。`ModuleRegistry.call` 根据同一类型键定位当前服务器已安装模块、校验参数并生成可执行调用，不再维护另一套输入 dispatch Codec。模型目录和模型返回值解码都由当前模块注册表约束；未安装模块、事件专用模块、`structuredOnly()` 模块不会成为模型可选能力。网络、配置和事件存档在边界使用 Codec，内部直接传 `TypedPayload<?>`。

`AgentCodecs.strict` 在原版 Codec 基础上额外拒绝未知键和显式 null；精确整数 Codec 拒绝小数和溢出值，避免原版 `Codec.INT` 的截断行为影响索引。业务参数的缺省值由严格 `optionalFieldOf` 提供，类型错误不静默回退；配置字段统一使用 `lenientOptionalFieldOf` 回退默认值，两者互不影响。Codec 解析失败不接受部分结果。写工具回放要求指纹完全一致，再由对应工具的结果 Codec 恢复对象；不匹配时拒绝执行，不归一旧格式，也不重新执行已完成写操作。

### 调度、路由与执行职责

`ModuleRouting.explicitOnly()` 排除模型与自动语义选择，同时允许模块注册自己的明确指令；`structuredOnly()` 保留为相同的自动选择禁用声明。执行入口允许 `STRUCTURED` 和注册表解析得到的 `COMMAND`，自动来源仍需 `modelSelectable` 授权。手动任务提交采用这一方式，不通过自然语言推断自动消耗物品、经验或勾选任务。

- `AgentEngine` 提供统一入口，持有服务和玩家生命周期，并通过同一个 `execute` 校验模块权限、选择策略、执行流程、报告部分成功。
- `InteractiveExecutor` 管理每玩家一个交互请求、取消代次、请求指纹与结果回放；入队、初始化和写回都检查取消代次。
- `EventExecutor` 管理持久事件、规则授权、去重和恢复；恢复使用已保存策略、预算与工具回执。
- `ConversationService` 负责历史读取、清空和偏好修改，不重复实现执行或队列逻辑。
- `QuestNarration` 管理只读任务口播、短期同玩家并发约束、模型整理片段缓存及同批合并，不占用主聊天、不创建对话记录。`QuestBackgrounds` 按任务唯一 ID、语言和内容指纹持久化背景分类与文案（含无背景），存档根目录文件为 `system-quest-backgrounds.json`，不含玩家进度。FTB 的 `FtbQuestSpeech` 只提供主线程定义快照；命中缓存后仍复核当前权限，格式化和模型预算及客户端 session 边界见[语音合成](tts.md)。
- `TaskExecutor` 统一有界调度、队列满错误、关闭和 Future 收尾；关闭后未执行的任务也得到 `CANCELLED`。
- `HybridRouter` 只编排结构化调用 → 指令注册表 → 本地语义/缓存 → 一次模型回退。每个模块通过 `ModuleRouting<I>` 提供自己的指令、例句、参数提取和可选只读快捷入口。
- `ExecutionContext` 是执行权限、预算、模型与工具调用的共同入口。Module 组合流程，Tool 每次只执行一个行为。

`engine.start()` 校验模块策略和事件引用，随后冻结模块、工具、指令和语义注册信息。扩展必须在 `AssistantRegistrationEvent` 中完成注册；当前不支持运行中增删模块。管理员生命周期指令与客户端界面操作各留在对应端，Agent 能力通过统一注册表调用。游戏聊天的 `/system <instruction>` 是通用转发入口，补全实时读取 Agent 注册表；助手输入框通过状态快照获得同一组指令，不需要为扩展修改客户端指令表。详细扩展示例见 [Agent 运行结构](agent-runtime.md)。

### 内置输入与输出

`agent.builtin.input` 与 `agent.builtin.output` 中每个类型独立成文件，直接导入后使用 `QuestDetailInput`、`PlayerSnapshotOutput` 等名称，不再通过聚合外类引用。输入使用带校验和 Schema 元数据的 MapCodec，输出使用 Codec；任务列表和名称搜索返回 `List<QuestBriefOutput>`，详情返回 `QuestOutput`，礼包返回 `StarterGiftOutput`。奖励入账参数 `RewardCreditInput` 归入输入包，物品直接使用原版 `ItemStackTemplate.CODEC`，奖励使用 `PlayerData.Reward.CODEC`；`Reward.ITEMS_CODEC` 统一限制产出为 1～36 项。重载阶段不创建物品堆，运行时在游戏线程调用模板的 `create()`。

### 从 Codec 定义生成输入 schema

`SchemaCodecs.record((i, f) -> ...)` 继续使用原版 `RecordCodecBuilder`，在声明字段时同时记录 JSON Schema 元数据，返回可直接用于原版 dispatch 的 `SchemaMapCodec`。例如：

```java
public record Search(String query, int offset) {
    public static final SchemaMapCodec<Search> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.STRING).forGetter(Search::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(Search::offset)
    ).apply(i, Search::new));
}
```

`Search.CODEC.schema()` 返回包含 `type`、`properties`、`required`、`default`、非负数约束和 `additionalProperties: false` 的 schema。`SchemaCodecs.STRING`、`NON_NEGATIVE_INT`、`BOOL` 提供常用类型；确有业务依据时可用 `string(min, max)`、`intRange(min, max)` 指定范围。`matching` 同时提供正则校验与 `pattern`，`object` 和 `Value.listOf()` 支持嵌套对象与数组，`described` 为字段或对象补充说明。空参数使用 `SchemaCodecs.unit`。schema 只在定义时组装，不增加网络调用或依赖；路由器沿用模块描述入口读取生成结果，模块构造函数不再接收手写 schema。

这不是对任意原版 Codec 的反射反编译：原版没有通用结构访问接口，`xmap` 和 `validate` 也可能包含任意 Java 逻辑。`SchemaMapCodec` 的 `xmap`、`flatXmap`、`validate` 和生命周期包装保留已有结构元数据，但不会自动翻译额外谓词。例如任务选择器三选一仍在 Codec 中校验，并通过 `described` 向模型说明。接入外部 Codec 时可显式构造 `SchemaCodecs.Value(codec, schema)`，或用 `SchemaMapCodec(codec, schema)` 包装外部对象 Codec；该显式 schema 的准确性由扩展负责。生成结果用于提示模型，执行时仍以 Codec 和业务授权为准，不启用新的模型侧 structured-output 协议。

### 结果卡片与动作

`AgentResult`、`Card` 和 `Action` 各自提供 `CODEC`。`Card.data` 直接持有 `CardData`，各数据实现提供 `MapCodec` 和模板标识，`codec()` 返回已登记的 MapCodec，`template()` 返回布局资源 ID，`buildContext(builder, formatting)` 填充自身展示参数。公共 CardContext 和 CardFormatting 不引用客户端 GUI，翻译由调用方传入的文字解析器提供。根 `CardData.CODEC` 从正式注册表 `CARD_DATA_TYPE` 按 `type` 分派；`Action.call` 仍为 `TypedPayload<?>`，其 `ACTION_CODEC` 从 `ACTION_TYPE` 按 `moduleId` 选择输入 Codec。`titleKey` 仅用于本地化显示，不参与结果的类型判断。

`AgentResult` 继承 Saturn `ResultWithData<List<Card>>`，成功判断统一使用 `isSuccess()` / `isFailure()`，卡片由父类保存，`cards()` 是展示访问器。Agent 继续保留文案、静默标志与 `status`；`PARTIALLY_SUCCEEDED` 表示存在未完成操作，仍属于失败，不能写入成功路由缓存。失败通过文案键适配 `Failure`，网络与存档继续使用原有 Codec 字段，不序列化 Failure 实现类。`partial()` 与 `withPresentation(...)` 分别用于部分成功标记和展示副本，值相等语义保留。

```java
AgentResult result = AgentResult.data("system.result.player", snapshot);
AgentResult.Action deliver = new AgentResult.Action("system.action.deliver", SystemActionTypes.DELIVER_ACTION.get(),
        new EntitlementInput(entitlementId));
```

卡片数据编码为 `{"type":"system:player","name":"...",...}`，没有 `value` 包装；动作编码为 `{"labelKey":"system.action.deliver","call":{"moduleId":"system:deliver","input":{"entitlementId":"..."}}}`。新业务直接创建输入/结果对象，不再为卡片或按钮手工拼接 `JsonObject`。客户端任务行、奖励选项、指令补全、设置状态和口播通过类型访问数据；`TypedPayload.json()` 仅用于模块动作的输入预算和指纹等需要文本的边界，C2S 直接传对象。历史压缩继续只提取允许字段及有限领奖引用，不把整个卡片序列化后塞进模型上下文。

客户端先由 `CardPresentation.context(...)` 预填动作，调用 `CardData.buildContext(builder, formatting)` 填充类型参数，再补充通用标题、模式和按钮回调并构建不可变 `CardContext`，再由 `CardPresentation.of(data, context)` 按 `CardData.template()` 直接读取 `assets/<namespace>/cards/<path>.json`。没有额外渲染映射表；资源 ID 可与数据类型 ID 分别命名，缺失模板沿用通用回退展示。`CardNode` 根接口通过 Codec 按 `type` 分派，`SystemRegistries.CARD_NODE_TYPE` 通过正式注册表绑定节点 ID 与 MapCodec，JSON 节点类型使用 `system:text` 等完整 ID；`screen/card/node` 中的独立实现负责 codec/render/text/css，`CardTemplate.Node` 保留共用条件及样式；文字、容器、横排/竖排、分割线、物品与动作区可任意组合。JSON 的 style 接受字符串或字符串数组，Codec 将数组拼接为空格分隔的 AUI 类名。`ClientCardBindings` 仅按需登记客户端行为，Registration 链式组合任务引用、状态、刷新与消息，不负责上下文构建或渲染。图形、纯文本和按钮标签分别直接调用 `CardPresentation.of/format/label`；参数由数据实现构建，不再登记构建处理器。消息按键统一返回 ClientMessage 的显示和播报文字，不再维护独立摘要/口播表。参数使用 `CardContext.Builder` 构建不可变 `CardContext` 后再绑定模板；整张卡片共用状态、对象参数和回调三张表，重复条目使用带下标的平铺键，`items: "{{result_items}}"` 直接绑定物品对象，不经 JSON 字符串转换。完整/简约界面和 HUD 主体共用布局树；HUD 确认、关闭、领取和任务行继续由代码生成，模板动作节点在通知中忽略。资源重载刷新完整/简约界面，通知保留入队时的模板及数据快照。内置 22 种卡片均有默认资源，格式及类型扩展见[客户端 JSON 卡片](cards.md)。

新增结果类型实现 `CardData`，通过 DeferredRegister 向 `SystemResourceKeys.CARD_DATA_TYPE` 登记自身 `MapCodec`；业务直接创建结果对象，不再经 Holder 包装。内置条目由 `SystemCardDataTypes` 公开。新增模块按钮参数仍向 `SystemResourceKeys.ACTION_TYPE` 登记 `TypedPayload.Type<?>`，内置条目由 `SystemActionTypes` 公开，复用模块输入 Codec。请求、响应及客户端卡片节点也使用正式注册表；语音供应商使用普通工厂 Map，声明与查询入口见[注册表速查](registries.md)。注册必须在两端模组加载期间完成，类型 ID 唯一，未知 ID 或未注册的 Codec / 类型键在编解码时拒绝。共享结果定义不得引用可选模组类；FTB 的普通输入/结果 Codec 即使未安装 FTB 也能读取历史卡片，实际模块仍只在检测到 FTB 后注册，点击动作仍由服务端模块权限及引用校验约束。

网络、历史和嵌入玩家存档的结果统一使用 `AgentResult.CODEC`，文本边界通过 `CodecIO.parse/write` 处理。项目尚未发布，已移除旧卡片形状、旧动作结构、旧事件授权字段和写回执格式的兼容分支；不提供历史开发存档迁移。结果类型 ID 和当前格式自身的任务恢复/写入去重机制保留。

游戏访问继续通过受限主线程队列，禁止在主线程等待模型或工具 Future。扩展模块在 `routing()` 中声明 `ModuleRouting<I>`，由同一次模块注册自动建立指令与 `RoutingProfile`，参见 [轻量本地意图路由](semantic-routing.md)。

工具声明是否写入、是否幂等、是否允许事件主体调用。事件专用模块不进入聊天目录。可恢复写步骤使用稳定的 `stepId`；恢复重复执行模块入口时，已完成的写步骤按回执跳过。不要在工具分派之外产生隐藏副作用、调用模型或运行无限循环，Java 接口不能强制隔离恶意扩展。

## 独立策略

`AgentSettings.policies()` 按模块 ID 覆盖整个 `ExecutionPolicy` 对象。路由单独用 `routingPolicy`，已消耗的模型调用与 tokens 转入被选模块；模块总期限从最初收到请求计时。

| 字段 | 含义 / 有效值 |
| --- | --- |
| `modelCalls` | 实际模型尝试数，非负整数 |
| `toolCalls` | 工具尝试数，非负整数 |
| `steps` | 模型/工具总步骤尝试，正整数 |
| `writeSteps` | 写工具尝试，非负整数 |
| `inputBytes` | 每次提示词+输入的 UTF-8 字节上限，正整数 |
| `outputTokens` | 每次最大输出，正整数 |
| `totalTokens` | 全任务保守输入字节估计+输出预留及实际用量差额，正整数 |
| `ttlMillis` | 从接受时起的任务期限，正整数 |
| `modelTimeoutMillis` | 单次模型期限，正整数且不超过任务期限 |

预算由模块默认策略或管理员配置决定，不再给预算值另设任意硬上限。配置在 Codec 边界检查有效值，引擎和平台启动时不重复遍历校验相同字段；权限、重复 ID、业务引用、付款状态和执行预算仍在相应边界检查。配置字段继续使用 `lenientOptionalFieldOf`，错误字段回退默认值。

首批快速模块通常 0..2 次模型调用、14.5 秒，成就模块默认 5 分钟且确定性路径不调用模型。自定义模块可在自己的执行逻辑中做更多有界决策，不会改变快速模块策略。配置示例见 [examples/agent](../examples/agent)。

当前使用固定有界线程池：4 个交互工作者/32 等待槽、2 个事件工作者/64 槽、2 个维护工作者/32 槽；同玩家一个活动交互任务。模型并发还受全服信号量限制，队列容量与工作者数暂未配置化。平台主线程队列每 tick 尝试最多 16 项、2ms 调度预算，单项 API 无法被抢占；大型查询仍需观察并进一步拆片。

## 持久化与恢复

存储位于 `<存档>/system-agent/<UUID>.json`。不同世界使用各自目录，原型只支持单进程本地文件系统：写临时文件、`force(true)` 后原子替换。文件上限 4MiB；文件系统不支持原子替换时拒绝写，不退化为不安全覆盖。没有 SQLite 或多服务器共享写入。

玩家文件按 UTF-8 读取，读取器在 `load` 返回前通过 try-with-resources 关闭，避免 Windows 下未释放的读取句柄阻止后续原子替换。读写、删除和目录枚举失败会在服务端记录操作、路径与异常原因，并通过带 cause 的 `AgentException` 保留错误链；客户端仍只接收稳定的 `STORAGE_UNAVAILABLE` 错误码，不发送内部路径或堆栈。

长期 AI 用量使用服务器全局 SavedData，保存在 `<存档>/data/system/token_usage.dat`，不进入上述业务文件或玩家附件，也不随 revoke 清理。所有 AI 请求统一经过 `ModelGateway`，按 API 实际 usage 累计。TTS / ASR 由客户端使用私人凭据请求，用量按客户端账号 UUID 只存本机 `config/system/speech-usage/<UUID>.json`，费用读取客户端 `SpeechPricingConfig`。服务端没有语音上报消息、存储字段或快照字段；管理员全服视图只有 AI，个人语音面板跨服务器累计，协议保持 1。详情见[用量统计](token-usage.md)。

每玩家文件包含对话、每日签到、收件箱、业务操作回执、事件任务、成就状态及模块覆盖，各业务状态分属独立 DataStorage。对话清理不清除业务唯一标记。配置不进入玩家文件；密钥始终来自服务端环境变量。请用宿主机权限保护存档、临时文件和备份。

`PlayerData` 顶层 `RecordCodecBuilder` 只保存版本、绑定与偏好、`Conversation`、`DataStorageHolder<Identifier>` 和 `ModelQuota` 共 7 项。签到、收件箱、事件/操作回执、商城、成就状态和模块覆盖分别实现 Saturn `DataStorage`，由 ServerModule.storages 声明，内置 Codec 由 `SystemDataStorages` 注册，扩展 Codec 独立注册到 Saturn DATA_STORAGE_TYPE，并由 `DataStorageHolder.codec(Identifier.CODEC)` 分派；扩展模块可用自己的 DataStorage 避免继续扩大共享玩家记录。访问入口为 `ModuleData.get(data, SomeSystemModule.STORAGE)`，底层按 Holder 键和类型调用 `get` / `set`，与 DataStorage Codec 类型 ID 分开；复制必须隔离可变集合和其中的可变记录。玩家文件仍由 `AgentStore.update` 整体事务化写入，不属于玩家附件。对话历史和请求回放由 `Conversation` 管理，`Conversation.clear()` 统一清理并推进记忆代次；内部交付、事件、步骤回执和订单使用 `RecordCodecBuilder`。

事件参数按 moduleId/input 分派，事件授权只使用任务自身保存的 ruleId。结果、语音统计和 ASR 统计按当前 Codec 结构读取，不从旧记录猜测或回退字段。

## 绑定与撤销

`PlayerData.bound` 默认 `false`。平台在鉴权后的管理员命令中调用 `engine.grant(UUID, baselines)`；只有新绑定落盘成功才发送绑定 HUD 消息，重复授予发送普通通知，不自动打开界面、不清空记录。未绑定的交互任务与事件任务在读取或创建业务状态前被拒绝。MC 适配器在绑定时建立已完成成就基线，不补发绑定前的奖励。

`engine.revoke(UUID)` 先切换该玩家的运行代次，取消活动交互、禁用事件规则并清空短期报价；在维护线程通知 `onRevoke` 监听器后，用 `AgentStore.delete(UUID)` 删除玩家 JSON 与临时文件。FTB 适配器通过监听器清理该玩家的领取引用。成功回包会关闭系统界面并清空客户端会话；失败会报告真实错误。

业务任务和平台工具必须使用 `context.store()`；平台异步工作可在提交前获取 `engine.playerStore(UUID)`。这个受限视图会在实际事务锁内核对提交时的运行代次；撤销前的工具回调、事件回执与对话结果不能重建已删除数据。`context.check()` 同时拒绝失效代次，因此撤销后的排队游戏回调也不会执行。`engine.store()` 为受信平台的原始管理接口，不应在异步业务回调中绕过视图使用；第三方 Java 扩展仍不是沙箱。

服务器同时更换该玩家的回包代次，丢弃旧交互回复；事件通知和登录初始化另检查运行代次，重复初始化使用独立标记。删除失败时原运行任务仍被取消，客户端解除旧请求等待，但不声称清理成功。撤销不回滚已经发生的 MC 背包变化、FTB 进度或外部扣款；在途外部支付也可能晚于本地撤销结束，需由管理员确认后再使用这一测试重置操作。

`grant/revoke/reset [玩家]` 只允许 `COMMANDS_GAMEMASTER`；省略目标时使用执行者本人，显式目标必须为单个在线玩家。网络包和模型不能调用这些管理操作。`reset` 与 `revoke` 一样直接删除玩家文件，不尝试迁移无法解析的旧格式；它额外给出旧格式提示，完成后需要再次 `grant`。玩家数据格式仍为 1，新增布尔字段按默认值补齐。

交互任务不在崩溃后自动续推理，重新请求会校验业务回执。事件任务保存输入、模块版本、策略、累计预算和每个写步骤的稳定回执；重登后恢复 `QUEUED/RUNNING/WAITING`，原期限不刷新。删除或禁用规则后不会授权其旧任务。当前没有通用动态步骤图、租约多工作者协调、自动补偿或独立通知 outbox；恢复通过模块入口与写步骤回执实现。

成就监听使用已读取当前依赖源码确认的 `AdvancementEvent.AdvancementEarnEvent`。每玩家/规则/成就只触发一次；初次建立基线时已有成就不补奖。后续登录用当前完成状态查漏，无法还原撤销/重复历史。游戏事件与文件持久化之间仍可能漏事件，不承诺严格一次事件捕获。

默认成就模块可设置一个持久化 `featureId` 标记、记入固定奖励权益；`featureId` 当前仅为扩展模块可查询的解锁标记，没有旧版页面或数据包解锁机制。需要实际游戏行为时注册相应业务工具，不开放任意控制台命令。

## 商城与目录扩展

`agent/commerce/CommerceCatalog` 是奖励、商品与奖池目录的不可变快照，只从数据包生成；默认内容也放在 JAR 内 `data/system/system/signin_rewards/`、`shop_offers/` 与 `lottery_pools/`。初始目录注入引擎，重载后通过 `engine.catalog(snapshot)` 发布新目录。MC 使用已核对依赖源码的 `AddServerReloadListenersEvent.addRetainedListener` 注册加载器；只在成功切换服务器资源后发布新快照，不使用全局跨世界目录。资源同路径按数据包优先级覆盖，不同 ID 汇总，格式见 [扩展配置](datapacks.md)。

奖励和商品 ID 使用 `Identifier`，通过 `Identifier.CODEC` 解析；签到选择、报价请求和成就奖励引用保留该类型，只在搜索或对话摘要等文本边界转换。商品与奖池直接保存 `List<Cost>`，通过 Saturn `Cost.LIST_CODEC` 按注册类型分派；目录监听器使用 NeoForge 注入的 `getRegistryLookup()` 创建 RegistryOps，支持资源 Cost 的动态注册表引用。`CommerceCatalog.Price` 仅保存求值后的物品图标和文字组件，供客户端及订单快照使用，不含可执行费用。

`system:shop` 分页浏览、`system:shop_quote` 生成预览、`system:shop_purchase` 处理独立确认，后者拒绝模型路由直接进入。支付内容只来自服务端签发的 `CommerceQuotes`，绑定玩家、操作类型与目录版本；订单按报价 ID 持久化，清空对话不删除订单。`ShopPurchaseTool` 与 `LotteryDrawTool` 各自只负责一种确认行为，通过同一 `CommerceTransactions` 预留 `CommerceOrder` 与锁定的收件箱权益，再进入受限主线程队列验证模板并扣除材料，最后在同一玩家事务中完成订单并解锁权益。扣款失败与结果未知分别记录；未知订单不自动重试，权益保持锁定。`Purchase.order` 保存操作类型、来源、价格及完整奖励快照；没有迁移旧 `Purchase.offer` 的兼容逻辑，开发存档若无法解析，使用 `/system reset [玩家]` 后重新授予。

`system:lottery` 浏览奖池、`system:lottery_preview` 分页展示奖品权重并签发确认、`system:lottery_draw` 只接受结构化确认。浏览与预览声明指令及语义 profile；实际抽奖不进入模型能力目录。`LotteryPool` 通过 JDK `ThreadLocalRandom.nextLong(totalWeight)` 选择一个奖励，结果在支付前存入订单；整个奖池的物品模板在扣款前创建验证，避免错误奖品改变成功抽奖的概率。待支付或支付未知权益不进入收件箱奖励展示，仅返回订单状态；完成后重复确认返回原回执。

奖池列表的 `LotteryPoolOutput.prizes` 通过 Codec 携带首个奖品页的公开奖励模板、权重及完整总权重，供共享物品投影显示图标和概率；列表与详情使用同一个 8 项分页常量。详情已发送独立 `LotteryPrizeOutput` 卡片，顶部摘要不重复携带奖品。预览不会生成抽奖结果、扣款或调用模型。单物品消耗统一由 `AssistantItems` 渲染，免费、资源及其他 Cost 使用文字；签到候选的物品卡片保留所属奖励包名称。

商城 / 抽奖模块共用只读 `CommercePreviewTool`，由 `CommerceTransactions.preview` 在游戏线程按目录 ID 获取可信费用并生成 `CommerceCosts.Prepared`。Cost 只定价一次，短期报价保留绑定玩家实体、支付目标及金额的 Saturn `CostPayment`；确认沿用该账单，掉线 / 重生导致实体更换时拒绝旧账单。`CostContext` 包含 `origin=system:shop` / `system:lottery` 和 `system:source`，扩展 Cost 可读取。System 不再保留单独的系统币接口或自定义物品扣款器；币可由 Saturn 资源或扩展 Cost 表达。

`Prepared` 继承 Saturn `ResultWithData<CommerceCatalog.Price>`，以 `getData()` 读取展示价格，以 `isSuccess()` 判断是否可生成报价，保留定价失败的原始 `Failure` 和 `failedResource`。失败说明在创建不可变价格快照前加入，并由 `Failure.message(failedResource)` 格式化；账单、支付玩家及扣款跟踪留在服务端，不进入客户端快照。客户端 DTO 中的 `available` 仅是上述成功状态的投影。

支付全部通过 Saturn 的 `test()` / `pay()` 执行。当前依赖会在实际扣款出错后继续其他费用并可能返回成功，因此 `CommerceCosts` 包装绑定事务，额外追踪每项真实扣款的失败与异常；一旦出现部分 / 不确定扣款，订单和权益保持 `PAYMENT_UNKNOWN`，不发奖、不自动重试。Saturn 不统一预留重复 / 重叠费用，不保证跨费用回滚，数据包应合并同一种需求。扩展 Cost 必须遵守 `prepare`、`execute(true)` 只读的契约。

订单、收件箱各最多 4096 条，文件总体 4MiB；没有历史订单归档工具。材料匹配与扣除由 Saturn Cost 执行，普通物品 ID 可能消耗带自定义组件的同类物品，范围为玩家 Inventory。订单只保存展示价格与完整奖励快照，不序列化绑定的 CostPayment 或恢复后自动扣款。崩溃时 MC 存档与外部文件无法统一提交，人工核对窗口仍存在；旧价格存档不迁移，必要时用 `/system reset [玩家]` 清理开发数据。

## FTB 适配与边界

只有 `ModList.isLoaded("ftbquests")` 为真才反射加载 `FtbQuestAdapter`；该类内部直接调用 Java API，反射只用于隔离加载，业务不走 MCP 传输。先检查 `ServerQuestFile.exists()`，再访问不可空的 `getInstance()`。

读取 Tool 各自返回固定类型，FTB 原生 `Quest` 和 `TeamData` 只在游戏线程内使用，对外返回可序列化的数据快照：

| Tool | 参数 | 固定返回类型 |
| --- | --- | --- |
| `ftb.query` | `QuestQueryInput`：关键词、是否只看可领奖 | `List<QuestBriefOutput>` |
| `ftb.find` | `QuestFindInput`：任务名或 ID，精确匹配优先 | `List<QuestBriefOutput>` |
| `ftb.detail` | `QuestDetailInput`：已知 ID 或上次列表编号、详情分页偏移 | `QuestOutput` |
| `ftb.starter_gifts` | `EmptyInput` | `StarterGiftOutput` |

`QuestDetailModule` 将名称输入先交给 `ftb.find`；无匹配或多匹配显示候选列表，单匹配再按 ID 读取详情。唯一匹配直接打开详情，不替换玩家原先看到的编号列表；ID/编号查询不存在或不可查看时返回业务错误，不切换结果类型。详情的 `starterGift` 标记由兼容层分类，Module 遇到礼包后调用共享的 `ftb.starter_gifts` 工具展示全部可领取礼包，不让详情 Tool 返回礼包对象。这些只读组合最多调用 3 次本地工具，不增加模型调用；礼包工具实例与独立礼包查询模块复用。

查询包含已完成但仍可领奖励的任务。任务列表一次返回全部匹配简表并连续编号，查询与名称候选不再携带列表分页偏移；详情保留每页 4 个奖励、每个自选奖励 8 个选项；更多奖励和选项由独立按钮继续读取。领取引用为随机短期令牌，绑定玩家、队伍、任务/奖励对象、完成时间、自选表版本，2 分钟失效；重启、换队或变化后重新查询。读取回执不以模型文字认定成功，自选路径校验索引并采用 FTB 的先标记后调用奖励语义。第三方奖励的实际交付不作独立保证。

任务列表在完整界面与通知中使用限高滚动区域，不再创建列表翻页动作。S2C 回复使用 trusted Codec 和 NeoForge 的大包拆分，不再受旧的 60000 字符回复拦截；C2S 仍使用受限解码。玩家文件容量不足时先按整轮淘汰对话及回放缓存，业务记录不参与淘汰。

`FtbQuestAdapter.install` 同时安装 `FtbQuestNotifications` 的进程级监听器，仍只有检测到可选依赖后才加载；其服务端快照按 `MinecraftServer` 隔离，停止时移除。核对当前依赖源码后使用 NeoForge 原生 `FTBQuestsEvent.QuestProgress` 的 `COMPLETED`，并监听 task/quest/chapter/file 的 started/completed 变化。相同事件也会在客户端回显，必须以 `object.getQuestFile() instanceof ServerQuestFile` 过滤，不能重复发送。

FTB 没有专门的解锁事件。按队伍保存可见且 `canStartTasks` 的任务 ID 集合，收到进度事件后标记 dirty，在服务器 tick 结束时合并比较；完成提示按事件顺序先发送，新增且未完成的任务按章节/任务顺序分别发送解锁提示。保留已完成但可推进的 ID 作为基线，避免普通重置直接冒充新解锁；每 20 tick 再扫描一次，以覆盖没有进度事件的锁定、重置与重复任务冷却变化。锁定切换、换队和任务文件 cache clear 重载只重建基线，不批量播报已有任务。只有新绑定成功会在 `AssistantPlayers` 标记一次待发送引导，绑定消息先发送，等登录基线就绪且队伍可用后由 FTB tick 消费该标记，依次发送可领取的新手礼包和普通任务概览（总数及前三项）；缺少内容时跳过。概览不生成列表编号，点击“查看任务”才重新执行完整列表查询。普通登录及重复授予不创建标记，登出、撤销或重置清除未发送标记，文件重载不重复引导；撤销后新绑定可再次触发。FTB 明确关闭通知的进度批次静默更新基线，隐藏章节与不可见任务不发送。

`FtbQuestReader` 是独立的只读任务入口，按当前 `ServerQuestFile` 与 `TeamData` 创建并在服务端线程使用，不缓存进度。`stream(Predicate<Quest>)` 统一章节遍历、可见性和介绍任务过滤，其余类型可组合谓词扩展；查询、详情、播报、礼包和通知共用这里的读取判定。无任务或全部为原生 `CheckmarkTask` 且无奖励的任务视为介绍任务，系统不读取，也不修改其进度或绕过它作为前置时的依赖。至少有一个原生勾选任务、不可重复、所有奖励均为非空且非隐藏的原生 `ItemReward` 时，识别为新手礼包。使用精确类匹配，第三方任务/奖励子类、混合奖励及重复任务保留普通处理，不按任务名称猜测。

读取视图分别为 `active(player)`（可见、已解锁、可推进、未完成，排除队伍锁定和玩家暂停）、`unfinishedStarterGifts()`（所有可见但未完成的礼包，包含尚未解锁项）、`availableStarterGifts(player)`（现在可领取的礼包，包含已完成但尚未领取的礼包）、`claimable(player)`（有可领奖励的任务）和 `manualSubmission(player)`（当前可执行且包含手动目标的任务）。`find(name)` 按标题或十六进制 ID 精确优先匹配，完整列表快照与玩家编号仍由 `FtbQuestAccess` 管理。通知单独使用 `notificationBaseline()` 保留已完成且可推进的任务，避免重置被当作新解锁。

FTB 没有通用的服务端“需要手动提交”字段；`requiresManualSubmission(quest)` 对全部目标执行 `anyMatch`，任意一个原生手动目标就将整个任务归入手动提交，包括可选目标或已完成的手动目标。按实际配置区分消耗物品、仅任务界面物品、勾选、经验及仅启用按钮且不自动检查的自定义任务；未适配的资源目标保守交给 FTB 专用输入。类型判定不加载 `TaskClient`，也不把“没有 tick 自动检查”的击杀、观察等目标误当成手动任务。显式提交只遍历 `submittableTasks(quest)` 中支持直接调用的原生目标：消耗物品、勾选、经验和手动自定义按钮；仅任务界面、流体、能量与未知资源目标仍在 FTB 中操作，查询和通知不触发提交。

`canComplete(player, team, quest)` 统一判断已解锁、未完成、玩家未暂停，且全部剩余目标均为原生 `CheckmarkTask` 的情况。`QuestOutput.canComplete` 供详情创建“完成此任务”动作，解锁通知共用同一判定和 `QuestSubmitModule.completeAction`。动作复用提交模块，`QuestSubmitInput.checkmarkOnly=true` 在真正执行时重查上述条件，并只遍历勾选目标，避免旧按钮提交新增加的资源目标；依赖、顺序、重复任务代次和完成通知合并仍沿用原链路。按钮不会自动执行、不会解锁真正不可推进的任务，也不改变介绍任务与新手礼包分类。当前网络协议为 `1`，任务列表没有分页偏移，Tool 直接返回完整简表 List，Module 再包装为 `ResultTypes.QuestList` 卡片。

新手礼包详情使用 `StarterGiftOutput.gifts` 汇总全部当前可领取的匹配任务；每组只同步内部任务 ID、奖励预览、领取引用与可选错误，界面和播报不显示任务名或章节。`StarterGiftQueryModule` 与只读 Tool 负责刷新完整列表，领取模块只接受可信按钮携带的 `StarterGiftInput.claimRefs`，不参与自由模型路由。领取引用绑定玩家、队伍、原任务/奖励对象及完成时间，2 分钟有效，撤销同步清理。显式批量领取先校验引用归属，再逐组核对解锁、暂停、可见和奖励阻止状态，在 `withPlayerContext` 内执行原生勾选提交，并通过 `TeamData.claimReward` 领取原奖励；不消耗物品或经验。成功项从列表移除，失败项保留错误与可用动作；回包仅静默更新状态和原礼包列表，不新建成功通知、对话记录或播报，全部领取后继续下一条通知。任务顺序、自动领取和队伍奖励语义沿用 FTB；不新建签到/收件箱权益，不重复推送礼包任务的普通完成消息。

明确提交完成的普通任务与 FTB 原生完成事件合并到同一队伍、任务 ID 的当批完成项；提交回包仅结束请求，不再追加“已提交”或“进度更新”通知。未完成时才返回实际进度是否变化。`AgentResult.silent()` 统一标记这两类静默回包，客户端更新请求与礼包状态，历史不新增空白结果；写步骤与请求回放仍保留。新增礼包列表输入和丰富任务通知载荷后，未发布版本的网络协议统一为 `1`，客户端与服务端需同步更新。

通知接收者为已完成系统绑定初始化的在线队员。`AssistantPlayers.notificationsEnabled` 复用服务端已完成登录基线作为绑定缓存，不在 tick 中读取玩家文件；发送前重新核对原玩家实例、连接代次、绑定运行代次及当前队伍。新加入或刚绑定的成员不接收加入前的解锁差异，离线队伍不保留积压。单队同 tick 最多保留 256 个不同完成项，客户端仍使用 32 个等待槽的限长 HUD 队列；大批量管理操作不承诺无限保留通知。消息由 `notifyEvent` 直接以 `event` 回包进入队列，没有模型请求、Agent 写任务或新奖励副作用；详情按钮继续走既有授权查询。通知没有独立持久化 outbox，不补播停机或离线期间的历史。FTB 原生 toast 不拦截。

完成通知在发送时按接收玩家的可领奖励生成操作，与详情共用 `FtbQuestAccess.reference`。仅一项普通奖励可直接复用 `quest_claim`，多项或自选奖励先走 `quest_detail` 供选择；没有可领奖励则只保留详情。事件到达本身不发奖，领取时仍校验引用期限、玩家/队伍和对象版本。可选模块安装时把共用的访问对象按服务器传给通知桥接，停服时清理。

物品参数由 `CardData.buildContext(...)` 填入公共 `CardContext.Builder`，JSON 的 items 节点选择来源、分组标题和说明，再交给客户端 `AssistantItems` 共享绘制，卡片、当前回复和通知使用同一个 AUI `Item` 组件及共享 CSS。物品数据来自已解码的业务对象，不扫描 JSON 或模型文字；新类型在自己的 `buildContext(...)` 中扩展。`ItemPreviewOutput` 使用 `RecordCodecBuilder` 保存物品模板与数量文本，`QuestChoiceOutput` 保存候选名称与预览；只有隔离的 FTB 适配器访问 `ItemReward`，核心载荷及客户端不引用可选模组类。FTB 使用数量 1 的模板保留组件，再用 `getButtonText()` 展示真实配置数量/随机范围，不构造超出模板范围的物品，也不改动 FTB 原始栈。客户端在渲染阶段创建实际栈；原版 tooltip 接收该栈，保留物品 tooltip 组件和模组事件。详见[界面与本地化](ui-localization.md)。

客户端 `AssistantRecognition.Session` 通过 `BooleanSupplier` 接收所属 UI 的有效性判断，供输入界面与 HUD 通知共用录音、网络与计量；最终回调同时核对原连接。`AssistantRecognitionKeys` 优先将按住说话交给当前可见通知，再交给主输入界面。通知将按钮本地化键与回调分离，用共用 `LexicalIndex` 缓存固定例句向量；未命中时进入主聊天路由。资源包定义、匹配门槛和生命周期见[语音识别](asr.md)与[轻量路由](semantic-routing.md)。

## 当前语义增强与其余限制

本地路由已改为纯 Java 词法匹配，默认新配置 ENFORCE；已有配置的 OFF 保留。使用少量领域同义词、字词及相邻特征、TF-IDF 余弦相似度与覆盖率，没有模型运行库、外部词典或向量服务。低分、小分差、缺参数、陌生主题、复杂表达或不可用时调用配置的模型服务。profile 显式声明、模块工具声明和执行层共同限制只读直达，`RouteSource` 区分结构化、指令、本地匹配、精确缓存、LLM 和事件，自动路由不会绕过商城确认。详见 [轻量本地意图路由](semantic-routing.md) 与 [选型依据和后续计划](semantic-routing-plan.md)。

执行成功的部分 LLM 只读意图可按玩家短期精确缓存，只保存模块 ID，当前参数仍重新解析，动态数据每次查询。JDK 有界 LRU/TTL 替代 Caffeine，关闭保存不读写此缓存。语言从 Minecraft `player.clientInformation().language()` 读取，执行上下文冻结后为每次模型调用注入 `requestContext.playerLanguage`。清空记忆、偏好变化、撤销与掉线清理对应缓存作用域，旧任务不得回填。旧 ONNX 字段和阈值不再读取，也不再需要模型目录或 JVM native 参数。

本地索引只在启动时由可信 profile 和服务端补充例句建立，查询在原有交互工作线程执行，不增加 native 线程池或网络请求。模块、词表、例句和索引语言变动后正常重启；模式、匹配阈值、缓存容量和新条目 TTL 直接读取配置实例。全量大小检查覆盖 System 内嵌资源及库，Minecraft、NeoForge 和原有 UI 依赖另计。

原设计稿是目标，不应把所有章节都当作已经实现。当前未包含：配方/整合包加工链、任意他人私有资料查询、多助手/多会话、模型摘要、通用工具自动规划循环、逐模块可配置公平权重/重试、模型供应商故障熔断、事件热重载、数据库迁移、严格一次背包交付、完整观测后端与 MCP 端点。

本轮验证仅为编译、打包与静态检查；没有实际调用模型服务、启动客户端/服务端或验证游戏内布局/领奖。
