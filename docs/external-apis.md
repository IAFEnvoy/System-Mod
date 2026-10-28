# 本模组会请求哪些 API

本文根据当前仓库代码整理，核对日期：2026-10-27。描述的是本模组内置实现发出的外部请求，不包含 Minecraft、加载器或其他模组自身的联网行为，也不包含第三方扩展自行注册的模型、TTS、ASR 或支付实现。

## 总览

| 功能 | 请求发起方 | 外部服务 | 什么时候请求 | 默认状态 |
| --- | --- | --- | --- | --- |
| AI 意图判断、签到奖励选择、特殊任务文案整理、任务背景筛选 | Minecraft 服务端；单人游戏为内置服务端 | 配置的 OpenAI Chat Completions 兼容服务，默认 DeepSeek | 实际需要模型且当前玩家允许外部 AI 时 | 默认提供商为 DeepSeek；需要服务端配置密钥 |
| 网页搜索与抓取（预留客户端） | 显式调用 Java 客户端的工作线程，后续预计服务端使用 | 独立配置的 DeepSeek Search / Fetch API | 仅代码显式调用 `search()` / `fetch()` 时 | 未接入 Agent、命令或界面，普通游戏操作不会调用 |
| 文字转语音（TTS） | Minecraft 客户端 | 阿里云百炼 / DashScope | 开启自动朗读后播报当前消息，或手动试听 | 自动朗读默认关闭 |
| 语音转文字（ASR） | Minecraft 客户端 | 阿里云智能语音交互 NLS | 玩家开启语音输入并主动录音 | 默认关闭 |
| 非流式 TTS 音频下载 | Minecraft 客户端 | TTS 响应中指定的阿里云 OSS 地址 | 调用非流式合成且返回音频 URL 时 | 正常游戏播报使用流式 PCM，不需要这次下载 |
| 音色设计、复刻、查询、删除和临时上传 | 独立 Python 工具 | 阿里云百炼及 OSS | 手动执行 `tools/voice_design.py` 的对应子命令 | 不随模组运行自动执行 |

FTB Quests 查询、提交和领奖调用本机服务端 Java API，没有独立的 HTTP 或 stdio MCP 服务。轻量意图匹配也在本地运行，不请求 embedding API。

## 1. 服务端文本模型 API

### 地址与协议

- 方法：`POST`，请求和响应为 JSON，当前 `stream=false`。
- 路径：在 `config/system/system-model.json` 的 `baseUrl` 后追加 `/chat/completions`。
- 默认完整地址：`https://api.deepseek.com/chat/completions`，默认模型 `deepseek-chat`。
- 例如使用 `https://example.com/v1` 作为 `baseUrl`，实际请求 `https://example.com/v1/chat/completions`；配置时不要再填一次 `/chat/completions`。
- 可替换为其他 OpenAI 兼容服务。远端要求 HTTPS，也允许本机回环地址的 HTTP 服务。
- 认证：读取 `apiKeyEnvironment` 指定的服务端环境变量，默认 `SYSTEM_DEEPSEEK_API_KEY`，放入 `Authorization: Bearer ...`。允许无需密钥的本地服务时，可关闭 `apiKeyRequired`。

DeepSeek 的 `thinking`、`reasoning_effort` 兼容只改变同一次请求的参数，不增加另一个接口请求。模型、JSON 模式和输出 Token 参数说明见 [模型接口](model-api.md)。

### 哪些行为会调用模型

| 调用场景 | 发给模型的主要内容 | 可以避免请求的情况 |
| --- | --- | --- |
| 意图路由兜底 | 当前输入、有限的近期对话原文及历史结果摘要、本地候选模块及相似度、可用模块说明与输入 schema、玩家语言、路由提示词 | 按钮等结构化动作、明确指令、本地高把握只读匹配或有效意图缓存命中 |
| AI 签到奖励选择 | 当前玩家名称、生命值与上限、经验等级、所在维度、语言、日期，以及数据包中的候选奖励定义 | 当日已签到时直接返回已记录的结果，不重新选择 |
| 特殊任务要求或奖励的播报整理 | 无法直接格式化的目标或奖励类型、原生标题、目标数量和已知限制，以及语言和整理提示词 | 常见目标直接格式化；整理片段缓存命中；未启用相应播报 |
| 任务背景筛选 | 任务标题、FTB 原始可见说明、语言和背景筛选提示词 | 详情或背景播报关闭；说明为空、重复标题或仅为 URL；有效背景缓存命中 |

