# 模型接口与 DeepSeek 兼容

默认仍使用 `https://api.deepseek.com`、`deepseek-chat` 与环境变量 `SYSTEM_DEEPSEEK_API_KEY`。模型配置独立于执行预算，不提供旧聚合配置迁移。模型协议适配器为 `OpenAiCompatibleClient`，按 OpenAI Chat Completions 格式发送非流式请求，底层 HTTP 由独立的 `com.iafenvoy.system.util.HttpTransport` 处理。所有路由、签到与扩展模块仍统一经过 `ModelGateway`，实际 token 用量继续按玩家累计。

## 配置

服务端模型配置在 `config/system/system-model.json`，修改后通过资源重载重新读取，后续请求直接使用当前配置；在途请求保持原报文与连接参数。下面是默认配置中的模型字段：

```json
{
  "providerType": "auto",
  "baseUrl": "https://api.deepseek.com",
  "model": "deepseek-chat",
  "apiKeyEnvironment": "SYSTEM_DEEPSEEK_API_KEY",
  "apiKeyRequired": true,
  "jsonMode": true,
  "outputTokenParameter": "max_tokens",
  "thinkingEnabled": false,
  "reasoningEffort": "high"
}
```

| 字段 | 含义 |
| --- | --- |
| `providerType` | `auto`、`openai` 或 `deepseek`。`auto` 只对 `deepseek.com` 及其子域启用 DeepSeek 分支，其他地址使用通用 OpenAI 格式 |
| `baseUrl` | API 前缀，不包含 `/chat/completions`。保留自定义前缀并追加该路径，兼容 `/v1`、`/compatible-mode/v1` 等；末尾 `/` 会去掉 |
| `model` | 原样发送的模型 ID，由目标服务决定可用名称 |
| `apiKeyEnvironment` | 服务端读取密钥的环境变量名，不是密钥本身 |
| `apiKeyRequired` | 默认 `true`。无认证的本地服务可设 `false`；环境变量仍有非空值时照常发送 Bearer 头 |
| `jsonMode` | 默认发送 `response_format: {"type":"json_object"}`。服务不支持此参数时设 `false`；本地仍严格要求返回 JSON 对象 |
| `outputTokenParameter` | `max_tokens`（默认）或 `max_completion_tokens`，每次只发送其中一个；数值仍来自模块执行预算 |
| `thinkingEnabled` | 仅 DeepSeek 分支使用，默认 `false`，显式发送 `thinking.type=disabled`；开启时发送 `enabled` |
| `reasoningEffort` | 仅 DeepSeek 且开启思考时发送 `reasoning_effort`，默认 `high`，空字符串表示省略。具体可用值由目标模型支持情况决定 |

远程地址使用 HTTPS；`localhost`、`127.0.0.1` 和 `[::1]` 允许 HTTP，便于本地模型服务。地址不允许内嵌用户名/密码、查询参数或 fragment，也不跟随重定向。出站目标始终来自服务端配置。

切换普通兼容服务时修改 `baseUrl`、`model` 和 `apiKeyEnvironment`，可显式设 `providerType=openai`。若服务使用新的输出限制字段，再设 `outputTokenParameter=max_completion_tokens`。某些模型不支持 JSON 模式，可关闭 `jsonMode`，但仍需遵守现有提示词并直接返回 JSON。

以下片段用于合并到 `system-model.json`；完整默认示例见 [system-model.json](../examples/agent/system-model.json)：

- [普通兼容服务](../examples/agent/model-openai-compatible.json)：将示例域名与模型 ID 替换为实际值。
- [本地服务](../examples/agent/model-local.json)：示例端口为 11434，需填写已部署的实际模型 ID。
- [DeepSeek 思考开关](../examples/agent/model-deepseek-thinking.json)：开启后，所用模型须支持相应参数；现有输出 token 与超时预算同时包含推理开销，可在模块策略中调整。

## 提供商差异

