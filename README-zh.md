# System

[English](README.md) | 简体中文

一个把网文“系统”带进 Minecraft 的 NeoForge 模组。Agent 部署在服务端，负责数据查询、任务、签到与商城交互；客户端提供完整工作台、简约说话框和不抢占操作的 HUD 通知，并可选用语音播报。

默认使用 DeepSeek，也支持其他兼容 OpenAI Chat Completions 的模型服务。明确指令和高把握的本地只读路由优先执行，减少不必要的模型调用；不同模块独立控制调用次数与执行预算。

## 功能

- **玩家与物品查询**：查看本人状态、公开在线玩家名称，按名称或注册 ID 搜索物品。
- **FTB Quests 集成**：用简表展示可见、已解锁、可推进且未完成的任务，按编号或名称展开详情，再明确选择奖励与领取。完成与新解锁任务通过系统通知依次提示。
- **AI 签到**：模型从服务端批准的奖励池中选择奖励，成功后进入收件箱，玩家手动领取；模型不能编造物品或修改数量。
- **系统商城**：浏览商品、查看报价、确认以物易物。预留系统币支付接口，尚未实现系统币经济系统。
- **内容配置与工作流**：数据包定义签到奖励和商城商品；服务端规则可在玩家完成成就后触发有界模块。
- **两种界面**：完整工作台提供历史与操作卡片，简约模式仅展示当前对话与单行输入。支持本地指令补全和原生输入法。
- **HUD 通知与语音**：提示不抢鼠标或键盘；可选阿里云流式 TTS 朗读正文，界面关闭、通知结束或切换时掐断对应语音 session。
- **用量与隐私控制**：按玩家统计 AI / TTS / ASR 用量，支持管理历史、关闭外部 AI，按 UUID 隔离数据并限制模型与工具执行。

本地路由使用领域同义词、稀疏 TF-IDF 和玩家独立的精确意图缓存，不携带 ONNX Runtime、DJL、原生推理库或需额外下载的模型权重。低把握时仍交给配置的模型判断；相似分数不等于正确概率。

## 环境要求

| 组件 | 目标版本或安装位置 |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 同一 Minecraft 版本的 26.1.2.114 或更高版本 |
| Java | 25 |
| System | 服务端与客户端均安装 |
| ApricityUI | 客户端依赖，1.2.6 或更高且兼容 Minecraft 26.1.2 的版本 |
| Simple Voice Chat | 可选客户端录音后端；开发适配 2.6.26+26.1.2，不打包进 System，服务端无需安装 |
| FTB Quests | 可选；开发配置版本为 26.1.2.8，配套 FTB Library 26.1.2.9、FTB Teams 26.1.2.4 |

同时安装所选模组版本要求的依赖。FTB 功能只在安装 FTB Quests 后注册，其他助手功能不依赖它。构建依赖列表见[开发文档](docs/development.md)。

网络协议为 **3**，客户端与服务端需同步更新；请求、响应、结果卡片和动作统一使用注册的 Codec 分派。网络包直接携带类型对象，不再使用 JSON 字符串或特殊请求 ID 区分操作；当前开发存档直接使用现行 Codec，不提供旧格式迁移。

## 快速开始

1. 两端安装 System，客户端安装 ApricityUI。需要任务集成时安装 FTB Quests 及其依赖。
2. 正常启动一次服务端，或进入单人世界，在 `config/system/` 下生成各服务端配置文件。
3. 需要 AI 时，在服务端进程的启动环境中设置 `SYSTEM_DEEPSEEK_API_KEY`，然后重启。单人游戏需让 Minecraft 进程继承该变量。配置文件保存环境变量名，不保存密钥。
4. 管理员执行 `/system grant <玩家>` 授予系统。玩家默认未绑定。
5. 执行 `/system`、`/system full` 或 `/system simple`，使用界面操作或输入请求。

例如，在 PowerShell 中设置后，从同一环境启动服务端：