所有内置模型请求经过 `ExecutionContext.model → ModelGateway → OpenAiCompatibleClient`，共享权限、额度、并发限制和用量统计。一次自然语言操作可能先调用路由，再调用功能自身的模型逻辑；不是每条消息都固定一次请求。

签到模型只接收当前玩家的专用投影，不发送在线玩家列表或完整背包。路由可能发送该玩家近期输入的原文，以及历史结果中的任务、奖励或领取引用等受限字段；玩家主动输入的内容也可能包含个人信息。不会自动将整个存档、全部聊天历史或所有玩家记录打包发送。

特殊任务整理和背景筛选只使用任务定义，不发送玩家背包、当前任务进度或整段对话；任务文本本身仍可能包含数据包作者填写的内容。背景结果保存在当前存档的 `system-quest-backgrounds.json`，按任务 ID、语言和内容指纹复用；成功的“无背景”结论也缓存。

关闭玩家的“外部 AI”后，这些模型调用被禁止；本地查询和已有缓存仍可使用。语音功能有独立开关和密钥，关闭外部 AI 不会一并关闭客户端 TTS 或 ASR。

实现入口：

- [模型 HTTP 客户端](../src/main/java/com/iafenvoy/system/agent/model/OpenAiCompatibleClient.java)
- [模型连接配置](../src/main/java/com/iafenvoy/system/config/file/ModelConfig.java)
- [意图路由](../src/main/java/com/iafenvoy/system/agent/routing/HybridRouter.java)
- [签到模型输入](../src/main/java/com/iafenvoy/system/agent/builtin/signin/SignInDecision.java)
- [任务播报整理](../src/main/java/com/iafenvoy/system/agent/QuestNarration.java) / [背景筛选缓存](../src/main/java/com/iafenvoy/system/agent/QuestBackgrounds.java)

## 2. 客户端 TTS API

配置文件为客户端的 `config/system/system-speech.json`。密钥取配置中的 `apiKey`，为空时读取客户端环境变量 `DASHSCOPE_API_KEY`；由客户端直接请求阿里云，不交给 Minecraft 服务端代发。

### 域名选择

| 地域 | 未填写 Workspace ID | 已填写 Workspace ID |
| --- | --- | --- |
| 北京 `beijing` | `https://dashscope.aliyuncs.com` | `https://<workspace>.cn-beijing.maas.aliyuncs.com` |
| 新加坡 `singapore` | `https://dashscope-intl.aliyuncs.com` | `https://<workspace>.ap-southeast-1.maas.aliyuncs.com` |

### 合成接口

| 模型 | 方法与路径 |
| --- | --- |
| 默认 `qwen3-tts-flash` 及适配器允许的日期版本 | `POST /api/v1/services/aigc/multimodal-generation/generation` |
| `qwen-audio-3.1-tts-flash`、`qwen-audio-3.1-tts-plus`、`qwen-audio-3.0-tts-flash`、`qwen-audio-3.0-tts-plus` | `POST /api/v1/services/audio/tts/SpeechSynthesizer` |

认证使用 `Authorization: Bearer ...`。发送实际待朗读文本、模型、音色 ID、合成语种或语种提示；Qwen-Audio 还发送格式和采样率。文字可能包含宿主名称、任务名称、任务要求、奖励或允许播报的背景，具体取决于当前消息和语音设置，不会自动把整段聊天历史传给 TTS。使用已有自定义音色只发送音色 ID，正常合成不会重新上传复刻样本。

当前游戏播报发送 `X-DashScope-SSE: enable`，在同一 HTTP 请求中接收 SSE / PCM 音频流，不按每个音频片段新建一次合成请求。接口返回的最终音频 URL 不用于流式播放。自动朗读默认关闭，手动试听会主动调用合成接口；通知的本地提示音不调用 TTS。

可选整句音频缓存默认关闭；开启并命中后直接播放本机音频，不调用合成 API，也不新增云端用量。静音、缺少密钥或其他不允许播放的状态会跳过正常自动合成。关闭界面或确认通知会取消当前请求和播放，但不能保证已到达供应商的请求不产生用量。

### 保留的非流式音频下载

