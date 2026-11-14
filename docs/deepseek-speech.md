# DeepSeek 语音合成与识别

DeepSeek TTS 和 ASR 均使用客户端自己的 DeepSeek API Key，可以与模型、搜索共用同一份凭据。语音请求由客户端直接发送到官方 API，不经过 Minecraft 服务端；服务端管理员不能查看玩家的语音密钥和用量。

## 配置

1. 打开 `/system settings`，进入“API Key管理”，选择已有 DeepSeek 凭据，或按 **DeepSeek API Key** 类型新建。填写 `api_key`，留空自动识别 `SYSTEM_DEEPSEEK_API_KEY` / `DEEPSEEK_API_KEY`。
2. 在“语音交互”标签的“语音播报”或“语音识别”分组选择这份凭据，自动匹配服务和供应商，开启对应功能。两者可分别选择，不要求同时切换。

两种服务分别由各自 Manager 接受 `system:deepseek` 类型凭据，无需为凭据启用服务。默认语音绑定继续使用百炼 / NLS，不自动切换供应商。配置结构见[统一 API Key管理](api-management.md)。

选中 DeepSeek TTS 服务时，设置默认音色 `stella`。可将音色改为 `tide`；切回百炼时会恢复该服务的默认模型与音色，需要时重新填写自己的设计音色。切换同一类型的凭据不会重置音色。

## TTS

官方接口为 `POST https://api.deepseek.com/v1/tools/tts`，以 Bearer Key 鉴权。请求通过 Codec 编码，包含 `text`、`voice_id`、`format: "pcm"`、`filter_markdown: false`。助手已提供纯文本口播，不额外过滤 Markdown，使本地计费字符估算对应实际提交文本。

| 选项 | 行为 |
| --- | --- |
| `stella` | 海星，女声，默认 |
| `tide` | 暗潮，男声 |
| 模型 / 地域 / 语种 | API 不接收这些参数；页面隐藏对应选项，语种由服务自动识别 |
| PCM | 24 kHz、16 位有符号、小端、单声道 |

音频在 HTTP 响应体中流式返回，不是 SSE。直接追加到现有 `SpeechAudio`，约 200 ms 可播放音频后开始走原版 Voice 声道，保留现有 OpenAL 后备、音量、通知提示音与整句缓存。关闭界面、确认或替换通知、停止播报仍取消同一个 session，同时中断 HTTP 和播放；不自动重试或降级重发收费请求。

上游最多接受 10,000 个计费字符及 64 KiB 请求体；模组仍使用现有的最多约 500 个 UTF-16 单元口播，以及 8 MiB / 180 秒播放上限。合成在语音工作线程进行。无效状态码、错误 Content-Type、空 PCM、未对齐采样帧及中途网络错误都作为失败，不缓存半段音频。此处未接入分段输入文本的 WebSocket 协议：当前口播在提交前已有完整文本，HTTP 同样支持流式音频输出。

## ASR

官方接口为 `POST https://api.deepseek.com/v1/tools/asr`，以 Bearer Key 鉴权。**不支持流式识别或中间文本**：按键录音期间只采集本地音频，松开后才发送完整音频并等待最终结果。

继续复用现有 16 kHz、16-bit、单声道录音后端与可选 SVC 适配，最多录音 60 秒。PCM 只在内存中保存，封装为标准 WAV 后通过 multipart 的 `file` 字段上传，不落盘、不上传到 OSS，也不需要 NLS AppKey、AccessKey 或临时 Token。DeepSeek 本身支持最长 120 秒、最大 8 MiB 的 WAV / Ogg Opus，模组保留更小的已有录音上限。

返回通过 Codec 读取 `request_id`、`text` 和 `duration_ms`。语种自动检测，无需地域或语言参数。最终文字沿用草稿保护、自动发送开关与“先匹配通知按钮，未命中再进入聊天”的流程。录音、上传和响应解析都在后台；取消会中断采集及 HTTP，迟到结果不能写回新页面。结束录音后仍沿用 15 秒等待上限，整个 session 最长 90 秒；无收费请求自动重试。只采集或在上传前取消不增加 ASR 尝试次数。

## 用量与价格

以下价格依据 **2026-11-14** 官方使用指南，默认表采用当时公布的限时五折价。折扣可能变化，价格由客户端 `config/system/system-speech-pricing.json` 管理，不在线更新；实际以账单为准。

| 服务 | 刊例价 | 限时价（默认表） | 计量 |
| --- | --- | --- | --- |
| TTS | 3 元 / 万字符 | 1.5 元 / 万字符 | 汉字 2 个，其余 Unicode 码点 1 个 |
| ASR | 0.78 元 / 小时 | 0.39 元 / 小时 | 根据响应音频时长，每次向上取整到秒，至少 1 秒 |

TTS 二进制接口不返回 usage。只有完整合成成功后才按输入估算字符，保存为 `reported=false`，不伪装成供应商返回的用量，也不推算 Token；用量页“未报告”包含这些本地估算的请求。ASR 使用真实返回的 `duration_ms` 计时，不按 NLS 的次数单价收费；用量页新增返回音频秒数，显示原始总时长，费用逐次取整计算。

两个价格条目如下，可追加到已有价格文件的 `rates` 数组。已有价格文件不会被升级覆盖；未添加时仍可使用语音并记录用量，金额显示为未估价。

```json
[
  {"provider":"deepseek","model":"tts","region":"global","unit":"CHARACTERS","units":10000,"input":1.5,"output":0},
  {"provider":"deepseek","model":"sentence-recognition","region":"global","unit":"SECONDS","units":3600,"input":0.39,"output":0}
]
```

失败、取消、截断或无法解码且无已知用量的请求保留为未估价，不推断供应商免费。价格计算和保存仍在本地账本工作线程，后续改价不重算历史，缓存命中不增加调用或费用。

## 代码入口与依据

`speech/deepseek` 保存两个供应商；`data/speech/DeepSeekTtsMessage`、`DeepSeekAsrMessage` 保存协议 Codec；实际服务由 `SystemServices.TTS / ASR` 的 CredentialManager 从 `DeepSeekCredential` 构造，不再经过服务 ID 或字段 Map。通用 `PcmWav` 同时供 ASR 上传封装和 TTS 完整 WAV 接口使用。

- [TTS API](https://api-docs.deepseek.com/zh-cn/api/tts) · [TTS 指南与价格](https://api-docs.deepseek.com/zh-cn/guides/tts)
- [ASR API](https://api-docs.deepseek.com/zh-cn/api/asr) · [ASR 指南与价格](https://api-docs.deepseek.com/zh-cn/guides/asr)

协议和价格来自上述官方文档；本次仅编译及静态检查，没有启动 Minecraft、录音或调用收费接口，实际声音和供应商响应仍待游戏内验收。
