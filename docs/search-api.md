# 网页搜索与抓取

服务端功能模块 `system:web_search` 是可选的 ToolModule，导出两个只读工具 `web.search`、`web.fetch`，也作为 DeepSeek 服务端联网的玩家权限入口。核心模块的 `system:question` 统一负责数据库查询、基于资料回答及联网回退。开发数据保留搜索模块定义，但不向默认 tag 追加搜索权限；发布 JAR 不附带该模块定义。启用模块、绑定、登录和打开搜索页均不会直接请求搜索服务。

## 数据库优先

问答顺序由代码固定，不依赖模型自行遵守：

1. 调用 `database.query`，查询当前玩家独立库、共享库的 `system:knowledge` 文档表，以及已启用 FTB 模块导出的授权说明书。
2. 有匹配资料时，最多调用一次模型，仅根据这些片段回答，并以折叠卡片展示本地来源。未配置模型、关闭外部 AI、额度不足或可恢复的整理失败时保留资料；不转入网络搜索。
3. 无匹配资料且未要求只查本地时，**搜索模块已启用、模型使用 DeepSeek 协议、外部 AI 可用**即可自动启用 DeepSeek 服务端搜索。其他模型仍要求本次明确允许联网，才进入独立 Search/Fetch 的网页 ReAct；不满足条件则返回提示。

数据库读取异常不是“无结果”，不能因此联网。命中片段即结束本地查找分支，即使资料不完整也不让模型改为联网；回答发送前再次查询并复核来源，权限或正文变化时取消旧结果。通用查询只读取指定知识表，不扫描业务账本、背景缓存或其他玩家文件。FTB 原始共享语料必须经原生可见性检查，不能直接读取整个 `system:ftb_guides` 表。

## 触发与授权

- 助手输入 `/question <问题>`、`提问 <问题>`：仅查本地资料，不允许联网。
- 助手输入 `/search <问题>`、`/websearch <问题>`：仍先查本地；本地无结果时优先使用 DeepSeek 服务端搜索，其他模型允许独立的付费联网回退。
- 明确说“帮我联网搜索……”或“请上网查一下……”，英文如 `search the web ...`；固定句式可直接进入允许联网回退的问答。
- 其他查资料的自然表达由路由模型判断，例如“帮我查一下，红石比较器的两个模式分别是什么”或 `look up how a comparator works`，无需包含“联网”关键词。
- 完整界面的“模块 → 联网搜索”中输入问题，按 Enter 或点击搜索。

`system:question` 参与普通模型路由，也供自由模式及扩展模式的聊天兜底调用。`QuestionInput.allowWeb` 默认 false，仅控制独立收费搜索的本次授权；DeepSeek 原生搜索由已启用的模块自动允许。新增可选 `localOnly`（默认 false）：明确说“不联网／只查本地”时由路由模型设为 true，`/question` 直接设置 true，它优先于 allowWeb 并禁止两种联网方式。历史、来源文本和任务事件都不能授予权限。本地状态、任务、奖励、商品和物品名称／注册 ID 继续优先交给对应能力。自然语言意图由模型判断，不是确定性关键词校验；问答不缓存联网授权。

执行上下文仅在查库为空且授权条件满足后打开本次联网门禁；原生模型请求在等待并发槽之前、取得槽后及响应返回后复核模块权限，独立 Search/Fetch 也继续检查模块访问和外部服务偏好。模块失效、撤销、取消或切到离线后不再发送后续请求；在途请求可能已产生模型用量。

搜索模块可由管理员在系统定义中明确配置，也可用 `/system module add/remove <玩家> system:web_search` 管理（模块 ID 以数据包实例为准）。它不是必需模块；移除后不影响本地数据库和游戏查询。查询为空不会自动给玩家添加模块。

## DeepSeek 服务端联网

协议按模型配置 `providerType` 判断：`deepseek` 显式启用，`auto` 仅匹配 DeepSeek 官方域名；其他模型不发送扩展字段。官方当前要求使用 `deepseek-flash`，已有 `model` 配置不会被自动改写。模型凭据来自 `bindings.model`，不要求配置 `bindings.search` 或 `system-search.json`。

