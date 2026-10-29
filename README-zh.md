# System

[English](README.md) | 简体中文

一个把网文“系统”带进 Minecraft 的 NeoForge 模组。Agent 部署在服务端，负责数据查询、任务、签到与商城交互；客户端提供完整工作台、简约说话框和不抢占操作的
HUD 通知，并可选用语音播报。

## 功能

- **玩家与物品查询**：查看本人状态、公开在线玩家名称，按名称或注册 ID 搜索物品。
- **FTB Quests 集成**：用简表展示可见、已解锁、可推进且未完成的任务，按编号或名称展开详情，再明确选择奖励与领取。完成与新解锁任务通过系统通知依次提示。
- **明确触发的联网搜索**：使用 `/system search <问题>` 或联网搜索页主动检索，普通提问不会自动联网。默认共享 DeepSeek
  密钥，循环预算独立配置，Search 和 Fetch 分别按次计费。
- **AI 签到**：模型从服务端批准的奖励池中选择奖励，成功后进入收件箱，玩家手动领取；模型不能编造物品或修改数量。
- **系统商城**：浏览商品、查看报价，通过 Saturn Cost 确认以物易物、资源或扩展支付。模组本身不提供系统币经济系统。
- **数据驱动抽奖**：浏览奖池、查看奖品权重与单次消耗，确认后单抽一次，奖励存入收件箱。数据包通过 Saturn Cost 定义消耗，也可设置免费抽奖。
- **内容配置与工作流**：数据包定义系统名称、介绍、启用模块、签到奖励、商城商品和抽奖奖池；模块支持 ID、tag
  和条件；system_module 数据包条目通过 server.type 选择服务端行为、client 指定独立客户端实现，系统引用模块配置实例。服务端规则可在玩家完成成就后触发有界工作流。
- **HUD 通知与语音**：提示不抢鼠标或键盘；可选阿里云流式 TTS 朗读正文，界面关闭、通知结束或切换时掐断对应语音 session。
- **用量与隐私控制**：服务端按玩家统计 AI 与联网用量，TTS / ASR 用量仅存当前账号本机。支持管理历史、关闭外部 AI，按 UUID
  隔离数据并限制模型与工具执行。

## 快速开始

1. 安装本模组和前置。需要任务集成时安装 FTB Quests 及其依赖。
2. 正常启动一次服务端，或进入单人世界，在 `config/system/` 下生成各服务端配置文件。
3. 需要 AI 时，在服务端进程的启动环境中设置 `SYSTEM_DEEPSEEK_API_KEY`，然后重启。单人游戏需让 Minecraft
   进程继承该变量。配置文件保存环境变量名，不保存密钥。
4. 安装提供系统定义的服务端数据包，管理员执行 `/system grant <系统ID> [玩家]` 绑定，例如
   `/system grant example:daily Steve`；省略玩家时绑定自己。玩家默认未绑定，目前每人最多绑定一个系统。
   内置 system:default 通过默认模块 tag 启用功能；FTB 任务与联网搜索的默认模块配置和 tag 追加仅放在 src/test-mod/，不会打进发布包。
5. 执行 `/system`、`/system full` 或 `/system simple`，使用界面操作或输入请求。

例如，在 PowerShell 中设置后，从同一环境启动服务端：

```powershell
$env:SYSTEM_DEEPSEEK_API_KEY = "<你的 API Key>"
```

明确查询、商城浏览和物品兑换不需要模型密钥；AI 签到和模型路由兜底需要。`/system usage` 不要求已绑定。

### 模型配置

配置文件位于 `config/system/system-model.json`，默认字段如下，修改后正常重启服务端。

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

`baseUrl` 填 API 前缀，不含 `/chat/completions`，适配器会自动追加。切换其他兼容服务时修改地址、模型 ID 和密钥环境变量名，必要时显式设置
`providerType: "openai"`。`auto` 只为 DeepSeek 官方域名启用专用参数；通过代理调用 DeepSeek 时可显式设置为 `deepseek`。

## 指令与交互

以下指令在 Minecraft 聊天栏执行：

