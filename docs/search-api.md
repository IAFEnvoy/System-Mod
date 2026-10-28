# 网页搜索与抓取

已接入服务端功能模块 `system:search`，包含独立工作流 `system:web_search` 和两个只读工具 `web.search`、`web.fetch`。默认系统启用该模块，但启用模块、绑定、登录和打开搜索页均不会请求搜索服务。

## 触发与授权

- 助手输入 `/search <问题>`、`/websearch <问题>`，或游戏聊天 `/system search <问题>`。
- 明确说“帮我联网搜索……”或“请上网查一下……”，英文如 `search the web ...`。
- 完整界面的“模块 → 联网搜索”中输入问题，按 Enter 或点击搜索。

只识别当前消息中的明确联网指令。搜索不进入模型的自动路由目录，没有本地语义样本、推荐快捷操作或自动意图缓存；普通提问、上一轮授权、历史内容和任务事件均不能让模型自行联网。保守解析未识别的表达可改用 `/search`。模块失效、撤销、取消或关闭“外部 AI”后，不再发起后续收费请求；已发送的请求可能仍有费用。

搜索模块可通过系统定义中的 `modules` 字段控制，也可用 `/system module add/remove <玩家> system:search` 管理。它不是必需模块；移除后不影响本地游戏查询。

## 配置与共享密钥

