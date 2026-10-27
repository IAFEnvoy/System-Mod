# System

[English](README.md) | 简体中文

一个把网文“系统”带进 Minecraft 的 NeoForge 模组。Agent 部署在服务端，负责数据查询、任务、签到与商城交互；客户端提供完整工作台、简约说话框和不抢占操作的
HUD 通知，并可选用语音播报。

## 功能

- **玩家与物品查询**：查看本人状态、公开在线玩家名称，按名称或注册 ID 搜索物品。
- **FTB Quests 集成**：用简表展示可见、已解锁、可推进且未完成的任务，按编号或名称展开详情，再明确选择奖励与领取。完成与新解锁任务通过系统通知依次提示。
- **AI 签到**：模型从服务端批准的奖励池中选择奖励，成功后进入收件箱，玩家手动领取；模型不能编造物品或修改数量。
- **系统商城**：浏览商品、查看报价、确认以物易物。预留系统币支付接口，尚未实现系统币经济系统。
- **数据驱动抽奖**：浏览奖池、查看奖品权重与单次消耗，确认后单抽一次，奖励存入收件箱。数据包可定义物品消耗、免费抽奖或预留系统币支付。
- **内容配置与工作流**：数据包定义签到奖励、商城商品和抽奖奖池；服务端规则可在玩家完成成就后触发有界模块。
- **HUD 通知与语音**：提示不抢鼠标或键盘；可选阿里云流式 TTS 朗读正文，界面关闭、通知结束或切换时掐断对应语音 session。
- **用量与隐私控制**：按玩家统计 AI / TTS / ASR 用量，支持管理历史、关闭外部 AI，按 UUID 隔离数据并限制模型与工具执行。

## 快速开始

1. 安装本模组和前置。需要任务集成时安装 FTB Quests 及其依赖。
2. 正常启动一次服务端，或进入单人世界，在 `config/system/` 下生成各服务端配置文件。
3. 需要 AI 时，在服务端进程的启动环境中设置 `SYSTEM_DEEPSEEK_API_KEY`，然后重启。单人游戏需让 Minecraft
   进程继承该变量。配置文件保存环境变量名，不保存密钥。
4. 管理员执行 `/system grant <玩家>` 授予系统。玩家默认未绑定。
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

| 指令                                           | 用途                                 |
|----------------------------------------------|------------------------------------|
| `/system`                                    | 打开上次选择的界面                          |
| `/system full` / `/system simple`            | 打开完整 / 简约界面                        |
| `/system ask <内容>`                           | 提交自然语言请求                           |
| `/system player`                             | 查看本人状态                             |
| `/system usage` / `/system usage all`        | 查看个人用量 / 管理员查看全服用量                 |
| `/system grant [玩家]` / `/system revoke [玩家]` | 管理员授予 / 撤销并清除系统                    |
| `/system reset [玩家]`                         | 清除旧格式系统记录和绑定；累计用量保留，完成后需再次 `grant` |
| `/system routing`                            | 管理员查看路由诊断                          |
| `/system help`                               | 查看功能帮助                             |

通知显示在 HUD 底部。按原版聊天键（**默认 T**）进入聊天栏，即可点击“确定”或操作按钮。连续通知保持面板展开，仅淡出、替换和淡入内容。
确定、超时或切换通知时会立即掐断旧语音，不再等上一条读完才进入下一条；关闭助手界面同样结束其语音 session。

## 语音功能（可选）

**注意：所有语音功能均在客户端完成，并使用收费的 API**

目前所有语音功能均使用阿里云的API，后续将接入更多供应商。

### 语音输入

**此功能需要独立的阿里云 NLS 项目 AppKey 与临时 Token。**

**建议安装 Simple Voice Chat 获得更好的效果。**

语音输入默认关闭。通过 `/asr` 开启后，默认按住 **F8** 录音、松开结束；游戏中按键会打开助手，也可以使用 `/listen`
和“语音输入”按钮。最终识别结果填入草稿，Enter 发送；也可开启“识别后立即发送”；最长 60 秒，关闭界面同步取消。

### 语音输出

**此功能需要独立的阿里云百炼 API Key 并选择对应地域。**

从完整工作台进入“设置 → 语音播报”，或在助手输入框输入 `/tts`。

1. 填写阿里云百炼 API Key 并选择对应地域，或给客户端进程设置 `DASHSCOPE_API_KEY` 环境变量。
2. 设置模型、音色、语言与音量。默认模型 `qwen3-tts-flash`、音色 `Cherry`、语言跟随游戏。
3. 试听后开启自动朗读并保存。自定义音色 ID 必须与创建时绑定的模型一致。

外部修改 `config/system/system-speech.json` 后，可点击“重新加载配置”更新语音设置并丢弃本页未保存的修改。工具导出的音色参数需先合并到该文件，保留密钥和本地偏好。

仓库中的 Python 3.10+ 小工具 `tools/voice_design.py` 支持音色设计、复刻、测试合成和临时上传，使用标准库。

```powershell
python tools/voice_design.py --lang zh --help
```

## 内容配置与扩展

签到奖励、商城商品和抽奖奖池完全由数据包定义，默认内容也包含在模组内置数据包中：

```text
data/<命名空间>/system/signin_rewards/<路径>.json
data/<命名空间>/system/shop_offers/<路径>.json
data/<命名空间>/system/lottery_pools/<路径>.json
```

详细说明可以看GitHub仓库中的示例。

## 数据与隐私

### 配置文件

| 位置                                             | 内容                                     |
|------------------------------------------------|----------------------------------------|
| 服务端 `config/system/system-agent.json`          | 执行预算、并发、每日模型上限与时区                      |
| 服务端 `config/system/system-model.json`          | 模型连接与提供商选项                             |
| 服务端 `config/system/system-semantic.json`       | 本地意图路由、阈值、例句与缓存                        |
| 服务端 `config/system/system-advancements.json`   | 受信成就规则                                 |
| 服务端 `config/system/system-speech-pricing.json` | TTS 单价，缺失时复制内置默认文件，服务端启动时读取            |
| `<存档>/system-agent/<UUID>.json`                | 绑定、对话、偏好、奖励、订单与工作流记录                   |
| `<存档>/data/system/token_usage.dat`             | 独立持久化的玩家 AI / TTS / ASR 用量，不随撤销或清空记忆删除 |
| 客户端 `config/system/system-client.json`         | 界面模式偏好                                 |
| 客户端 `config/system/system-speech.json`         | 本地语音设置及界面中填写的密钥                        |
| 客户端 `config/system/system-asr.json`            | 本地识别开关、NLS AppKey、临时 Token 与地域         |

所有配置均不会在客户端和服务端之间同步。存档内的玩家数据文件由服务端管理，客户端无法直接修改。

### API用量

AI 用量取自服务端 API 响应；TTS 用量由客户端回报，仅供参考，不能作为支付或发奖依据。费用按服务端收费标准配置估算，修改后通过服务端
`/reload` 或重启生效，不重算历史累计金额，以提供商账单为准。