```powershell
$env:SYSTEM_DEEPSEEK_API_KEY = "<你的 API Key>"
```

明确查询、商城浏览和物品兑换不需要模型密钥；AI 签到和模型路由兜底需要。`/system usage` 不要求已绑定。

### 模型配置

模型连接配置位于 `config/system/system-model.json`，默认字段如下，修改后正常重启服务端。

```json
{
  "providerType": "auto",
  "baseUrl": "https://api.deepseek.com",
  "model": "deepseek-chat",
  "apiKeyEnvironment": "SYSTEM_DEEPSEEK_API_KEY",
  "apiKeyRequired": true,
  "jsonMode": true,
  "outputTokenParameter": "max_tokens",
  "thinkingEnabled": false
}
```

`baseUrl` 填 API 前缀，不含 `/chat/completions`，适配器会自动追加。切换其他兼容服务时修改地址、模型 ID 和密钥环境变量名，必要时显式设置 `providerType: "openai"`。`auto` 只为 DeepSeek 官方域名启用专用参数；通过代理调用 DeepSeek 时可显式设置为 `deepseek`。

文本模型目前使用非流式 JSON 决策，流式播放用于 TTS 音频。本地服务、JSON 模式开关和输出 Token 参数见[提供商兼容说明](docs/model-api.md)。

## 指令与交互

以下指令在 Minecraft 聊天栏执行：

| 指令 | 用途 |
| --- | --- |
| `/system` | 打开上次选择的界面 |
| `/system full` / `/system simple` | 打开完整 / 简约界面 |
| `/system ask <内容>` | 提交自然语言请求 |
| `/system player` | 查看本人状态 |
| `/system item <查询>` | 搜索物品 |
| `/system quests [查询]` | 列出或筛选进行中的任务 |
| `/system view <编号或名称>` | 查看任务详情 |
| `/system claimable` | 列出有待领奖励的任务 |
| `/system signin` / `/system inbox` | 签到 / 打开奖励收件箱 |
| `/system shop [查询]` | 浏览或搜索商品 |
| `/system buy <商品 ID>` | 生成兑换预览，仍需单独确认支付 |
| `/system usage` / `/system usage all` | 查看个人用量 / 管理员查看全服用量 |
| `/system grant [玩家]` / `/system revoke [玩家]` | 管理员授予 / 撤销并清除系统 |
| `/system reset [玩家]` | 清除旧格式系统记录和绑定；累计用量保留，完成后需再次 `grant` |
| `/system routing` | 管理员查看路由诊断 |
| `/system help` | 查看功能帮助 |

`grant`、`revoke`、`reset` 省略玩家时操作自己；控制台应指定在线玩家。**`revoke` 和 `reset` 会删除该玩家的系统业务记录**，包括对话、签到记录、收件箱奖励和订单，累计 AI / TTS / ASR 用量保留。`reset` 用于当前 Codec 无法读取旧格式存档的情况，执行后需要再次 `grant`；已经交付的物品、FTB 进度和外部支付不会回滚。

在**助手自己的输入框**中，还可以使用：

- `选择 1`、`/select 1` 或 `1`：执行当前回复中的操作。
- `查看 1`、`查看 任务名`：展开当前任务列表中的任务或按名称查询。
- `/mode simple`、`/mode full`：切换界面。
- `/memory on|off`、`/forget`、`/ai on|off`：管理历史与外部 AI 开关。
- `/settings`：统一设置；`/tts`、`/voice`：选中语音播报标签；`/asr`：选中语音输入标签；`/usage`：用量统计；`/close`：关闭界面。

这些本地界面指令不是独立的服务端命令。Enter 发送，上下键选择补全，Tab 或点击填入；输入法组合期间，Enter 优先确认候选。简约界面没有历史列表，操作通过文字、指令与当前回复编号完成。