实现参考 `E:\Go\ModList-Dashboard\agent\llm.go`：以通用 OpenAI 请求为基础，只为 DeepSeek 注入思考相关字段。通过第三方代理调用 DeepSeek 时，显式配置 `providerType=deepseek`；通过官方域名调用但代理需要完全通用字段时，可强制设为 `openai`。模型名不会用于猜测提供商。

本模组目前每次调用都是独立的 `system` 与 `user` JSON 决策，没有原生 `tool_calls` 对话循环。因此只使用 `message.content` 作为受限决策，不保存或跨玩家缓存 `reasoning_content`；Dashboard 工具循环中的推理内容回传机制在这里没有对应的延续轮次。将来若加入原生工具循环，推理状态应随该次玩家调用的消息链传递。

基础请求包含 `model`、`messages`、`stream=false` 和选定的输出 token 限制字段；`response_format` 按配置发送。普通分支不会附加 DeepSeek 的 `thinking` 或 `reasoning_effort`。返回内容仍要求 `finish_reason=stop` 和合法 JSON，不自动重试或修复响应。这里实现的是兼容 Chat Completions 的文本接口；不包含 Responses、原生工具循环、图像或流式输出。

## 用量

通用用量读取 `prompt_tokens`、`completion_tokens` 与 `total_tokens`。缓存命中优先使用 OpenAI 的 `prompt_tokens_details.cached_tokens`，缺省时兼容 DeepSeek 的 `prompt_cache_hit_tokens`，只计一次。缺失或无效 usage 仍标记为用量未知；在校验模型正文前读取 usage，正文错误不会删除已知消费。

## HTTP 工具复用

`HttpTransport` 仅依赖 JDK，不依赖 Minecraft、Agent 配置、Gson 或模型错误码。每个实例复用一个 `HttpClient`，默认连接超时 5 秒，也可在构造时指定；所有请求禁止重定向，不自动重试，由拥有者关闭实例。`postJson(endpoint, json, headers, timeout, maxResponseBytes)` 发送 UTF-8 JSON；`send(HttpRequest, maxResponseBytes)` 接受任意方法、请求头和 JDK `BodyPublisher`，可用于后续音频字节、文件或 multipart 请求。返回标准 `HttpResponse<byte[]>`，保留状态码、响应头与原始字节，文本由协议调用方解码。

每次必须指定正数响应字节上限和请求超时；响应在接收 chunk 时限长，等待期限覆盖完整响应体，超时或线程中断取消在途请求，中断保留线程标记。工具同步等待结果，必须在工作线程调用。状态码判断、JSON 解码、认证密钥来源和出站目标校验由具体 API 接入负责；模型接入仍使用`ModelSettings` 固定端点校验、64KiB 响应限制与 Token 统计。JDK 请求超时和完整响应等待超时均映射为 `MODEL_TIMEOUT`，其他 I/O 失败映射为 `MODEL_UNAVAILABLE`。

`stream(HttpRequest, maxResponseBytes, StreamConsumer)` 以串行回调交付响应信息和 byte[] 数据块，返回 `HttpResponse<Void>`；总字节上限和等待期限仍覆盖整个流。回调在 HTTP 工作线程执行，不能访问 MC 主线程状态或阻塞等待播放。调用方可用独立的 `ServerSentEvents` 解析 SSE，限长缓冲 UTF-8 行和事件，支持跨 chunk、多行 data 及 CR/LF。阿里云 TTS 已使用该链路边接收 PCM 边播放，原有 JSON/WAV 完整响应接口继续保留。详见 [语音合成与音色设计](tts.md)。

接口字段参考 [OpenAI Chat Completions 官方文档](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)。各兼容服务的参数支持范围可能不同，以服务商文档为准。本轮仅编译、打包与静态检查，未发起真实模型请求或启动 Minecraft。


WebSocket 语音识别复用 HttpTransport.webSocket(endpoint, headers, timeout, listener)，连接超时与中断会取消握手；建立后的连接由供应商关闭，协议层负责消息大小、音频时长与任务截止时间。详见[语音识别](asr.md)。