非流式 `synthesize` 使用相同的合成接口。如果响应给出音频 URL，客户端另发一次 `GET` 下载 WAV；若直接返回 Base64 音频则无需下载。

下载地址必须是适配器允许的阿里云 OSS 域名（主机以 `.aliyuncs.com` 结尾且含 `.oss-`），HTTP 地址升级为 HTTPS。下载不携带百炼 Bearer 密钥，也不跟随重定向。这条路径存在于代码中，当前正常游戏播报优先使用前述 SSE 流式路径。

实现入口：[阿里云 TTS 适配器](../src/main/java/com/iafenvoy/system/speech/aliyun/AliyunTtsProvider.java)、[合成请求字段](../src/main/java/com/iafenvoy/system/data/speech/AliyunTtsMessage.java)、[播报 session](../src/main/java/com/iafenvoy/system/speech/tts/AssistantSpeech.java)。

## 3. 客户端 ASR API

- 协议：WebSocket over TLS，地址 `wss://nls-gateway-cn-<region>.aliyuncs.com/ws/v1`。
- 当前支持地域：`shanghai`、`beijing`、`shenzhen`，默认上海；例如 `wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1`。
- 配置：客户端 `config/system/system-asr.json`，使用独立的 NLS 项目 `AppKey` 和临时 `Token`，不是百炼 TTS API Key。
- 配置为空时分别读取 `ALIYUN_NLS_APP_KEY`、`ALIYUN_NLS_TOKEN`。Token 放在握手请求头 `X-NLS-Token`，AppKey 放在识别命令头。
- 同一连接发送 `SpeechRecognizer.StartRecognition`、PCM 音频帧和 `StopRecognition`，接收中间结果及最终识别文本。

语音输入默认关闭；开启后，只有玩家主动启动语音输入才打开录音 session。发送本次麦克风录音（16 kHz、16-bit、单声道 PCM），以及识别所需的 AppKey、任务 ID、消息 ID 和参数，不发送整个聊天历史。当前单次最长录音 60 秒，关闭所属界面或通知会取消 session。

识别成功后，文字填入输入框，或按“识别后立即发送”设置继续提交；通知语音先匹配当前通知按钮，匹配失败才进入主聊天处理。后续若需要 AI 意图判断，还可能产生第 1 节的服务端模型请求，ASR 本身不是模型路由请求。

当前代码不请求阿里云 Token 签发或自动刷新接口，临时 Token 需要外部获取并配置。Simple Voice Chat 只作为可选录音设备适配来源，未安装时使用 Java Sound；ASR 音频由本模组客户端直接上传 NLS。只有玩家发送识别文字或执行通知操作时，才进入正常助手请求；ASR / TTS 用量不上传 Minecraft 服务端。

实现入口：[阿里云 ASR 适配器](../src/main/java/com/iafenvoy/system/speech/aliyun/AliyunAsrProvider.java)、[ASR 配置](../src/main/java/com/iafenvoy/system/config/file/RecognitionConfig.java)、[录音与识别 session](../src/main/java/com/iafenvoy/system/speech/asr/AssistantRecognition.java)。

## 4. 独立音色工具使用的 API

以下仅在手动运行 [tools/voice_design.py](../tools/voice_design.py) 时请求，不随 MC 启动、设置页打开或普通消息播报自动执行。工具可以读取客户端语音配置，但它不是游戏内后台任务。

| 操作 | 方法与路径 / 目标 | 发出的内容 |
| --- | --- | --- |
| `design` 音色设计 | `POST /api/v1/services/audio/tts/customization`，模型 `voice-enrollment`，动作 `create_voice` | 声音文字描述、试听文本、目标模型、音色前缀、语言和音频参数 |
| `clone` 音色复刻 | 同一 customization 接口，动作 `create_voice` | 选定 WAV 的 Base64 数据、目标模型、前缀、语言、样本时长和预处理选项 |
| `list` / `query` / `delete` | 同一 customization 接口，动作分别为 `list_voice` / `query_voice` / `delete_voice` | 前缀和页码，或指定音色 ID |
| `synthesize` 测试合成 | `POST /api/v1/services/audio/tts/SpeechSynthesizer` | 测试文本、模型、音色和 WAV 参数；返回 URL 时另向 OSS 发 `GET` 下载 |
| `upload` 获取临时上传策略 | `GET /api/v1/uploads?action=getPolicy&model=<model>` | 目标模型和百炼 Bearer 密钥 |
| `upload` 上传文件 | `POST` 到上一步返回的 HTTPS OSS `upload_host` | 选定音频文件和签名表单字段，不附带百炼 Bearer 密钥 |