连接配置为 `config/system/system-search.json`：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKeyEnvironment": "SYSTEM_DEEPSEEK_API_KEY",
  "maxResponseBytes": 4194304
}
```

默认与 DeepSeek 文本模型读取同一个服务端环境变量 `SYSTEM_DEEPSEEK_API_KEY`，不需要另一份密钥。搜索连接仍独立配置：换用其他 OpenAI 兼容文本模型后，可以保留 DeepSeek 搜索；若模型密钥环境变量改名，需同时修改搜索的 `apiKeyEnvironment`。

`baseUrl` 是包含 `/v1` 的前缀，分别追加 `/tools/search` 和 `/tools/fetch`，不要填写完整操作地址。远端要求 HTTPS，回环地址允许 HTTP；不允许认证信息、查询参数或 fragment。每次请求在配置锁内快照连接信息，网络等待不持锁。最大响应默认 4 MiB，超出则失败，不当作完整结果使用。

所有配置由 `ConfigInstance` 管理，缺文件时从 JAR 的 `defaults/<文件名>` 复制，已有文件不覆盖；字段使用 `lenientOptionalFieldOf`。初始化后的搜索连接与价格配置可随服务端 `/reload` 重载，模块执行策略需重启后应用。

## 独立 ReAct 预算

流程为模型选一个 Search/Fetch/Answer 步骤 → 执行一个工具 → 将紧凑证据交给下一轮。只输出类型化动作与最终答案，不要求模型输出思维过程。第一步需 Search，最后一轮只允许整理已有结果。默认最多 **6 次模型调用、5 次搜索/抓取工具调用、55 秒总期限**；每次模型最多 10 秒，HTTP 最多 15 秒并受剩余总时间约束。工具总次数包含 Search 和 Fetch，不分别给满 5 次。

在 `config/system/system-agent.json` 的 `policies` 中按工作流 ID 单独调节，不改变签到、任务等模块。例如：

```json
{
  "policies": {
    "system:web_search": {
      "modelCalls": 8,
      "toolCalls": 7,
      "steps": 20,
      "writeSteps": 0,
      "inputBytes": 65536,
      "outputTokens": 1536,
      "totalTokens": 524288,
      "ttlMillis": 90000,
      "modelTimeoutMillis": 12000
    }
  }
}
```

`steps` 要覆盖模型与工具总步数，最后预留一次模型整理；字节和 Token 预算也需要与轮数配套。客户端等待时间从服务端同步，包含最长可用交互工作流期限和传输余量。模型调用同时受全服并发与玩家每日模型额度控制，没有搜索服务自动重试。

每次 Search 取 5 条摘要，不主动附加正文。只在摘要不足时由模型选择 Fetch；仅允许抓取本次检索实际返回的精确 URL，不读取网页里的新链接。重复查询和重复抓取会终止继续检索。来源编号由服务端分配，模型引用未知编号会被拒绝；最终结果卡片显示回答和来源，TTS 只读回答。预算耗尽或整理模型失败时，仍在有效会话中则可显示已经取得的来源，不伪造结论。

## 数据和安全边界

- 搜索服务只收到当前请求导出的检索词、条数和正文开关；Fetch 收到所选页面 URL。工作流不填定位、玩家 UUID、背包或历史。
- 整理模型收到当前问题、玩家语言、此次检索的来源及有限正文。玩家主动输入的信息仍会包含在当前问题内。
- 来源标题、摘要和正文都作为不可信资料处理，不具有工具授权能力。工作流只持有 Search 和 Fetch，不能写游戏状态、领奖或付款。
- 单条标题最多保留 200 个 Unicode 码点，摘要 700，Fetch 正文 5,000；模型明确获知截断，不宣称已读全文。
- 外部 HTTP 由公共 `HttpTransport` 执行，禁止重定向、限制字节和超时，不把 DeepSeek 密钥发给搜索结果域名。

## 计费与用量

Search、Fetch 默认均为 **5 元 / 1,000 次成功且有结果的请求**，即每次 **0.005 元**；搜索结果条数不作为次数，也不混进 Token。一次 Search 加一次 Fetch 是 0.01 元，模型整理 Token 另计。按官方说明，空结果不计费；超时、网络中断或无法解析的结果标为未知，不推断为免费。

服务端价格配置 `config/system/system-search-pricing.json`，默认文件来自 JAR 的 `defaults/system-search-pricing.json`：

```json
{
  "searchPerThousand": "5",
  "fetchPerThousand": "5"
}
```

单价使用十进制字符串，每次请求快照价格并存下费用，精确到十亿分之一元；改价不重算历史。`SearchGateway` 统一记录玩家、Search/Fetch、尝试、成功、计费、未知及费用，最终写入独立世界 SavedData `<存档>/data/system/token_usage.dat`。用量页增加联网面板，管理员可按既有权限查全服；revoke/reset/forget 不清空累计用量。TTS/ASR 保持客户端私有。

该金额是估算值，不包含模型 Token、免费额度或账单调整，以供应商实际账单为准。

## 接口与错误

底层仍提供独立 `SearchProvider` / `DeepSeekSearchClient`，同步调用必须在工作线程。游戏模块应通过 `ExecutionContext.tool` → `SearchGateway` 使用，避免绕过授权、预算和玩家计量。

| 错误码 | 含义 |
| --- | --- |
| `SEARCH_INVALID_REQUEST` | 检索词或 URL 不符合接口 Codec，尚未发送 |
| `SEARCH_NOT_CONFIGURED` | 服务端密钥缺失，尚未发送 |
| `SEARCH_TIMEOUT` | 等待超时，未自动重试 |
| `SEARCH_UNAVAILABLE` | 网络异常、客户端关闭或响应超过上限 |
| `SEARCH_HTTP_ERROR` | HTTP 非 200，原异常保留状态码，不暴露正文或密钥 |
| `SEARCH_INVALID_RESPONSE` | HTTP 200，但不能解码结果 |

## 依据与验证

- [官方使用指南与价格](https://api-docs.deepseek.com/zh-cn/guides/search)
- [Search Schema](https://api-docs.deepseek.com/zh-cn/api/search)
- [Fetch Schema](https://api-docs.deepseek.com/zh-cn/api/fetch)

实现边界依据源码检查；执行 `compileJava testModClasses --console=plain` 编译，不启动 Minecraft、不运行测试，也不调用收费 API。游戏内交互、实际供应商响应和账单仍需运行验收。