| 指令                                    | 用途                                    |
|---------------------------------------|---------------------------------------|
| `/system`                             | 打开上次选择的界面                             |
| `/system full` / `/system simple`     | 打开完整 / 简约界面                           |
| `/system settings`                    | 直接打开统一设置，无需绑定或管理员权限                   |
| `/system ask <内容>`                    | 提交自然语言请求                              |
| `/system search <问题>`                 | 明确发起付费联网检索                            |
| `/system player`                      | 查看本人状态                                |
| `/system submittable [查询]`            | 列出可提交或勾选的 FTB 任务                       |
| `/system usage` / `/system usage all` | 查看个人用量 / 管理员查看全服 AI 与联网用量；语音统计始终仅显示自己 |
| `/system grant <系统ID> [玩家]`           | 管理员绑定一个数据包系统，省略玩家时绑定自己                |
| `/system revoke [玩家]`                 | 管理员撤销系统并清除业务记录，保留累计用量                 |
| `/system reset [玩家]`                  | 清除旧格式系统记录和绑定；累计用量保留，完成后需再次 `grant`    |
| `/system module add <玩家> <模块ID>`      | 管理员为玩家额外启用模块                          |
| `/system module remove <玩家> <模块ID>`   | 管理员为玩家禁用模块                            |
| `/system module list [玩家]`            | 管理员查看注册模块或指定玩家已启用的模块                  |
| `/system routing`                     | 管理员查看路由诊断                             |
| `/system help`                        | 查看功能帮助                                |

系统 ID、玩家和模块参数支持补全。重新授予同一系统不会重置玩家；改绑另一个系统需先撤销。
模块手动增删会覆盖数据包条件并持久保存，`revoke/reset` 时清除；核心、绑定和用量等必需模块不能手动移除。

通知显示在 HUD 底部。按原版聊天键（**默认 T**）进入聊天栏，即可点击“确定”或操作按钮。连续通知保持面板展开，仅淡出、替换和淡入内容。
确定、超时或切换通知时会立即掐断旧语音，不再等上一条读完才进入下一条；关闭助手界面同样结束其语音 session。

## 语音功能（可选）

**注意：所有语音功能均在客户端完成，并使用收费的 API**

目前所有语音功能均使用阿里云的API，后续将接入更多供应商。

### 语音输入

**此功能需要独立的阿里云 NLS 项目 AppKey 与 AccessKey ID / Secret；临时 Token 由程序自动获取、缓存并按需刷新。**

**建议安装 Simple Voice Chat 获得更好的效果。**

语音输入默认关闭。通过 `/system settings`，或在助手输入框输入 `/settings`，进入“语音输入”标签开启，修改自动保存。
默认按住 **F8** 录音、松开结束；游戏中按键会打开助手，也可以使用 `/listen` 和“语音输入”按钮。
最终识别结果填入草稿，Enter 发送；也可开启“识别后立即发送”；最长 60 秒，关闭界面同步取消。
通知显示时优先匹配确认或业务按钮，未匹配的文字再进入主聊天。Simple Voice Chat 为可选依赖，未安装时使用 Java Sound 录音。

### 语音输出

**此功能需要独立的阿里云百炼 API Key 并选择对应地域。**

从完整工作台进入“设置 → 语音播报”，或使用 `/system settings`、助手内 `/settings` 打开统一设置后切换标签。

1. 填写阿里云百炼 API Key 并选择对应地域，或给客户端进程设置 `DASHSCOPE_API_KEY` 环境变量。
2. 设置模型、音色、语言与音量。默认模型 `qwen3-tts-flash`、音色 `Cherry`、语言跟随游戏。
3. 试听后开启自动朗读，修改自动保存。自定义音色 ID 必须与创建时绑定的模型一致。

工具导出的音色参数直接填写到设置字段中；TTS 不支持 JSON 导入或设置页“重新加载”按钮。
本机设置保存在 `config/system/system-speech.json`。通知提示音有独立开关，默认开启，不需要请求 TTS。

仓库中的 Python 3.10+ 小工具 `tools/voice_design.py` 支持音色设计、复刻、测试合成和临时上传，使用标准库。

```powershell
python tools/voice_design.py --lang zh --help
```

## 内容配置与扩展

系统定义的路径为 `data/<命名空间>/system/system/<路径>.json`，名称与介绍使用客户端翻译键。例如创建
`data/example/system/system/daily.json`：

