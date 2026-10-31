# Agent 快速索引

从 [README](README.md)、[中文 README](README-zh.md) 的版本信息与文档导航整理，供恢复上下文时快速定位。整理日期：2026-10-27。

先阅读 [AGENTS.md](AGENTS.md) 的仓库约定，再按下表查阅相关文档；本文件仅作导航，不替代源码或详细设计。文档描述与实现不一致时，以当前源码和构建配置为准。

## 版本与依赖

| 项目 | 当前配置 / 要求 | 核对入口 |
| --- | --- | --- |
| System | `1.0-alpha.1`；客户端与服务端均安装 | [gradle.properties](gradle.properties) 的 `mod_version` |
| Minecraft | `26.1.2` | [gradle.properties](gradle.properties) 的 `minecraft_version` |
| NeoForge | 开发配置 `26.1.2.114`；README 要求同一 MC 版本的此版本或更高版本 | [gradle.properties](gradle.properties) 的 `neo_version` |
| Java | `25`，toolchain 与字节码均使用 Java 25 | [build.gradle](build.gradle)、[Mixin 配置](src/main/resources/system.mixins.json) |
| Gradle / ModDev | `9.2.1` / `2.0.148` | [Wrapper 配置](gradle/wrapper/gradle-wrapper.properties)、[build.gradle](build.gradle) |
| ApricityUI | 开发配置 `1.2.6`；客户端依赖，需兼容 MC `26.1.2` | [gradle.properties](gradle.properties) 的 `apricityui_version` |
| Saturn | `1.0-beta.3`，CurseMaven 文件 ID `9349648`；两端必装，提供 Cost、DataStorage 与 Codec 工具 | [build.gradle](build.gradle)、[开发与架构](docs/development.md) |
| Simple Voice Chat | 开发适配 `2.6.26+26.1.2`，CurseMaven 文件 ID `9307181`；可选客户端录音后端，不打入发布 JAR，服务端不要求安装 | [gradle.properties](gradle.properties)、[build.gradle](build.gradle) |
| FTB Quests | `26.1.2.8`；可选集成 | [gradle.properties](gradle.properties) 的 `ftb_quests_version` |
| FTB Library / FTB Teams | `26.1.2.9` / `26.1.2.4`；FTB 集成配套依赖 | [gradle.properties](gradle.properties) |
| 网络协议 | `1`；客户端和服务端必须匹配 | [NetworkManager.java](src/main/java/com/iafenvoy/system/network/NetworkManager.java) 的 `event.registrar(...)` |
| 音色工具 Python | `3.10+`；标准库实现 | [tools/voice_design.py](tools/voice_design.py)、[语音文档](docs/tts.md) |

模组尚未发布，网络协议统一为 `1`；实际注册值以 `NetworkManager` 为准。

改版本或依赖时同步维护本索引、两份 README 及 [开发文档版本表](docs/development.md)；实际配置集中在 `gradle.properties`、`build.gradle` 和 Wrapper 配置。可选依赖的安装检测与类隔离约定见 `AGENTS.md`。

## 文档导航

完整入口：[docs/README.md](docs/README.md)。按修改主题直接查阅：