通知显示在 HUD 底部。按原版聊天键（**默认 T**）进入聊天栏，即可点击“确定”或操作按钮。连续通知保持面板展开，仅淡出、替换和淡入内容。确定、超时或切换通知时会立即掐断旧语音，不再等上一条读完才进入下一条；关闭助手界面同样结束其语音 session。

## 可选语音

语音输入默认关闭。通过 `/asr` 开启后，按住 **F8** 录音、松开结束（可在控制设置改键）；游戏中按键会打开助手，原有 `/listen` 和“语音输入”按钮仍可使用。设置页会检测可选的 Simple Voice Chat：安装后沿用其选定麦克风，未安装使用 Java Sound；服务端无需安装 SVC。需要独立的 NLS 项目 AppKey 与临时 Token，不能使用百炼 TTS 密钥。最终识别结果填入草稿，Enter 发送；也可开启“识别后立即发送”；最长 60 秒，关闭界面同步取消。ASR 按指定的 3.5 元/千次成功调用估价，已有价格配置需加入 CALLS 条目。详见[语音识别](docs/asr.md)。

从完整工作台进入“设置 → 语音播报”，或在助手输入框输入 `/tts`。统一设置包含常规、语音播报、语音输入三个标签，切换保留未保存内容，“保存当前页”只应用选中标签；界面模式、历史与外部 AI 开关集中在常规页。语音默认关闭。

1. 填写阿里云百炼 API Key 并选择对应地域，或给客户端进程设置 `DASHSCOPE_API_KEY`。
2. 设置模型、音色、语言与音量。默认模型 `qwen3-tts-flash`、音色 `Cherry`、语言跟随游戏。
3. 试听后开启自动朗读并保存。自定义音色 ID 必须与创建时绑定的模型一致。

外部修改 `config/system/system-speech.json` 后，可点击“重新加载配置”更新语音设置并丢弃本页未保存的修改。工具导出的音色参数需先合并到该文件，保留密钥和本地偏好。

SSE 音频边接收边播放，优先使用 Minecraft 的 Voice 音量分类，必要时走直接 OpenAL 后备。取消 session 会停止请求、清空 PCM 缓冲并停止播放，迟到回调不会重新播放旧音频；已知用量仍保留。

Python 3.10+ 小工具 [`tools/voice_design.py`](tools/voice_design.py) 支持音色设计、复刻、测试合成和临时上传，使用标准库。模型兼容与可选音频预处理说明见[语音文档](docs/tts.md)。

```powershell
python tools/voice_design.py --lang zh --help
```

## 内容配置与扩展

签到奖励与商城商品完全由数据包定义，默认内容也包含在模组内置数据包中：

```text
data/<命名空间>/system/signin_rewards/<路径>.json
data/<命名空间>/system/shop_offers/<路径>.json
```

可将[商城数据包示例](examples/commerce-pack)复制到存档的 `datapacks/` 目录，按正常数据包流程加载。新增 ID 会加入目录，同路径文件覆盖内置条目；使用 `enabled: false` 禁用单项。目录重载会使旧报价失效，已入账奖励保留原内容。

Agent 统一位于 `com.iafenvoy.system.agent`：根包集中执行、接口、注册表、指令和请求结果，`builtin`、`routing`、`model`、`store`、`commerce`、`prompt` 各自集中相关接口、数据与实现。内置输入和输出在 `agent.builtin.input`、`agent.builtin.output` 中一类一文件；通用 Codec、Schema 和 JSON 工具移至 `com.iafenvoy.system.util`，配置位于外层 `com.iafenvoy.system.config`。受信 Java 扩展通过 `AssistantRegistrationEvent` 一次注册模块，由工具 List 和 `ModuleRouting` 统一声明指令、语义配置、模型可见性及快捷入口；指令执行、帮助和补全共用注册信息，详见 [Agent 运行结构](docs/agent-runtime.md)。模块分别设置模型次数、工具次数、写步骤、Token 与时限。FTB 直接通过本地类型化 Tool 调用，不需要 stdio 或 HTTP MCP 传输。

