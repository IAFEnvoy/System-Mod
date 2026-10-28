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
| Saturn | `1.0-beta.1`，CurseMaven 文件 ID `9334739`；两端必装，提供 Cost 与 Codec 工具 | [build.gradle](build.gradle)、[开发与架构](docs/development.md) |
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
| Agent 执行流程、Module / Tool 契约、注册表、类型化输入输出、扩展入口 | [Agent 运行结构与扩展](docs/agent-runtime.md) |
| 包组织、依赖、配置、持久化、MC 桥接、实现边界 | [开发与架构](docs/development.md) |
| 功能级装配、两端指令、玩家生命周期、卡片/通知/口播注册和可选集成 | [功能模块化](docs/feature-modules.md) |
| assets 卡片 JSON、CardContext.Builder 三表与类型检查、CardNode 接口 / node 子包与 Codec 分派、平铺参数与回调、style 字符串/数组、通知主体与代码按钮 | [客户端 JSON 卡片](docs/cards.md) |
| OpenAI 兼容接口、DeepSeek 特殊参数、模型连接与 Token 响应 | [模型接口](docs/model-api.md) |
| 独立 DeepSeek Search / Fetch 客户端；当前未开放给 Agent | [网页搜索与抓取](docs/search-api.md) |
| 模组和音色工具会请求哪些 API、触发条件、发送数据、客户端 / 服务端归属 | [外部 API 请求清单](docs/external-apis.md) |
| 领域词表、稀疏 TF-IDF、阈值、玩家意图缓存、只读直达与模型兜底 | [本地意图路由](docs/semantic-routing.md) |
| 10MB 分发约束、路由方案比较、校准与后续方向 | [路由选型与计划](docs/semantic-routing-plan.md) |
| 系统定义、签到奖励、商城商品、抽奖奖池、提示词、成就规则、执行策略 | [数据包与扩展配置](docs/datapacks.md) |
| TTS 接口、流式播放、语种、音色、取消 session、Python 设计 / 复刻 / 临时上传 | [语音合成与音色设计](docs/tts.md) |
| ASR、NLS 凭据、录音 session、可选 SVC、按键录音与按次估价 | [语音识别](docs/asr.md) |
| AI / TTS / ASR 用量、字符与 Token、SavedData、计费配置、撤销后保留 | [用量统计](docs/token-usage.md) |
| 共享 CSS、完整 / 简约界面、HUD、动画、物品 tooltip、输入法、双语键 | [界面与本地化](docs/ui-localization.md) |
| 网文系统语气、提示词与模板边界、公开参考 | [文案与提示词](docs/system-voice.md) |
| 绑定、新手礼包、任务通知的文案与占位符；该稿不直接作为运行时模板 | [通知文案稿](docs/notification-copy-draft.md) |
| 编译检查、已执行验证、未验收范围、开发依赖 | [编译与验收](docs/testing.md) |
| 原始目标架构；实际已落地范围需结合运行结构与开发文档 | [原始设计稿](docs/agent-design.md) |

新增、改名或删除文档时同步更新本表和 `docs/README.md`；README 保留面向使用者的精简导航，本文件保留面向 Agent 的完整定位入口。
