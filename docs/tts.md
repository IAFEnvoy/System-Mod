# 语音合成与音色设计

语音输入另外使用 NLS 一句话识别，通过 `/settings` 打开统一设置后选择“语音输入”标签，需要独立 AppKey / NLS Token，详见[语音识别](asr.md)。

## 模组内使用

完整界面点击“设置”，或在**助手的输入框**输入 `/settings`，再选择“语音播报”标签。设置指令只有这一处入口，是客户端界面指令，不是原版聊天栏中的服务器命令。所有标签继续使用唯一的 `assistant.css`，输入法处理与助手输入框共用；控件修改后立即自动保存，切换标签或关闭页面无需额外保存，离开播报标签会停止试听。

1. 填写阿里云百炼 API Key，选择与密钥一致的地域（默认北京）。也可把密钥留空，在启动客户端前设置 `DASHSCOPE_API_KEY`。有密钥后才开放音色设置。
2. 默认模型为 **`qwen3-tts-flash`**，默认音色 **`Cherry`**。也可填 `Serena`、`Ethan` 等[系统音色](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list)。Workspace ID 可留空使用兼容域名；填写后使用业务空间专属域名。
3. 设置语种和音量，点击“试听”。参数已经自动保存，试听不要求先开启自动朗读。填写过程中允许保留不完整的凭据，完整参数在试听或实际合成时校验。
4. 开启“自动朗读”即自动保存；“停止朗读”取消当前合成和播放。

同页的“通知音效”默认开启，独立于“自动朗读”，不需要 API Key。关闭后只取消通知展开时的本地提示音，正文继续按自动朗读设置播放，不再等待提示音。对应客户端 `config/system/system-speech.json` 的 `notificationSoundEnabled`，已有文件缺少该字段时默认开启；切换后立即保存。

语音默认关闭。`config/system/system-speech.json` 是**客户端本地**配置，和服务器 `system-agent.json`、界面模式配置分开。界面填写后保存的密钥以明文保存在该本地文件；环境变量回退的密钥不会写入文件或填进页面。密钥不发送到 MC 服务端、不进对话、附件或存档。开启后，新到达的系统消息正文会发送给所选语音服务合成，聊天栏、其他模组与玩家聊天不在此监听范围。

“跟随游戏语言”按客户端当前语言映射语种（兼容 `en_us` 与 `en-US` 等写法），不支持的语种回退 `Auto`。也可选择自动识别或明确指定语种。Qwen3 使用 `input.language_type`；Qwen-Audio-TTS 使用官方 `input.language_hints`，例如英语发送 `["en"]`、中文发送 `["zh"]`，自动识别时省略该字段。流式与非流式共用映射。固定语言键先通过客户端 `Component.translatable` 翻译为文本，然后合成；中英文配置页面、预览文案与错误提示均有语言键。