详见[数据包与成就规则](docs/datapacks.md)、[架构与扩展 API](docs/development.md)。系统币目前只有支付扩展接口，没有内置余额、赚取、转账或充值功能。

## 数据与隐私

本模组配置统一放在各自服务端或客户端实例的 `config/system/`。缺失的配置会先从 JAR 内 `defaults/<文件名>.json` 复制到此目录，再进行解析；已有文件保留。

| 位置 | 内容 |
| --- | --- |
| 服务端 `config/system/system-agent.json` | 执行预算、并发、每日模型上限与时区 |
| 服务端 `config/system/system-model.json` | 模型连接与提供商选项 |
| 服务端 `config/system/system-semantic.json` | 本地意图路由、阈值、例句与缓存 |
| 服务端 `config/system/system-advancements.json` | 受信成就规则 |
| 服务端 `config/system/system-speech-pricing.json` | TTS 单价，缺失时复制内置默认文件，服务端启动时读取 |
| `<存档>/system-agent/<UUID>.json` | 绑定、对话、偏好、奖励、订单与工作流记录 |
| `<存档>/data/system/token_usage.dat` | 独立持久化的玩家 AI / TTS / ASR 用量，不随撤销或清空记忆删除 |
| 客户端 `config/system/system-client.json` | 界面模式偏好 |
| 客户端 `config/system/system-speech.json` | 本地语音设置及界面中填写的密钥 |
| 客户端 `config/system/system-asr.json` | 本地识别开关、NLS AppKey、临时 Token 与地域 |

玩家身份取自已认证连接，操作经过服务端校验、工具白名单与模块预算；模型输出不能授权任意命令或自行定价。对话上下文采用有界近期历史和确定性压缩，不额外调用模型做摘要。

服务端模型密钥不发送给客户端，客户端语音密钥不发送给 MC 服务端。界面填写的语音密钥以明文保存在本地设置文件中。必要上下文会提交给配置的模型服务，朗读正文会提交给语音服务；清空本地历史不代表删除提供商记录。

AI 用量取自服务端 API 响应；TTS 用量由客户端回报，仅供参考，不能作为支付或发奖依据。费用按服务端收费标准配置估算，修改后通过服务端 `/reload` 或重启生效，不重算历史累计金额，以提供商账单为准。物品交付、扣款与账本落盘并非同一事务，崩溃后的未知状态需要人工核对，不会盲目自动补发。详见[用量统计](docs/token-usage.md)和[领取 / 支付行为](docs/usage.md)。

## 构建

使用 JDK 25 和仓库自带的 Gradle Wrapper：

```powershell
.\gradlew.bat compileJava testModClasses jar
```

Linux / macOS 使用 `./gradlew`。产物位于 `build/libs/`；仅开发使用的 `src/test-mod/` 内容不会进入发布 JAR。

该命令编译主源码与 test-mod 源集并打包，不运行测试或启动 Minecraft；游戏内验收需单独进行。

## 文档

完整目录见[文档入口](docs/README.md)。

| 文档 | 内容 |
| --- | --- |
| [使用与配置](docs/usage.md) | 安装、指令、界面、任务、商城与隐私 |
| [模型接口](docs/model-api.md) | 兼容服务与 DeepSeek 特殊处理 |
| [本地意图路由](docs/semantic-routing.md) | TF-IDF、阈值、缓存与模型兜底 |
| [内容配置](docs/datapacks.md) | 奖励池、商品、事件规则与执行策略 |
| [语音](docs/tts.md) | 流式播放、音色与 Python 工具 |
| [AI / TTS / ASR 用量](docs/token-usage.md) | 存储、计量与费用估算 |
| [界面与本地化](docs/ui-localization.md) | 共享样式、输入、通知与语言文件 |
| [开发与架构](docs/development.md) | 模块、工具、持久化与实现边界 |
| [编译与验收](docs/testing.md) | 编译检查与游戏验收范围 |