本地为空后，工作流以一次普通文本模型请求发送：

```json
{
  "tools": [
    { "type": "deepseek_web_search" },
    { "type": "deepseek_read_url" }
  ]
}
```

官方当前接口使用上述工具声明，而非 `web_search_options` 或一个额外的 true 字段。DeepSeek 自行决定检索和阅读链接，应用不接管 Search/Fetch tool call，也不发送这两个独立收费接口的请求。开启思考时沿用 `thinking`／`reasoning_effort`，由服务端执行深度搜索。

联网请求不发送 `response_format: json_object`、自定义函数或 `tool_choice: required`。官方不支持该 JSON 输出模式，并将 required 按 auto 处理；与路由、签到和自由聊天主 ReAct 的结构化函数调用分开，防止联网结果破坏已有工具执行。自由聊天通过 `system:question` 获得资料后继续原有循环，不把全部玩家上下文交给联网请求。

解析 `annotations` 和 `server_tool_calls` 的公开来源信息，按 Unicode 码点位置加入引用编号，最多保留 64 个 HTTP(S) 来源并去重，复用联网卡片与折叠来源区。标题／摘要分别限制 200／700 个码点；不保存供应商思考、加密页面或签名，不跨请求回传网页上下文。没有引用时可显示已完成检索的来源，但不会伪造引用。响应仍须正常 stop、正文非空且受长度限制。

服务端搜索与抓取本身免费，只计入此次模型的 Token、调用次数、每日额度和并发预算；不增加独立 Search/Fetch 的按次费用或次数。最多 4 MiB 的响应上限覆盖服务端工具元数据。HTTP 超时、请求总期限和 Token 总预算继续生效；服务端内部步骤不由本地 ReAct 轮数控制，官方 max_tokens 限制每一步输出，总用量在响应后按实际值校验。失败不会自动降级到收费搜索。

提示词为可覆盖的 `data/system/system/prompt/web_search_native.txt`；独立搜索仍使用 `web_search.txt`。下面的连接、ReAct 和按次计费说明只适用于独立 Search/Fetch 路径。

## 配置与共享密钥

