# System 文档

当前实现已经切换到新的 Agent 架构。旧的数据包定义、绑定与页面流程已移除；旧版说明不再适用。

| 文档 | 内容 |
| --- | --- |
| [使用与配置](usage.md) | 打开助手、服务端密钥、功能入口、隐私及奖励状态 |
| [模型接口与 DeepSeek 兼容](model-api.md) | OpenAI Chat Completions、提供商配置、DeepSeek 思考参数与用量格式 |
| [DeepSeek 网页搜索与抓取](search-api.md) | 明确触发的联网模块、独立 ReAct 预算、共享密钥、Search/Fetch 按次计费 |
| [外部 API 请求清单](external-apis.md) | 模组与独立音色工具访问的接口、客户端/服务端归属、触发条件和发送的数据 |
| [开发与架构](development.md) | Agent 包组织、模块与工具接口、预算、持久化、MC 接入和当前限制 |
| [功能模块化](feature-modules.md) | 功能装配、两端指令、生命周期、页面/通知/口播注册与可选集成 |
| [注册表速查](registries.md) | 正式注册表 ID、条目类型、公开入口、查询方式及扩展注册示例 |
| [客户端 JSON 卡片](cards.md) | CardData/CardBuilder、纯文本约定、通用模板解析、按钮动作、物品和资源包覆盖 |
| [卡片与消息数据流](card-flow-design.md) | 已实现的两端边界、完整 DTO、按 ID/版本更新、结果状态枚举与 BuiltinTools 职责 |
| [轻量本地意图路由](semantic-routing.md) | 纯 Java 领域词表/TF-IDF、玩家意图缓存、只读直达、低把握回退与扩展 API |
| [路由选型与后续计划](semantic-routing-plan.md) | 10MB 分发约束、fastText/LIBLINEAR 等方案核实、校准与后续方向 |
| [扩展配置](datapacks.md) | 服务端配置、成就规则及签到/商城数据包；不使用旧注册表 |
| [界面与本地化](ui-localization.md) | 新工作台、AUI 文本安全、语言键约定 |
| [AI / 联网 / TTS / ASR 用量统计](token-usage.md) | 服务端 AI/联网账本、本地语音用量、按次估价与撤销后保留 |
| [语音合成与音色设计](tts.md) | 阿里云 TTS、客户端密钥与语种、音色配置、Python 设计/临时上传工具 |
| [语音识别](asr.md) | NLS 一句话流式识别、麦克风 session、临时 Token 与按次估价 |
| [文案与提示词](system-voice.md) | 系统流公开参考、语气规则与模型/模板边界 |
| [编译与验收](testing.md) | 本次已执行检查、尚未执行的验收和可选开发依赖 |
| [原始设计稿](agent-design.md) | 完整目标架构；其中未落地的部分以开发文档中的实现范围为准 |

默认未绑定，管理员先用 `/system grant <系统ID> [玩家]`（例如 `/system grant system:default Steve`）授予，自己绑定可用 `/system grant system:default`，系统 ID 从数据包注册表补全，当前每人最多一个系统；再用 `/system`、`/system simple` 或 `/system full` 打开界面。`/system revoke [玩家]` 撤销并清空该玩家业务记录，累计 AI / TTS / ASR 用量保留，可用 `/system usage` 查看。服务端首次初始化会分别创建执行预算、模型连接、语义路由和成就规则配置；模型连接位于 `config/system/system-model.json`。默认使用 DeepSeek，设置环境变量 `SYSTEM_DEEPSEEK_API_KEY` 后启动服务端即可使用模型路由和 AI 签到，也可配置其他 OpenAI 兼容服务。新配置默认启用轻量本地路由，无需额外模型；已有关闭配置保留。高把握只读请求可直接处理。结构化玩家、物品、任务查询不需要模型密钥。