中英混合建议选择“自动识别”，整段英文可选择“英语”。“跟随游戏语言”在中文客户端仍会提供中文提示，并不等同于自动检测正文。按照 [Qwen-Audio-TTS HTTP API](https://help.aliyun.com/zh/model-studio/qwen-audio-tts-http-api)，目标语种提示可改善数字、缩写和符号读法，且与复刻样本的源语种独立。旧版 Qwen-Audio 适配器没有发送任何语种提示，因此旧配置中的 `Chinese` 对该协议实际未生效；不能仅凭它断定 `Dev` 逐字母朗读的原因。当前不拆分英文、不强制改写名字或缩写；具体读音仍需按实际音色试听确认。

语音按界面或当前通知持有独立 session，完整界面、简约模式与 HUD 都通过 `speech/tts/SpeechText` 生成口播。普通结果只读主文案（翻译后的 `messageKey` 与非空 `text`），不拼接通用卡片字段或按钮。任务完成、解锁和详情按设置补充任务内容；初次概览只读任务总数，新手礼包只读通用待领取提示，不读来源任务名或逐项物品。礼包领取成功静默刷新；明确提交后任务完成也不另读提交回执，统一由完成事件通知。绑定播报保留“叮！”、实际系统名称与介绍，左上角固定“系统”不参与播报。帮助只提示手册已展开，历史和状态同步保持静音，不推断奖励已到账。

设置页提供“播报任务详情”“播报任务奖励”“播报任务背景”三个开关，对应客户端 `system-speech.json` 的 `questRequirements`、`questRewards`、`questDescription`，默认前两项开启、背景关闭。关闭详情时只读任务名称及允许播报的奖励；开启后才追加要求，背景还必须单独开启且原文确有叙事。空描述、制作或提交步骤、按键说明、机制解释和无法确定的内容均不读作背景，混合内容只提取已有叙事，不补写剧情。普通物品目标、数量范围、经验、自选和随机奖励直接格式化，可选目标明确标注；物品名保留原版 Component，在客户端语言环境展开。描述始终遵守 FTB 的完成前隐藏设置。

只有实际开始且允许朗读时，客户端才用独立请求发送任务 ID 和三个开关；不开朗读、静音或没有 TTS 密钥时不发起任务整理。服务端在主线程重新检查可见性及详情权限，读取可信任务定义，客户端不能提供模型提示词或原始任务文本。常见类型不调用模型；特殊任务/奖励只把类型、原生标题、目标数量和已知限制交给一次模型请求，不发送背包、玩家进度或对话。请求通过 `ExecutionContext.model` 与统一 `ModelGateway`，沿用外部 AI 偏好、玩家日额度、并发限制和用量统计；关闭外部 AI 时仍可复用现有文案缓存，未缓存部分使用原生文本。

特殊目标与奖励的整理片段按源文本、语言、模型地址/名称及渲染后的提示词缓存，仍使用每服务端实例最多 512 条的内存 LRU；相同批次并发合并，重启清空。背景筛选另由 `QuestBackgrounds` 管理，保存到存档根目录 `system-quest-backgrounds.json`，以任务唯一 ID 和语言定位，并核对任务标题、原始描述、提示词、模型与接口的内容指纹。成功的“没有背景”也持久化，失败仅抑制 30 秒重试；相同任务并发共享生成。命中后重新读取任务并核对可见性与源内容，不能绕过隐藏条件。提示词分别为 `data/system/system/prompts/quest_narration.txt` 和 `quest_background.txt`，支持数据包覆盖与 `/reload`。两种缓存均不保存玩家进度、领奖状态或可执行操作。

查询、播报和文案整理均不提交任务。手动目标只能在玩家明确发送 `提交 1`、`提交 任务名`、`/submit <number|name>` 或原版聊天命令 `/system submit <number|name>` 后处理；编号对应最近一次任务列表。提交工具支持原生消耗物品、勾选、经验及手动自定义按钮目标，其他专用输入仍在 FTB 中操作；保留暂停、前置、顺序和 FTB 自动奖励，一次指令不会进入自动重置后的下一轮。礼包仍须明确领取。播报等待整理只占用工作线程，客户端最多等 8 秒，超时只读原短提示；通知切换/关闭会取消本地等待与音频，迟到结果不重播。服务端已开始的只读整理可完成缓存和实际计量，不保证撤回已经发出的云端请求。

界面收到新回复时替换旧朗读，提交下一条请求、关闭界面、切换模式或进入其他页面时取消原 session。绑定提示、FTB 完成/解锁等 HUD 消息仍由最多 32 条的通知队列依次显示，但不再独立堆积语音：轮到当前通知时提交一次朗读，通知超时、提前确定、执行操作、被其他界面或 F1 隐藏时立即取消其请求与播放，不等待语音播完再切换下一条。恢复被隐藏的通知只恢复文字，不补播；历史、状态同步与文档重建也不重播。试听替换当前朗读；“停止朗读”停止当前音频并清除待启动语音，不清空文字通知队列。

HUD 通知框从隐藏状态首次展开时，播放一次注册的 `system:notification` 提示音，再播放正文口播。连续消息保持展开，只切换正文播报，不重复提示音；收缩途中收到消息而反向展开也不重播，完全收起后下一轮通知才重新提示。音源为 `assets/system/sounds/notification.ogg`，由提供的 MP3 转为单声道 Ogg Vorbis，约 1.78 秒、16 KiB；`assets/system/sounds.json` 定义并预加载该音效，字幕支持中英文。提示音使用 Voice 分类，增益为页面播报音量的 75%，再叠加游戏主音量与 Voice 音量。关闭自动朗读、没有密钥、正文静音或云端冷却期间仍播放本地提示音；播报音量为零、主音量或 Voice 静音时二者均不播放。

提示音和正文共用当前通知的 session。提示音播放期间并行准备流式合成或读取缓存，但通过原版声音实例的实际活动状态等待其结束后才启动正文，不按固定时长延迟，不额外请求云端或增加用量。提示音缺失或无法启动时继续原播报流程；提前确认、关闭、切换消息、隐藏界面、断线和声音重载会同时停止提示音、在途合成及正文，延迟解码回调也不能让旧提示音继续播放。通知恢复、文档重建不补播提示音；助手界面的普通回复和设置页试听保持原有直接播报方式。

播报转为客户端语言的纯文本，最多 500 个 UTF-16 单元（保护代理对），另可附加省略号。HUD 的 `Message.text` 保存完整可视正文，长正文与礼包物品区分别滚动，不再按 360 码点/6 行截断；`Message.speechText` 独立保存口播。原来的四参数构造默认读自定义通知正文，也可通过五参数构造指定口播或留空静音。音频优先走 MC 的 **Voice/语音** 音量分类，同时受主音量和页面音量控制；静音时不请求合成，播放中静音会结束当前音频，不触发后备播放。合成/两种播放均失败不影响文字、任务或奖励，也不重试同一条消息；清空语音队列并暂停自动朗读 60 秒，之后允许后续新消息尝试，手动试听仍可立即重试。

### 可选整句音频缓存

语音设置中“复用已合成语音”默认关闭，对应 `config/system/system-speech.json` 的 `cacheEnabled: false`；旧配置缺少字段时同样关闭。开启后，相同合成输入先查本机音频，未命中才调用供应商，并保持原有 SSE 首包播放。供应商成功结束、PCM 完整且会话没有取消时才写缓存，失败或中途关闭不会留下半段可播放缓存。试听也遵循当前表单的开关。

缓存目录为 `config/system/tts-cache/`，使用带版本、PCM 格式和 SHA-256 校验的文件；最多 64 MiB / 256 条，写入时按最近使用时间淘汰。键包含供应商、模型、音色、地域、业务空间、实际语种、实际朗读文本和凭据，文件名仅保存摘要，不保存明文密钥或文本。更换密钥会使用另一组键，页面音量不影响缓存。音频本身保存在本机磁盘，可能含玩家名字；关闭缓存不会删除旧音频，删除整个缓存目录即可清空。

读写与校验全部在原合成工作线程进行；损坏或不可读缓存按未命中处理，写入失败不打断播报。命中后直接走原版 / OpenAL 的同一会话管线，仍可立即掐断，且不发起云端用量报告或增加 TTS 请求统计。缓存仅复用音频，不缓存任务、奖励或业务结果。

### SSE 流式合成与播放

阿里云现在默认使用 SSE 流式输出。`qwen3-tts-flash` 与 Qwen-Audio-TTS 都发送 `X-DashScope-SSE: enable`，后者指定 `format=pcm`、`sample_rate=24000`；两种协议均消费 `output.audio.data` 的 Base64 PCM（24 kHz、16-bit、小端、单声道）。完整文本仍只提交一次，不拆句多次调用；收到音频后逐块追加，不等待整段合成或下载最终 OSS URL。参考 [Qwen3-TTS API](https://help.aliyun.com/zh/model-studio/qwen-tts-api) 和 [Qwen-Audio-TTS HTTP API](https://help.aliyun.com/zh/model-studio/qwen-audio-tts-http-api)。

`SpeechAudio` 以限长分段内存保存 PCM，累计约 **200ms 音频**即可开始播放；这是待播放音频长度，不是固定等待 200ms，短句完成时即使不足阈值也立即开始。云端首包与网络耗时仍然存在，不能保证从文字显示到声音出现的固定延迟。原版路径通过 `SoundManager.play` 和 NeoForge `SoundInstance.getStream`，每次读取至多约 50ms 的可用 PCM；网络暂时无数据时给一小段静音保活，结束标记或取消后才返回 EOF。Minecraft 当前 `Channel.pumpBuffers` 会显式跳过 `null`，因此 `null` 是流结束信号，不是传给 OpenAL 的音频数据；所有结束判断统一由 `SpeechAudio.Cursor` 处理，声音线程不等待网络。最多保存 8MiB / 180 秒 PCM，跨 SSE 片段的半帧留待下个片段补齐，结束时必须帧对齐。

`TtsProvider.supportsStreaming()` 默认 false。新供应商可实现 `synthesizeStreaming(Request, AudioSink, Consumer<Usage>)`，通过 AudioSink 提供采样率、声道及 16-bit 小端 PCM，并报告累计 usage。未实现流式的旧供应商沿用完整 WAV 合成，在工作线程解码后进入同一播放管线；阿里云流式失败不会自动重发非流式请求，避免重复消费。

`HttpTransport.stream` 对整个流施加 90 秒等待期限、16MiB 总响应限制；`ServerSentEvents` 按 UTF-8 增量组帧，支持 CR/LF、注释、多行 data、BOM 与跨网络块的字符，单事件上限 2MiB。校验 HTTP 状态、SSE Content-Type、JSON 错误和最终 `finish_reason=stop`，播放不会把上游正文或签名 URL 暴露给玩家。session 结束、停止朗读、断线、撤销或资源重载取消在途请求并释放 PCM，过期回调受 session 和 generation 保护。已收到的 usage 仍计入发起请求的本地账号；取消本地连接不保证云端立即停止生成或计费。

### 原版播放与 OpenAL 后备

用 `PlayStreamingSourceEvent` 在声音线程核对实际声源是否已进入播放状态；原版返回 `NOT_STARTED`、启动抛错、声源未播放或 3 秒内未启动时，只切换一次 OpenAL。原版成功开始后按自然结束处理，不在正常播完时重播。

后备路径参考 `E:/Java/BGMPlayer/common/src/main/java/com/iafenvoy/bgm/player/music/AudioPlayer.java`，在 MC 当前 OpenAL context 内创建声源，以 **4 个可循环 buffer** 上传、播放并补充同一 PCM 流，绕过 MC 声源池。没有数据时等待后续 tick，缓冲耗尽后有新数据便从原进度恢复，不重新请求或重播开头；仅在生产者结束且缓冲排空后结束。没有可用 context 或硬件声源时报告播放失败。状态栏区分原版播放与“正在朗读（OpenAL）”。

进入后备前取消原版流与延迟启动回调，避免双重播放；后备从独立 PCM 游标读取，原版预读不会吞掉开头。后备音量每 tick 同步页面音量 × 主音量 × Voice 音量，暂停单人世界时暂停、恢复后继续。source 与 buffer 在同一渲染线程创建、查询和销毁，播放结束/取消释放 source/buffer。客户端专用 `SpeechSoundEngineMixin` 在原版 `stopAll` / `emergencyShutdown` 前清理语音，因此退出世界、撤销绑定、关闭自动朗读、全局停止声音、设备切换、资源重载及退出客户端均不会留下后备声源或延迟回放。声音系统重载会丢弃旧语音队列，不补播。

## 设计音色与模型匹配

依据当前官方文档，`qwen3-tts-flash` 的非实时 HTTP 接口使用系统音色；[声音设计](https://help.aliyun.com/zh/model-studio/voice-design-api-references)的 `voice-enrollment` 创建 Qwen-Audio-TTS / CosyVoice 音色，返回的 `voice_id` **绑定创建时的 `target_model`**。不能把任意设计音色直接填入默认模型。

本次额外接入 Qwen-Audio-TTS 的[非实时 HTTP 接口](https://help.aliyun.com/zh/model-studio/qwen-audio-tts-http-api)，支持 `qwen-audio-3.1-tts-flash`、`qwen-audio-3.1-tts-plus`、`qwen-audio-3.0-tts-flash`、`qwen-audio-3.0-tts-plus`。可使用业务空间专属域名，也可在 Workspace ID 留空时使用兼容域名。默认 `qwen3-tts-flash` 不变。CosyVoice 合成不在本次实现范围内。

工具导出的 `system-voice.json` 包含 provider、region、workspace、model、voice、language，**不包含密钥**，供工具继续合成或人工查阅。在模组中通过 `/settings` 的“语音播报”标签手工填写地域、Workspace ID、模型、音色 ID 和语种，修改自动保存，再点击试听。模组不提供 JSON 导入或页面内重新加载按钮；常规持久化仍使用 `config/system/system-speech.json`。音色创建后可能处于审核中；用工具 `query` 查看状态，`OK` 后才能正常使用。

## CosyVoice 与当前模型价格对比

2026-10-25 核对的北京地域[官方公开单价](https://help.aliyun.com/zh/model-studio/model-pricing)，不含免费额度和折扣：

| 模型 | 合成价格 |
| --- | --- |
| 当前复刻音色的 `qwen-audio-3.1-tts-flash` | 输入 1.5 元 / 百万 Token，输出 12 元 / 百万 Token |
| `cosyvoice-v3.5-flash` | 0.8 元 / 万字符 |
| `cosyvoice-v3.5-plus` | 1.5 元 / 万字符 |
| `cosyvoice-v3-flash` | 1 元 / 万字符 |
| `cosyvoice-v3-plus` / `cosyvoice-v2` | 2 元 / 万字符 |

CosyVoice 只按输入文本计费，一个汉字算 2 个计费字符，其他字母、标点和空格等算 1 个。新加坡的 `cosyvoice-v3-plus` / `cosyvoice-v3-flash` 分别为 1.9082 / 0.9541 元每万字符；上表 3.5 和 v2 未列出新加坡价格，不能直接套用北京价。

按[声音复刻说明](https://help.aliyun.com/zh/model-studio/voice-cloning-user-guide)，Qwen-Audio-TTS 与 CosyVoice 的音色复刻创建免费，使用音色合成时按目标模型收费。不要与另一项 `qwen-voice-enrollment` 的按音色收费混淆。当前 `system-test1` 绑定 Audio 模型，若改用 CosyVoice 需要为目标模型重新创建音色。

此前测试实际返回输入 37、输出 81、总量 118 Token 和 69 characters；当前 Audio 模型价格约 0.0010275 元。相同计费字符数的 CosyVoice 3.5 Flash 约 0.00552 元，仅说明这段样本的价格差异，不能据此认定所有文本都按相同比例计费。Java 模组目前尚未接入 CosyVoice 合成，此表仅作选型参考。

## Python 小工具

`tools/voice_design.py` 使用 Python 3.10+ 标准库，无需 pip 包。`--lang zh/en` 切换帮助和工具提示语言。API Key 从 `DASHSCOPE_API_KEY` 读取，缺少时隐藏输入；不支持把密钥直接放在命令行参数里。工具不自动重试收费请求。

先在当前终端设置百炼密钥，下面的 `YOUR_WORKSPACE_ID` 替换为实际业务空间 ID；也可省略 `--workspace` 使用兼容域名。`--workspace` 也可由 `DASHSCOPE_WORKSPACE_ID` 提供；通用选项放在子命令前：

```powershell
python tools/voice_design.py --lang zh --help
python tools/voice_design.py --lang zh --workspace YOUR_WORKSPACE_ID design --prompt "清冷、克制的年轻女性电子助手，吐字清晰，语气平稳，带轻微机械感" --prefix system
```

默认目标 `qwen-audio-3.1-tts-flash`，默认中文预览。声音描述 1～500 字符，预览文本 15～200 字符，前缀 1～10 位英文字母/数字。英文设计需同时提供英文预览：

```powershell
python tools/voice_design.py --lang en --workspace YOUR_WORKSPACE_ID design --prompt "A calm, clear young female virtual assistant" --voice-language en --preview-text "Host, the system link is established. A new quest is ready."
python tools/voice_design.py --lang zh --workspace YOUR_WORKSPACE_ID list
python tools/voice_design.py --lang zh --workspace YOUR_WORKSPACE_ID query VOICE_ID
python tools/voice_design.py --lang zh --workspace YOUR_WORKSPACE_ID delete VOICE_ID
```

当前官方声音设计文档列出的这些模型使用北京地域，工具的音色操作也限制北京。`--region singapore` 用于临时上传；不能混用不同地域的密钥或音色。`delete` 会要求输入完整音色 ID 确认。

`design` 在 `voice-output/<时间>-<随机标识>/` 下输出 `preview.wav` 与 `system-voice.json`；可用 `--output` 指定输出根目录。工具先建立输出位置再调用 API，避免因为路径不可写而创建无法保存的音色。先保存音色 ID 和配置，再解码预览；若预览异常，不要重复创建收费音色，先用 `query` 查询。预览 WAV 用本地播放器打开即可。

### 从录音复刻音色

`clone` 接受 16-bit PCM WAV（至少 5 秒、不超过 60 秒、采样率至少 16 kHz、单/双声道、不超过 10 MiB）。官方推荐 10～20 秒清晰连续人声。浮点 WAV 需先转成 16-bit PCM；不会修改原音频。复刻也是 `voice-enrollment/create_voice`，传入参考音频 `audio`，不是文字音色设计。

```powershell
python tools/voice_design.py --lang zh --config run-test-client/config/system/system-speech.json clone voice-input/system-test1.wav --id system-test1
```

`--config` 只读取指定客户端配置中的密钥、地域和 Workspace ID，不输出密钥；没有配置文件也可继续使用环境变量。默认目标模型为 `qwen-audio-3.1-tts-flash`。

阿里云自动生成音色 ID，不能自定义为 `system-test1`。`--id` 保留本地别名，写入 `voice-output/system-test1/system-voice.json`，其中 `voice` 为真实云端 ID。云端前缀仅支持 10 位以内英文字母数字；未显式指定 `--prefix` 时从本地 ID 生成，`system-test1` 对应 `systemtes1`。将导出的音色参数填写到模组的语音播报设置中，修改自动保存。

提交前落盘 `submission.json`，保留本地 ID、前缀、目标模型、音频摘要及提交状态，不保存密钥或 Base64 音频。若网络失败导致结果不明，会保留记录，重复使用该 ID 会拒绝再次提交；先按前缀 `list` 查询，避免重复收费。返回音色 ID 后用 `query` 确认状态为 `OK`。不会为验证自动请求额外收费的语音合成。

需要试听复刻结果时显式运行 `synthesize`，它会按目标模型调用一次合成并下载 WAV，同时保存文本、请求 ID 与用量元数据：

```powershell
python tools/voice_design.py --lang zh synthesize --profile voice-output/system-test1/system-voice.json --output voice-output/system-test1/test.wav
```

输出文件存在时拒绝覆盖，避免误操作重复合成；失败不自动重试。音频下载不携带 Bearer，HTTP OSS 地址升级为 HTTPS。音频生成后，元数据去除下载签名 URL 和内联音频。

部分服务返回的 WAV 沿用流式占位长度，播放器可能误报数小时。工具按实际 PCM 字节重建标准 WAV 头，不改变采样数据，也不重新请求合成。

## 临时上传

文字音色设计与普通文本合成都**不需要上传文件**。工具额外提供官方[临时上传](https://help.aliyun.com/zh/model-studio/get-temporary-file-url)流程，供后续参考音频/复刻接入使用：

```powershell
python tools/voice_design.py --lang zh upload reference.wav --model voice-enrollment
```

先获取指定模型的上传策略，再用签名表单向 OSS 上传，返回 `oss://...`，有效期 **48 小时**；策略本身短时有效，立即使用。后续引用这个 URL 的模型请求必须设置 **`X-DashScope-OssResourceResolve: enable`**。上传不会创建或复刻音色；模型是否接受对应的音频格式、时长等仍以该模型接口为准。

上传工具限制参考音频不超过 20 MiB，并遵守策略里更小的上限；文件名随机化，file 表单项最后发送。Bearer 密钥只发百炼，OSS 上传只用签名策略。签名策略、音频及密钥不写日志；临时 URL 按工具用途输出。

## 接口与边界

- `com.iafenvoy.system.speech.tts.TtsProvider` 是无 MC 依赖的合成接口，保留 WAV 字节接口，并提供可选的流式 PCM 回调。供应商由 `speech.TtsProviders` 的普通工厂 Map 管理，内置键为 `aliyun`。扩展在客户端初始化时调用 `TtsProviders.register(name, factory)`，不参与 MC 注册表生命周期；`TtsProviders.create(provider)` 按配置字符串精确查找并创建独占实例，不自动补命名空间。收费表另将短名补为 `system:` 后匹配；新供应商自行校验协议参数。完整示例见[注册表速查](registries.md)。
- `AliyunTtsProvider` 通过共享的 `HttpTransport` 实现两种阿里云 HTTP 协议，MC 默认使用上述 SSE 路径。保留的非流式接口总响应超时 45 秒，音频下载 25 秒；JSON 最大 12MiB，音频最大 8MiB。无重定向、无自动重试。非流式下载仅接受阿里云 OSS 音频域名，旧 HTTP URL 升级 HTTPS，下载不携带 Bearer。
- `AssistantSpeech.Session` 由界面和当前通知持有；全局只保留当前朗读及一个待启动项，新朗读替换旧项，文字队列由 `AssistantMessages` 管理。关闭 session 同时取消在途 Future、PCM 和播放，已关闭的 session 不能重新提交。单个合成工作线程及单个工作等待槽保持有界；请求、SSE 解析及旧供应商 WAV 解码不占 MC 线程，旧代次结果不得重新开始播放。
- `SpeechAudio` 保存有界的 16-bit 小端、单/双声道 PCM；`SpeechSound` 提供原版非阻塞读取，`SpeechPlayback` 管理原版优先/单次后备，`OpenAlSpeechPlayer` 按游标向循环 buffer 队列补充音频。仅有客户端声音生命周期 mixin，不需要资源包、外部播放器或音频库。
- `AssistantSpeech.status()`、`SpeechPlayback` 和设置页共用 `SpeechState` 枚举，统一合成、播放及错误状态；供应商错误码在入口转换，界面通过 `translationKey()` 读取现有双语文案。
- 没有新增 Java 依赖、模型文件或原生运行库。TTS 由客户端独立调用，用量只写入本机 `config/system/speech-usage/<客户端账号UUID>.json`；用量、密钥、正文和音色 ID 均不上传 MC 服务端。`/system usage` 同页展示服务端 AI 和本人的本地 TTS / ASR，用量跨服务器累计，revoke 不清除。累计口径与官方单价见[用量统计](token-usage.md)。
- 收费标准使用客户端 `config/system/system-speech-pricing.json`；缺失时复制 JAR 内 `defaults/system-speech-pricing.json` 后加载，保留已有配置。修改后通过客户端 F3+T 或重启生效，历史累计费用不重算；详细字段见[收费标准配置](token-usage.md#收费标准配置)。

## 验证范围

首次接入执行过 Java 编译、testMod 源集编译与 JAR 打包、Python AST 语法检查和 CLI 帮助、双语键集合与模板绑定静态检查，当时没有调用收费 API。

2026-10-25 后续按用户指令实际完成一次 Qwen-Audio-TTS 音频复刻：本地 ID `system-test1`，目标模型 `qwen-audio-3.1-tts-flash`，云端状态 `OK`；兼容域名下查询、复刻及测试语音合成成功。参考音频在本地转换为 24 kHz、单声道、16-bit PCM WAV，保留原文件。测试输出 6.48 秒，WAV 头按实际 PCM 长度修正。结果位于忽略提交的 `voice-output/system-test1/`，没有把密钥写入结果。此验收覆盖线上复刻与下载，不代表已验收 MC 内音频播放或临时上传。

正文队列与 OpenAL 后备这轮只执行 Java 编译、testMod 源集编译、JAR 打包和静态检查，依据当前 Minecraft / NeoForge / LWJGL 依赖源码确认签名与声音生命周期。**没有运行测试、启动游戏或再次调用收费 API**；原版实际出声、故障切换和游戏内资源回收尚未做运行验收。

SSE 流式增强已通过 Java / testMod 源集编译和 JAR 打包；依据官方流式协议以及当前 `Channel.attachBufferStream`、`pumpBuffers`、`AudioStream.read` 源码实现分块播放。未运行测试、启动游戏或调用收费接口，首包延迟与连续播放效果尚未实测，不给出延迟改善的实测数值。

语音 session 与通知切换调整已通过 `compileJava testModClasses jar` 编译和打包；静态检查了界面关闭、通知确认/超时/隐藏、代次取消及延迟回调路径。未运行测试、启动游戏或调用收费接口，中途掐断和通知连续切换仍未做游戏内验收。

2026-10-26 整句音频缓存与 Qwen-Audio 语种提示已通过 `compileJava testModClasses jar --console=plain` 编译和打包；静态检查了默认关闭、旧配置缺字段回退、表单与音色导入保留开关、命中不报告云端用量、完整 PCM 写入和取消保护，中英文语言键均为 377 个且集合一致。依据官方 HTTP 文档确认 `input.language_hints`，没有启动游戏、运行测试或调用收费 TTS API；缓存的游戏内命中效果与 `Dev` 实际读音尚未试听验收。