连接配置为 `config/system/system-search.json`：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "maxResponseBytes": 4194304
}
```

独立搜索从统一 `system-api.json` 的 `bindings.search` 读取凭据，默认和文本模型引用同一份 DeepSeek 凭据及其 `SYSTEM_DEEPSEEK_API_KEY` 环境变量，无需重复填写。两种用途可以改选不同凭据；换用其他兼容文本模型后，也可保留 DeepSeek 独立搜索。原生搜索只使用模型凭据。见[统一 API 管理](api-management.md)。

`baseUrl` 是包含 `/v1` 的前缀，分别追加 `/tools/search` 和 `/tools/fetch`，不要填写完整操作地址。远端要求 HTTPS，回环地址允许 HTTP；不允许认证信息、查询参数或 fragment。每次请求在配置锁内快照连接信息，网络等待不持锁。最大响应默认 4 MiB，超出则失败，不当作完整结果使用。

所有配置由 `ConfigInstance` 管理，缺文件时从 JAR 的 `defaults/<文件名>` 复制，已有文件不覆盖；字段使用 `lenientOptionalFieldOf`。初始化后的搜索连接与价格配置可随服务端 `/reload` 重载，模块执行策略需重启后应用。

## 独立 ReAct 预算

本地为空且获准后，`WebResearch` 向通用 `agent.react.ReActLoop` 提供 Search / Fetch 工具、独立 ToolContext 和搜索证据，执行模型选择 → 一个工具 → 下一轮紧凑证据。只输出类型化动作与最终答案，不要求模型输出思维过程。第一步需 Search，最后一轮只解码 Answer，整理已有结果。整个 `system:question` 默认最多 **6 轮循环、6 次模型调用、9 次工具调用、20 步、90 秒总期限**；每次模型最多 20 秒，HTTP 最多 15 秒并受剩余总时间约束。工具预算包含 `database.query`、其内部 FTB 查询和本地结果复核；联网循环使用剩余预算，Search 和 Fetch 共用额度。轮数含最终整理，路由只扣模型预算、不扣循环轮数。通用机制与上下文边界见 [ReAct 循环](agent-react.md)。

在 `config/system/system-agent.json` 的 `policies` 中按工作流 ID 单独调节，不改变签到、任务等模块。例如：

```json
{
  "policies": {
    "system:question": {
      "modelCalls": 8,
      "toolCalls": 11,
      "steps": 24,
      "writeSteps": 0,
      "inputBytes": 65536,
      "outputTokens": 1536,
      "totalTokens": 524288,
      "ttlMillis": 90000,
      "modelTimeoutMillis": 12000,
      "reactIterations": 6
    }
  }
}
```

`steps` 要覆盖模型与工具总步数，最后预留一次模型整理；字节和 Token 预算也需要与轮数配套。自然语言若经过模型路由，该次模型调用也计入本次问答总预算；明确指令可省去这次路由。原 `policies["system:web_search"]` 不再控制问答，需改用 `system:question`。客户端等待时间从服务端同步，包含最长可用交互工作流期限和传输余量。模型调用同时受全服并发与玩家每日模型额度控制，没有搜索服务自动重试。

每次 Search 取 5 条摘要，不主动附加正文。只在摘要不足时由模型选择 Fetch；仅允许抓取本次检索实际返回的精确 URL，不读取网页里的新链接。来源编号由服务端分配，模型引用未知编号会被拒绝；最终结果卡片显示回答和来源，TTS 只读回答。

已有检索资料时，工作流为最终整理保留一次模型调用和时间窗口；剩余时间不足以同时容纳一次工具请求与一次模型整理时，直接整理。重复查询、重复抓取，或探索阶段的可恢复模型 / 工具错误都会切换到 finalOnly，使用已有资料回答，不直接退回来源列表；这次整理仍计入原有模型次数、步数、Token、并发和每日额度，不重试已收费的工具。最终整理失败或硬预算已耗尽时才保留来源，服务端日志记录稳定错误码、模型调用数、来源数和剩余时间，不记录密钥、玩家问题或网页正文。撤销、取消、模块失效和关闭外部服务不进入补偿整理。

`web_search.txt` 要求模型提炼网页中与问题相关的事实，合并重复信息，省略导航、广告、套话与无关背景。最终回答先给结论，通常一小段或 2–4 个简短要点；用法类问题提供操作步骤、控制方式和简短例子，对比问题只补关键差异和适用场景，保留必要数字、条件及不确定性，用户要求详细时再展开。回答不重复问题、检索过程、来源标题、URL 或长摘要；引用编号留在正文，来源由卡片单独显示，不增加独立的压缩模型调用。

搜索卡片的“来源与摘要”通过通用 `system:details` 节点默认收起，展开后各来源独占一块，标题下显示最多三行摘要预览，长内容以省略号收尾；标题和摘要的连续空白由服务端压成空格。来源标题使用 `system:link`，遵守 Minecraft 链接开关与原版确认设置，可打开网页查看全文。折叠标题和链接使用单独的文字子元素，展开时立即失效子树布局并提交重排。折叠和展开都是本地显示操作，不重复搜索或调用模型。两种助手界面均可交互，HUD 需先进入聊天栏。提示词可由数据包覆盖并用服务端 `/reload` 重载，卡片 JSON 和样式可由客户端资源包覆盖。

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

- [Chat Completions 联网工具及限制](https://api-docs.deepseek.com/zh-cn/guides/web_tools#chat-completions-api)
- [Chat Completions 请求 Schema](https://api-docs.deepseek.com/zh-cn/api/create-chat-completion)
- [官方使用指南与价格](https://api-docs.deepseek.com/zh-cn/guides/search)
- [Search Schema](https://api-docs.deepseek.com/zh-cn/api/search)
- [Fetch Schema](https://api-docs.deepseek.com/zh-cn/api/fetch)

实现边界依据源码检查；执行 `compileJava testModClasses --console=plain` 编译，不启动 Minecraft、不运行测试，也不调用收费 API。游戏内交互、实际供应商响应和账单仍需运行验收。