音色管理与合成沿用第 2 节的域名选择；当前工具的音色管理操作限制北京地域。临时上传策略使用兼容域名 `dashscope.aliyuncs.com` 或 `dashscope-intl.aliyuncs.com`，即使指定 Workspace ID 也不切换到业务空间域名。

`clone` 直接将样本作为 Base64 提交，不会自动先调用临时上传。`upload` 是独立子命令，返回 `oss://` 资源地址；执行上传不等于已经创建音色或完成合成。工具密钥来自指定配置、`DASHSCOPE_API_KEY` 或隐藏输入，不写入导出的音色配置。

## 5. 预留的 DeepSeek 网页搜索与抓取 API

独立客户端 `search.DeepSeekSearchClient` 通过 `SearchProvider` 提供 `search()` 与 `fetch()`，目前没有 Agent 工具、模块、命令、界面或后台调用，正常游戏流程不会自动联网搜索。

- 配置：`config/system/system-search.json`，首次使用时从 JAR 默认文件复制；与文本模型配置独立，默认仍读取环境变量 `SYSTEM_DEEPSEEK_API_KEY`。
- 搜索：`POST https://api.deepseek.com/v1/tools/search`，发送调用者提供的查询、条数、是否包含正文，以及可选原始问题和定位信息；不自动采集玩家资料或 IP。
- 抓取：`POST https://api.deepseek.com/v1/tools/fetch`，将调用者提供的页面 URL 发给 DeepSeek，由 DeepSeek 获取 Markdown；本机不直接访问该网页。
- 认证：只向配置的 API 地址发送 `Authorization: Bearer ...`，不向结果 URL 或图标地址附带密钥，也不自动抓取结果。
- Search / Fetch 按次计费，目前不进入 AI Token 或玩家用量账本。所有报文使用 Codec，网络复用限长、超时、禁止重定向且不自动重试的 `HttpTransport`。

详细配置、返回字段、调用方法和官方资料见 [网页搜索与抓取](search-api.md)。

## 6. 哪些操作不额外访问外部 API

- 玩家与物品查询、FTB 查询/明确提交/领奖、新手礼包领取、商城与抽奖结算：内置逻辑直接读取或修改服务端游戏状态；明确指令或可信按钮执行这些操作本身不需要云端。自然语言先走模型兜底、结果触发 TTS 或特殊任务播报时，仍可能产生对应请求。
- 绑定成功通知、任务完成/解锁事件与消息队列：事件生成和固定文案在本地处理；语音开启后可另触发 TTS 和按需任务文案整理。
- 语义匹配与对话压缩：使用本地词法 / TF-IDF、缓存及确定性历史投影；没有 embedding、向量数据库、Redis、独立摘要模型或模型自动下载请求。
- 用量统计与费用估算：AI 用量由服务端从模型响应记录；TTS / ASR 用量仅按客户端账号存本机，费率读取客户端 `config/system` 下的配置；不会请求供应商账单、余额或实时价格 API，也不向 MC 服务端报告语音用量。
- 提示词与奖励数据包重载、配置加载、通知音效播放：读取本地资源或存档，不调用云端。

客户端与服务端之间的助手请求、任务播报查询和 AI 用量查询使用 Minecraft 自定义网络载荷，不是新增的公开 HTTP API。TTS 密钥、NLS Token 及语音用量均保留在客户端，不发送给游戏服务端。

按当前内置源码，本模组没有额外的遥测、广告、更新检查或通用数据上传接口。外部请求实现集中于 `OpenAiCompatibleClient`、`AliyunTtsProvider`、`AliyunAsrProvider`、尚未挂接游戏流程的 `DeepSeekSearchClient` 和上述 Python 工具；新增提供商或其他第三方扩展后，应同步更新本清单。

## 核对范围

本清单依据源码中的请求调用、地址拼接、配置及序列化字段整理，没有实际请求供应商 API，没有使用密钥进行连通性或收费调用验证，也没有启动游戏或执行测试。域名与路径反映当前代码，供应商的接口可用性、配额和收费以实际服务为准。