```json
{
  "name": "system.definition.default.name",
  "description": "system.definition.default.description",
  "modules": [
    "#example:basic",
    "example:daily"
  ]
}
```

在 `data/example/system/system_module/daily.json` 中定义 `example:daily`：

```json
{
  "server": { "type": "system:signin" },
  "client": "system:signin"
}
```

`server.type` 选择已注册的模块 Codec，模块参数与 `type` 平铺；当前内置类型使用无参数单例。
`client` 指向客户端本地实现，两个字段均可按需省略。

对应的模块 tag 放在 `data/example/tags/system/system_module/basic.json`：

```json
{
  "replace": false,
  "values": [
    "system:player",
    "system:items",
    "system:inbox",
    "system:shop",
    "system:lottery"
  ]
}
```

模块列表也支持带 `condition` 的对象，`id` 可填单个模块或 `#tag`。同一模块被多个条目匹配时，任一条件成立即可启用。
模块开关控制工作台入口、指令和实际执行权限，FTB
功能还要求安装对应依赖。详见[系统定义与模块 tag](docs/datapacks.md#系统定义)。

签到奖励、商城商品和抽奖奖池完全由数据包定义，默认内容也包含在模组内置数据包中：

```text
data/<命名空间>/system/signin_rewards/<路径>.json
data/<命名空间>/system/shop_offers/<路径>.json
data/<命名空间>/system/lottery_pools/<路径>.json
```

详细说明可以看GitHub仓库中的示例。

## 数据与隐私

### 配置文件

| 位置                                             | 内容                                          |
|------------------------------------------------|---------------------------------------------|
| 服务端 `config/system/system-agent.json`          | 执行预算、并发、每日模型上限与时区                           |
| 服务端 `config/system/system-model.json`          | 模型连接与提供商选项                                  |
| 服务端 `config/system/system-search.json`         | DeepSeek 搜索连接，默认与模型共享密钥环境变量                 |
| 服务端 `config/system/system-search-pricing.json` | Search / Fetch 按千次计费的单价                     |
| 服务端 `config/system/system-semantic.json`       | 本地意图路由、阈值、例句与缓存                             |
| 服务端 `config/system/system-advancements.json`   | 受信成就规则                                      |
| `<存档>/system-agent/<UUID>.json`                | 绑定、对话、偏好、奖励、订单与工作流记录                        |
| `<存档>/data/system/token_usage.dat`             | 独立持久化的玩家 AI / 联网用量，不随撤销或清空记忆删除              |
| 客户端 `config/system/speech-usage/<UUID>.json`   | 当前本地账号的 TTS / ASR 累计，跨服务器保留，不上传             |
| 客户端 `config/system/system-speech-pricing.json` | 本机 TTS / ASR 单价，缺失时复制内置默认文件                 |
| 客户端 `config/system/system-client.json`         | 界面模式偏好                                      |
| 客户端 `config/system/system-speech.json`         | 本地语音设置及界面中填写的密钥                             |
| 客户端 `config/system/system-asr.json`            | 本地识别开关、NLS AppKey、AccessKey ID / Secret 与地域 |

配置文件不会在客户端和服务端之间直接同步。系统定义与实际可用模块通过 Minecraft 网络同步；存档内的玩家业务文件由服务端管理，客户端无法直接修改。

### API 用量

AI 用量取自服务端模型响应，联网调用通过服务端网关分别累计 Search / Fetch 次数与估算费用；全服列表仅提供服务端 AI 与联网记录。
TTS / ASR 用量按当前客户端账号存本机，不上传 Minecraft 服务端，管理员也不能查看其他玩家的语音账本。

Search / Fetch 单价由服务端配置，初始化后通过 `/reload` 重载；语音单价由客户端配置，通过 F3+T 或重启重载。
ASR 默认按 3.5 元/千次成功识别估算。TTS 按对应模型返回的字符或 Token 计量，两者不重复收费。
空搜索结果不估价，超时、网络中断等未知结果单列，不能据此断言供应商未收费。改价不重算历史累计，实际费用以提供商账单为准。
详见[用量与计费](docs/token-usage.md)和[外部 API 请求清单](docs/external-apis.md)。
