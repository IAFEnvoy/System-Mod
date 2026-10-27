# DeepSeek 网页搜索与抓取

已实现独立的 `SearchProvider` 接口与 `DeepSeekSearchClient`，同时支持 Search 和 Fetch。当前只提供 Java 调用入口，没有注册 Agent Module、Tool、意图样本、命令、网络载荷或 GUI，也没有启动或聊天时的自动搜索。文本模型切换到其他提供商不会改变搜索配置。

## 配置

配置路径为 `config/system/system-search.json`，类为 `config.file.SearchConfig`。首次访问 `SearchConfig.INSTANCE` 时，`ConfigInstance` 从 JAR 内的 `/defaults/system-search.json` 复制缺失文件再加载；已有文件保留。当前没有游戏侧调用，因此启动游戏不保证初始化此配置，首次直接调用客户端时会初始化。

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKeyEnvironment": "SYSTEM_DEEPSEEK_API_KEY",
  "maxResponseBytes": 4194304
}
```

- `baseUrl` 是包含 `/v1` 的 API 前缀，客户端分别追加 `/tools/search` 或 `/tools/fetch`；不要填完整操作地址。远程要求 HTTPS，回环地址允许 HTTP，不允许用户名、密码、查询参数和 fragment。
- `apiKeyEnvironment` 是服务端读取密钥的环境变量名，默认复用文本模型通常使用的 `SYSTEM_DEEPSEEK_API_KEY`；不会从 `ModelConfig` 读取地址或配置，改用其他文本模型后仍可保留独立的 DeepSeek 搜索密钥。
- `maxResponseBytes` 是包含正文的完整响应上限，默认 4 MiB；超过上限取消接收并报错，不截断结果冒充完整响应。

所有配置字段使用 `RecordCodecBuilder` 和 `lenientOptionalFieldOf`，单项无效回退该项默认值。配置初始化后参与既有资源重载。修改时使用 `SearchConfig.INSTANCE.set(next)`，由配置容器自动标记待保存，不缓存配置副本。每个请求在配置锁内确定地址、密钥及字节上限，网络等待不持有配置锁。

## Java 调用

同步调用必须放在工作线程。每次调用显式传入超时，调用者管理客户端生命周期，可以复用同一客户端处理多个请求，结束时调用 `close()`。

```java
import com.iafenvoy.system.search.DeepSeekSearchClient;
import com.iafenvoy.system.search.SearchProvider;

import java.time.Duration;

try (SearchProvider search = new DeepSeekSearchClient()) {
    SearchProvider.Response response = search.search(
            new SearchProvider.Request("Minecraft NeoForge site:neoforged.net lang:en", 5, false),
            Duration.ofSeconds(15));
    // Read response.results(); each result exposes title, url, snippet, summary and metadata.
}
```

需要单页正文时单独调用，不会自动遍历搜索结果再抓取：

```java
SearchProvider.Page page = search.fetch(
        new SearchProvider.FetchRequest("https://docs.neoforged.net/"),
        Duration.ofSeconds(15));
// page.title() and page.content(); content is Markdown returned by DeepSeek.
```

`Request(query)` 使用接口默认值：10 条、不返回正文、不发送原始问题或定位信息。`Request(query, count, needContent)` 适合常规搜索；完整构造函数还接受 `Optional<String> originalQuestion` 和 `String localization`，分别编码为 `ori_question` 和 `localization`，只发送调用者显式提供的值，不自动采集玩家输入或 IP。

搜索词原样保留官方支持的语法，例如 `site:`、`-site:`、引号、`lang:`、`after:` 和 `before:`。请求 Codec 只落实官方的搜索词长度、条数和 URL 格式约束；字符串长度按 Unicode 码点计算。结果包含排序分数、标题、链接、短摘要、长摘要、正文、可选站点名称/图标/日期和权威性；允许空结果。官方可空的站点元数据以 `Optional` 表示，并通过宽松可选 Codec 接受 `null`。请求与响应全程使用类型对象和 Codec，没有裸 `JsonObject` 传递。

## 网络与错误

复用 `util.HttpTransport`：完整响应超时、接收时限长、禁止重定向、不自动重试、线程中断取消请求。Search 和 Fetch 都只向配置的 DeepSeek API 地址发送 Bearer 密钥；Fetch 将目标 URL 作为 JSON 字段交给 DeepSeek，不从本机直接下载目标页面或图标。

`SearchException` 提供 `code()` 和 `statusCode()`：

| 错误码 | 含义 |
| --- | --- |
| `SEARCH_INVALID_REQUEST` | 请求对象不符合接口 Codec，尚未发送 |
| `SEARCH_NOT_CONFIGURED` | 环境变量中没有搜索 API 密钥，尚未发送 |
| `SEARCH_TIMEOUT` | 请求或完整响应等待超时 |
| `SEARCH_UNAVAILABLE` | 网络失败、客户端关闭或响应超出字节上限 |
| `SEARCH_HTTP_ERROR` | HTTP 状态不是 200，`statusCode()` 保留原状态，例如 401、402、429 |
| `SEARCH_INVALID_RESPONSE` | HTTP 200，但响应无法按预期 Codec 解码 |

本地错误的 `statusCode()` 为 0；线程中断单独保留为 `InterruptedException`。异常不复制供应商错误正文或认证头。调用方自行决定是否重试，取消或客户端解析失败不代表供应商未产生费用。

## 计费与接入边界

按 2026-10-27 核对的中文官方文档，Search 和 Fetch 各为每 1,000 次成功且产生结果的请求 5 元，不按 Token 或搜索结果条数计费。空搜索结果、失败和超时按官方说明不计费。两次独立的 Search / Fetch 是两次请求；`needContent=true` 不会在本地自动再发 Fetch。供应商计费以账号实际规则为准。

当前没有接入玩家用量账本、Agent 权限与预算、会话历史或结果缓存，也不把调用次数伪装成 Token。现有 AI / TTS / ASR 统计不变。将来由游戏功能开放搜索时，应在调用层接入相应玩家权限、请求额度与按次统计；目前普通游戏操作不会触发该客户端。

## 依据与验证

- [官方使用指南](https://api-docs.deepseek.com/zh-cn/guides/search)
- [Search 请求与响应 Schema](https://api-docs.deepseek.com/zh-cn/api/search)
- [Fetch 请求与响应 Schema](https://api-docs.deepseek.com/zh-cn/api/fetch)

本次执行 `compileJava` 编译和静态检查；没有请求收费 Search / Fetch API，没有使用密钥进行连通性验证，没有启动游戏或执行测试。