| 修改主题 / 查询内容 | 文档 |
| --- | --- |
| 安装、绑定 / 撤销 / 重置、指令、界面、任务、商城、抽奖、隐私 | [使用与配置](docs/usage.md) |
| Agent 执行流程、AgentAction 与 MapCodec 分派、Module / Tool 契约、类型化输入输出、玩家模块 DataStorage 扩展 | [Agent 运行结构与扩展](docs/agent-runtime.md) |
| 助手 Brigadier 命令、ClientModuleCaches（绑定/命令缓存直接持有数据与维护逻辑）、原生命令补全、标签同步与两端命令构建顺序 | [Agent 入口](docs/agent-runtime.md#给-module-声明入口)、[客户端模块贡献](docs/feature-modules.md)、[缓存时序](docs/registries.md#仍保留的运行时登记表) |
| 包组织、依赖、配置、持久化、MC 桥接、实现边界 | [开发与架构](docs/development.md) |
| 旧卡片缓存自动丢弃、有效回放保留、历史清理与存储异常分类；业务账本保持严格读取 | [持久化与恢复](docs/development.md#持久化与恢复)、[CodecIO](src/main/java/com/iafenvoy/system/util/CodecIO.java) |
| FTBQ 1.21.1 → 26.1.2 已同步：玩家语言、勾选礼包、图标 / tooltip、解锁汇总、分层数据库与数据库优先问答；分页点击与 ASR 收尾补充移植 | [跨版本同步记录](docs/ftb-quests-porting.md) |
| 勾选 / 无目标说明书、存档本地稀疏向量索引、guide 指令和任务页查询、玩家可见性复核 | [本地说明书检索](docs/quest-guides.md) |
| AgentDatabases 共享库 / 玩家独立库、Codec 数据表与段落索引、/system ftb reload、revoke / reset 清理私有库 | [分层本地数据库](docs/databases.md) |
| JVector 4.0.2 分发体积、磁盘图索引、Java / SIMD、TF-IDF 适配、性能边界与后续测量；仅评估未实施 | [JVector 索引评估](docs/jvector-evaluation.md) |
| 按键、声音、action/condition、模块目录、卡片节点注册与语音供应商工厂 Map；公开静态入口 | [registry 包](src/main/java/com/iafenvoy/system/registry)、[目录与职责](docs/development.md#目录与职责) |
| 内置功能平铺 module.builtin、DataStorage 集中 module.builtin.storage、available() 可选依赖检查、模块侧栏 / 顶栏设置、底部工具栏开关、对话底部对齐与滚动跟随、模块条件/手动覆盖、两端指令与 ModuleManager 生命周期 | [功能模块化](docs/feature-modules.md)、[界面与滚动](docs/ui-localization.md)、[内置模块](src/main/java/com/iafenvoy/system/module/builtin) |
| ServerModule / ClientModule 及 ToolModule / WorkflowModule / SharedModule / CoreModule 分类、按接口判断类型、配置 owner 权限、服务端 MapCodec dispatch、动态 system_module、客户端独立实现 Map | [功能模块化](docs/feature-modules.md)、[注册表](src/main/java/com/iafenvoy/system/registry/SystemRegistries.java)、[编译与验收](docs/testing.md) |
| 注册表 ID 与公开字段速查、协议/载荷/卡片节点注册表、语音工厂 Map、注册时序及扩展示例 | [注册表速查](docs/registries.md) |
| CardData.buildCard、公共 CardBuilder/CardView/CardUpdate、客户端 CardPresentation、纯文本无按钮约定、JSON 节点与平铺 Context、宿主双栏卡片 / player 模型跟随、朝向、缩放和偏移、明确本地/服务端动作 | [客户端 JSON 卡片](docs/cards.md) |
| 客户端 CardBuildEvent：按 CardData 类型给既有 Builder 补参数，再配合资源包 JSON；构建时触发，缓存读取不触发 | [卡片构建事件](docs/cards.md#构建事件给现有卡片补充参数)、[事件类](src/main/java/com/iafenvoy/system/event/client/CardBuildEvent.java) |
| 已实施卡片数据流：完整业务 DTO、消息 ID/版本、SHOW/REPLACE/REMOVE、状态与历史响应、Status 枚举和 BuiltinTools | [卡片与消息数据流](docs/card-flow-design.md) |
| OpenAI 兼容接口、DeepSeek 特殊参数、模型连接与 Token 响应 | [模型接口](docs/model-api.md) |
| 核心 system:question 先查私有 / 共享知识与授权 FTB 说明、可选工具与本次联网授权、WebResearch ReAct、DeepSeek 计费 | [知识问答与网页搜索](docs/search-api.md)、[知识表入口](docs/databases.md#问答知识表) |
| 模组和音色工具会请求哪些 API、触发条件、发送数据、客户端 / 服务端归属 | [外部 API 请求清单](docs/external-apis.md) |
| 领域词表、稀疏 TF-IDF、阈值、玩家意图缓存、只读直达与模型兜底 | [本地意图路由](docs/semantic-routing.md) |
| 10MB 分发约束、路由方案比较、校准与后续方向 | [路由选型与计划](docs/semantic-routing-plan.md) |
| 系统定义、system_module 配置实例与 server.type/client、grant 指定系统与单绑定/多系统结构预留、模块 ID/tag/condition、模块增删 action、签到奖励、商城商品、抽奖奖池、提示词、成就规则 | [数据包与扩展配置](docs/datapacks.md) |
| 三张服务端 Data Pack Registry：signin_rewards / shop_offers / lottery_pools；CatalogModule 的 rewards / offers / pools 支持 ID、tag、混合数组；玩家并集权限与扣款前复核 | [目录可选项](docs/datapacks.md#模块目录可选项)、[注册表](docs/registries.md#数据包注册表与外部注册表)、[CommerceCatalog](src/main/java/com/iafenvoy/system/agent/commerce/CommerceCatalog.java) |
| TTS 接口、流式播放、语种、音色、取消 session、Python 设计 / 复刻 / 临时上传 | [语音合成与音色设计](docs/tts.md) |
| ASR、NLS AccessKey 自动签发与 Token 缓存刷新、停止 / 自然 EOF 与设备关闭解耦、session 超时、可选 SVC、按键录音与按次估价 | [语音识别](docs/asr.md) |
| 服务端 AI/联网用量 SavedData、仅本地可见的个人 TTS / ASR 用量、字符与 Token、客户端计费配置、撤销后保留 | [用量统计](docs/token-usage.md) |
| 共享 CSS、完整 / 简约界面、HUD、动画、物品 tooltip、输入法、双语键 | [界面与本地化](docs/ui-localization.md) |
| 网文系统语气、提示词与模板边界、公开参考 | [文案与提示词](docs/system-voice.md) |
| 绑定、新手礼包、任务通知的文案与占位符；该稿不直接作为运行时模板 | [通知文案稿](docs/notification-copy-draft.md) |
| 编译检查、已执行验证、未验收范围、开发依赖 | [编译与验收](docs/testing.md) |
| 原始目标架构；实际已落地范围需结合运行结构与开发文档 | [原始设计稿](docs/agent-design.md) |

新增、改名或删除文档时同步更新本表和 `docs/README.md`；README 保留面向使用者的精简导航，本文件保留面向 Agent 的完整定位入口。
