# AI / 联网 / TTS / ASR 用量统计

任务播报中无法直接格式化的特殊类型，由服务端 `QuestNarration` 通过同一个 `ExecutionContext.model` / `ModelGateway` 发起整理，实际 Token 计入触发该请求的玩家。常见模板、文案缓存命中和合并后等待结果的其他请求不增加模型调用；整理失败或通知提前关闭时，已发生的模型用量仍保留。文案缓存与可选的客户端整句音频缓存相互独立。

同页另显示 ASR 尝试次数和估算费用，以及失败/取消、未完成、未估价计数。按用户指定的 3.5 元/千次估算成功识别，费用通过现有价格配置的 `CALLS` 条目控制；已有配置需自行加入 ASR 条目，不自动覆盖。ASR 无 Token 字段，不混入 AI / TTS Token。详情见[语音识别与计费](asr.md#用量与费用)。

`/system usage` 打开个人用量界面，不要求当前已绑定系统。完整工作台侧栏有“用量统计”入口；在完整或简约模式输入 `/usage` 或“用量统计”也可打开。管理员可通过 `/system usage all` 或界面内“所有玩家”切换到全服列表；普通玩家的请求即使伪造全服参数，也会由服务端拒绝。

界面沿用唯一的 `assistant.css`，同页显示服务端 AI 输入、输出、总 Token、缓存命中量、Search/Fetch 计费次数与估价，以及当前客户端账号的 TTS / ASR 调用和费用。AI、TTS 分别显示尝试、失败和用量未知次数，语音额外显示未估价次数。玩家明细包含服务端 AI 与联网调用、名称、UUID 和最近调用时间，不含其他玩家的语音数据。全服按 AI 总 Token 降序，每页 8 位，包含已离线或被撤销系统的玩家；刷新、翻页与查看统计不调用 AI，也不写入对话历史。字符与 Token 不相加。

界面标题为“用量统计”。仪表盘按 AI、联网、TTS、ASR 分成独立面板，调用/失败计数置于所属面板下方：AI 依次显示总 Token、输入、输出、缓存命中；TTS 依次显示估算费用、计费字符、总 Token、输入、输出；ASR 显示尝试次数与估算费用。TTS / ASR 标注为“我的本地”，即使切换“全服用量”也只显示当前账号自己的语音记录，绝不混入服务端汇总或其他玩家的行。语音统计在 API 回调后自动刷新，服务端 AI 读取失败不影响本地语音数据展示。

## 统一计量入口

所有模型请求经过 `ModelGateway.complete(player, ...)`，它负责调用网络适配器并记录一次调用的归属、实际 usage 和调用结果。路由、签到和扩展模块使用 `ExecutionContext.model(...)`，由上下文校验授权与预算后进入同一个网关；模块不自行累计 token。`AgentEngine.model()` 暴露的是网关，不是底层 HTTP 客户端。

`OpenAiCompatibleClient` 在校验 `choices`、`finish_reason` 或模型正文前读取 HTTP 响应的 `usage`，因此截断、正文 JSON 错误或后续业务拒绝不会丢掉已经返回的 token 用量。网关的 `finally` 只计一次，发生取消或 `revoke` 后也不依赖原玩家业务文件。规则命中、本地 TF-IDF、精确意图缓存、纯工具执行与界面刷新没有模型调用，不计 token。提供商配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

统计口径：

| 字段 | 含义 |
| --- | --- |
| 输入 Token | API `prompt_tokens` |
| 输出 Token | API `completion_tokens` |
| 总 Token | API `total_tokens` |
| 缓存命中 Token | 优先读取 `prompt_tokens_details.cached_tokens`，缺省时兼容 DeepSeek 的 `prompt_cache_hit_tokens`；它是输入的一部分，不重复加进总量 |
| 模型尝试 | 进入网关的调用次数，包括密钥缺失、网络失败和取消 |
| 失败 | 网络适配器未返回有效模型回复；后续模块业务校验失败不会再次算作失败调用 |
| 未返回用量 | 响应缺少或包含无效 usage，或超时/网络失败无法取得 usage 的次数 |

未返回用量时不使用预算估算填充真实 token，不能据此断言服务商未计费。历史版本没有持久化实际 usage，无法补算此前消费。自定义 `ModelClient` 适配器需要实现带 `Consumer<Usage>` 的重载并报告 usage；兼容旧重载的适配器仍可调用，但会标记为用量未知。

## TTS 用量与价格

TTS 在客户端请求。阿里云默认 SSE 流式合成，逐个 JSON 事件读取 `TtsProvider.Usage`，回调传的是本次请求的累计快照。`AssistantSpeech` 保留最新有效值，在流完成、失败或取消时只记一次，不把中间事件与最终事件相加；流在结尾前断开时只能保留此前返回的用量，可能不等于最终云端账单。非流式供应商继续在校验音频和下载 WAV 前回调用量，已收到的 usage 不会因为后续下载、解码或播放失败而撤销。`SpeechUsageTracker` 在本地记录开始/完成，不产生 Minecraft 用量报告；统计不依赖朗读队列的 generation，因此绑定撤销或丢弃旧音频不会删除已知消费。音色试听也计入；整句缓存命中、尚未开始合成的排队消息、静音跳过、OpenAL 后备播放不增加请求次数。

| 模型 | 读取的计费单位 |
| --- | --- |
| `qwen3-tts-flash`（含受支持的日期版本） | `usage.characters` |
| `qwen-audio-3.1-tts-flash` | `usage.input_tokens`、`output_tokens`、`total_tokens`；缺少总量时只将实际输入与输出相加 |
| `qwen-audio-3.1-tts-plus` / `qwen-audio-3.0-tts-plus` / `qwen-audio-3.0-tts-flash` | `usage.characters` |

Token 计费模型可能同时返回 `characters`，字符计费模型也可能返回为 0 的 Token 字段；仅将相应计费单位写入 TTS 汇总，避免重复计量。缺少或无效 usage 标为未知，不用本地文本长度或音频时长补算。新供应商应实现带 `Consumer<Usage>` 的重载，流式供应商应按累计快照回调；仅实现旧重载的供应商仍可合成，但 usage 未知。

默认文件沿用此前核对的[阿里云官方模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)，快照日期为 **2026-10-25**，不是在线更新的价格：

| 模型 | 北京 | 新加坡 |
| --- | --- | --- |
| `qwen3-tts-flash` | 0.8 元 / 万字符 | 0.733924 元 / 万字符 |
| `qwen-audio-3.1-tts-flash` | 输入 1.5、输出 12 元 / 百万 Token | 输入 1.677、输出 13.636 元 / 百万 Token |
| `qwen-audio-3.1-tts-plus` | 1.4 元 / 万字符 | 2.19 元 / 万字符 |
| `qwen-audio-3.0-tts-plus` | 1.4 元 / 万字符 | 1.49884 元 / 万字符 |
| `qwen-audio-3.0-tts-flash` | 1 元 / 万字符 | 1.12413 元 / 万字符 |

字符规则：汉字（含繁体、日文/韩文汉字）计 2 个，其余字母、数字、标点、空格、假名及韩文字母计 1 个；SSML 标签不计入。实现直接使用响应值，不自行重算。例如此前复刻测试的 `qwen-audio-3.1-tts-flash` 响应为输入 37、输出 81、总量 118 Token，同时附带 69 characters；北京估价为 `37 × 1.5 / 1000000 + 81 × 12 / 1000000 = 0.0010275` 元，不再按那 69 个字符收费。该次历史测试不会自动回填到存档。

`SpeechPricingConfig` 从客户端配置按供应商、模型、地域和单位精确匹配价格，每次请求算出费用后存入不可变 `SpeechUsage`，精确到十亿分之一元并四舍五入。后续修改单价不会重新计算已累计费用。未配置的供应商/模型/地域、单位不匹配或缺失用量不估价，计入“未估价”，因此页面金额只是已知价格部分的小计。金额不包含 AI 费用、音色创建费用、免费额度、折扣或账单调整；以供应商账单为准。

### 收费标准配置

客户端首次使用费用估算时读取 `config/system/system-speech-pricing.json`。文件不存在时，先从 JAR 内的 `defaults/system-speech-pricing.json` 原样复制，再读取生成的配置；源码默认文件位于 `src/main/resources/defaults/system-speech-pricing.json`。已有配置不会被覆盖，升级 JAR 不会重置本机修改的价格；删除配置后，下次加载会重新复制默认文件。服务端不读取或同步玩家的语音价格表。

修改后通过客户端 F3+T 重载资源或重启客户端生效。价格只在客户端计算，不需要服务端参与；历史费用不重算。下面是一份只给一个模型配置价格的完整示例：

```json
{
  "version": 1,
  "currency": "CNY",
  "asOf": "2026-10-25",
  "rates": [
    {
      "provider": "aliyun",
      "model": "qwen-audio-3.1-tts-flash",
      "region": "beijing",
      "unit": "TOKENS",
      "units": 1000000,
      "input": 1.5,
      "output": 12
    }
  ]
}
```

- `version` 默认为 `1`，`currency` 固定为 `CNY`；`asOf` 是管理员记录的价格日期，建议格式为 `YYYY-MM-DD`，不触发联网更新。
- `provider`、`model`、`region` 与请求元数据精确匹配，同一组合不得重复。
- `unit` 为 `CHARACTERS`、`TOKENS` 或 ASR 的 `CALLS`；`units` 表示价格对应的单位数量，例如字符价格通常为 `10000`，Token 价格通常为 `1000000`，ASR 默认 `1000`。
- `input` / `output` 是每 `units` 的输入 / 输出人民币价格，使用非负金额，内部使用 `BigDecimal`；计算后按每次调用舍入到十亿分之一元。字符或次数计费使用 `input`，`output` 必须为 `0`；Token 计费分别乘实际输入和输出 Token。
- `rates: []` 可以关闭新请求的费用估算，仍记录实际用量。配置大小和价格条目数量不设额外硬上限；配置字段使用 `lenientOptionalFieldOf`，错误头字段回退默认值、未知字段忽略。无效金额、单位或缺少关键价格信息的条目跳过并保留其他有效条目，重复项保留第一条，不将缺失或无效金额当成免费。无效的整个 `rates` 字段回退为空表。

配置缺失但无法复制、文件不可读或 JSON 语法损坏时，保留原文件并记录日志，新请求记为“未估价”，不会静默套用 JAR 单价，也不会因此停用助手、TTS 或用量统计。历史累计金额保持不变。

## 个人语音数据边界

TTS / ASR 的 API Key 由各客户端自行配置，所有调用及费用由 `LocalSpeechUsage` 按当前客户端登录账号 UUID 独立存储，不提供查询其他账号或玩家的界面。数据跨服务器累计，不能用作服务端扣款、奖励或预算依据。用量上报消息、服务端语音账本与统计快照字段已经移除，管理员也无法从全服查询取得其他玩家的 TTS / ASR 记录。

本地开始记录先累计一次尝试并标为用量未知、未估价；完成时补入实际单位、费用并修正未知计数。ASR 在鉴权和 WebSocket 握手完成、首次发送识别命令前开始记账，Token 签发与握手失败不计识别尝试。流式 TTS “请求失败”指流未正常完成、缺少最终标记、取消或 PCM 非法；此时仍保留已回调的有效 usage。非流式阿里云收到可解析的 2xx JSON 后即视为请求完成，本地下载、解码及播放错误不属于其请求失败。旧供应商未实现观察回调时，以其合成方法返回/抛出区分成功和失败。本地参数验证失败、尚未进入工作线程的取消不计请求。

每次请求捕获所属本地账本，取消、断线或换服后的迟到结果仍完成原账号的记录，不依赖 MC 连接，也不会写入其他账号。开始与完成分别持久化，完成回调通过原子标志去重；进程中断留下的未完成请求保持未知。`SpeechUsage` 和 `RecognitionUsage` 为不可变统计对象，同账号的 TTS / ASR 并发更新由账本锁串行合并，文件通过临时文件替换写入。文件加载或保存失败记录日志，不中断语音功能。

```text
<客户端游戏目录>/config/system/speech-usage/<客户端账号UUID>.json
```

记录只包含本人的统计数值和累计费用，不保存密钥、音频或朗读文本，不随 `revoke`、`forget` 或换服清空。API 价格表属于当前客户端安装，不是服务器统一价格。

## 存档数据

`TokenUsageData` 使用 Minecraft 26.1 的 `SavedDataType` 和 Codec，注册到服务器全局 `SavedDataStorage`，与袭击等游戏存档数据使用同一套生命周期。文件为：

```text
<存档>/data/system/token_usage.dat
```

这是独立的 NBT 数据文件，不使用玩家附件，不存入 `<存档>/system-agent/<UUID>.json`，不同维度的消费累计到同一 UUID。名称只用于展示，玩家改名后更新名称并保留同一条 UUID 记录。累计计数使用 64 位非负整数，溢出时保持最大值。

网关工作线程将不可变的消费记录放入队列，服务端主线程在 tick 或读取统计前归并并标脏。正常停服时先关闭 Agent，再归并剩余记录，由 Minecraft 正常保存世界；不会为每次请求阻塞网络线程写磁盘。异常断电可能丢失最近尚未随世界保存的部分，与普通 SavedData 的保存语义一致。

服务端账本保存 AI 和联网调用。内部 `PlayerUsage` 为可变类，名称和调用计数直接更新；四项 AI Token 计数封装在不可变的 `data/usage/TokenUsage` record 中。全服汇总使用独立累加对象，联网请求由不可变 SearchUsage 分开保存 Search/Fetch 的尝试、成功、计费、未知次数和估算费用；存档 Codec 不读取或输出语音统计。

`snapshot()` 返回不可变的 `data/usage/TokenUsageSnapshot` 对象，只包含分页信息、AI/联网汇总和玩家明细，列表复制且不暴露可变账本。`TokenUsageResponseS2C` 通过专用 `StreamCodec` 编码快照对象、错误状态及打开页面的 `open` 标志，客户端直接读取类型字段，再独立展示当前账号的本地语音统计；网络上没有 TTS / ASR 字段。当前网络协议为 **1**，客户端与服务端需一起更新；每包最多 8 位玩家，名字、错误键和请求 ID 都有限长。

`revoke`、`forget`、关闭记忆与重新 `grant` 均不删除累计统计。`revoke` 仍会重置玩家业务文件中的每日模型调用额度；该额度与长期用量账本是独立记录。当前没有清空统计的游戏命令。

本次通过编译、打包和静态资源检查；未启动 Minecraft，界面显示、重启恢复、权限切换及 revoke 前后的实机行为尚未验收。

## 联网次数与价格

Search 和 Fetch 通过同一个 `SearchGateway` 记录，服务端网络调用的 finally 即使遇到取消、revoke 或后续模型失败也记一次。工作线程只排队不可变记录，`AssistantUsage.drain()` 在主线程更新世界账本。两种操作分别计尝试、解码成功、计费成功、结果未知及费用；成功的空结果不计费。HTTP 失败和无法确认结果分开看待，不把本地超时解释为供应商一定免费。

两者默认均为 5 元/千次，即 0.005 元/次，读取服务端 `config/system/system-search-pricing.json`；首次缺失从 JAR `defaults/system-search-pricing.json` 复制。价格字段为十进制字符串 `searchPerThousand`、`fetchPerThousand`，每次请求快照单价，后续改价不重算历史；修改后服务端 `/reload` 生效。Search 与 Fetch 各自显示次数和金额，不与模型或 TTS Token 相加。推理模型仍走 ModelGateway，Token 正常计入同一玩家。

联网费用不包含模型 Token、免费额度与折扣，以实际供应商账单为准；未知调用可能仍有费用。联网授权与可调循环见[网页搜索与抓取](search-api.md)。
