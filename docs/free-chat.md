# 自由聊天与管理员 AI 权限

玩家在统一设置页面的常规页开启“自由聊天兜底”，修改立即保存到自己的 PlayerData；默认关闭。外部 AI 开关、历史保存开关与管理员权限分别保存。管理员禁用后，玩家的原有偏好仍保留，不能通过客户端请求解除管理员限制。

## 消息如何处理

| 状态 | 行为 |
| --- | --- |
| 自由聊天关闭或未获许可 | 优先精确指令、明确入口和较低阈值的本地只读匹配；未命中再进行原有单次 AI 意图判断，无法确定则提供可点击选项 |
| 自由聊天开启且获许可 | 精确指令、明确入口与较高阈值的本地只读匹配优先；未命中直接进入自由聊天，省去一次模型意图判断 |
| 顶栏“强制自由：开” | 本次界面的文字输入直接进入自由聊天，跳过本地指令与意图匹配；卡片按钮仍执行其原有动作 |
| AI 被管理员禁止或玩家关闭外部 AI | 精确指令和本地匹配继续可用，模糊输入提供本地选项，不发送模型请求 |

完整和简约界面的“待命”右侧均有强制自由开关；需要管理员许可及玩家开启外部 AI。它可独立覆盖自动兜底偏好：关闭自动兜底时仍能手动强制聊天。该临时状态跨这两个界面保留，断开连接或权限失效时关闭。强制模式关闭指令补全，以免聊天内容被解释为操作。

`config/system/system-semantic.json` 的 `modeScores` 调整现有每项 / 每语言 score 阈值：

```json
"modeScores": {
  "functions": -0.1,
  "freeChat": 0.03
}
```

默认基础 score 为 0.9，因此功能模式是 0.8，自由聊天模式是 0.93。覆盖率、候选分差、只读限制、复杂句和参数解析门槛继续生效，降低相似度阈值不会授权写操作。模式与配置参与缓存键，切换或 `/reload` 后不会复用另一种阈值下的缓存。

## 管理员命令与玩家提示

在原版游戏聊天栏或服务端控制台执行以下命令，需要管理员权限等级 2。玩家参数只接受一个在线玩家，支持玩家名和原版选择器补全；权限按 UUID 持久保存，重新登录仍有效，不要求玩家已绑定系统。

| 命令 | 作用 |
| --- | --- |
| `/system ai <玩家> enable` | 允许玩家使用 AI；仍需玩家开启自己的外部 AI 开关 |
| `/system ai <玩家> disable` | 禁用玩家的 AI，包括自由聊天和统一外部服务门禁下的收费搜索 |
| `/system ai <玩家> freechat enable` | 允许自由聊天；仍受 AI 总开关和玩家个人设置约束 |
| `/system ai <玩家> freechat disable` | 仅禁用自由聊天，其他已获准的 AI 功能仍可使用 |
| `/system ai <玩家> limit <次数>` | 设置每日模型调用上限；0 表示不再接纳模型调用，不清空已用次数 |
| `/system ai <玩家> status` | 查看生效的 AI / 自由聊天权限与每日调用上限；可省略 status |
| `/system ai <玩家> reset` | 移除该玩家的管理员覆盖，恢复继承默认规则；不清空个人设置、用量或每日已用次数 |

修改立即生效并自动保存，无需 `/reload`。生效权限变化时，服务端取消该玩家的在途交互，同步其界面状态，并通过原版聊天栏告知新的许可和额度。完整 / 简约输入框旁和设置页显示被管理员禁用或玩家自行关闭外部 AI 的原因；强制自由开关也会显示不可用原因。管理员禁用 AI 后，模糊本地匹配仍提供可点击选项，并说明限制来源。

命令写入服务端 `config/system/system-ai-access.json`，首次从 JAR 的 `defaults/` 复制。该文件独立于玩家偏好，不受 `/system revoke`、`/system reset` 或 `/forget` 影响；`/system ai <玩家> reset` 只移除对应的管理员覆盖。高级配置可手动修改全服 defaults 或离线玩家 UUID，手动改文件后执行 `/reload`，同样会同步生效权限并提示在线玩家。

```json
{
  "defaults": {
    "allowAi": true,
    "allowFreeChat": true
  },
  "players": {
    "00000000-0000-0000-0000-000000000001": {
      "allowAi": false
    },
    "00000000-0000-0000-0000-000000000002": {
      "allowFreeChat": false,
      "modelCallsPerDay": 20
    }
  }
}
```

示例 UUID 需替换为实际玩家 UUID。各项按“玩家覆盖 → defaults → 内置默认”解析。未配置的 allowAi / allowFreeChat 为 true；modelCallsPerDay 继续继承 `system-agent.json` 的 modelCallsPerPlayerDay。0 表示当天不再接纳模型调用。allowAi=false 同时关闭自由聊天与经统一外部服务门禁的收费搜索；本地工具、TTS 和 ASR 保持各自原有权限与账号配置。

设置页只读展示当前玩家的管理员许可及每日模型上限。客户端只能修改 history、external、freeChat 三项个人偏好，无法写入管理员配置或查看其他玩家的权限。

## 执行、上下文和扩展接口

`system:free_chat` 是核心模块安装的独立工作流，由 `FreeChatInput` 分派；它使用 structuredOnly，模型意图路由不会在玩家关闭自由聊天时偷偷选择它。`FreeChatModule` 每次只生成一条回复，沿用 ExecutionContext 的每日额度、并发、总预算、取消、运行代次和统一 ModelGateway 计量。默认输出上限 2048 token、总期限 60 秒，可用 `system-agent.json` 的 `policies.system:free_chat` 覆盖。

历史开启时，当前玩家最近最多 20 条适用聊天记录、合计最多 16 KiB 正文进入请求；助手内容只取 ChatOutput 的 Markdown 正文，当前请求不重复加入历史。历史关闭后仅发送当前消息。自由回复使用 `system:chat` 卡片和 `chat.json` 的 Markdown 节点，继续使用既有播报与消息历史管线。

`engine.chat()` 提供每个引擎独立的 `ChatSupport`：

- `prompts(PromptProvider)`：替换提示词提供者。当前默认只提供最小聊天 / JSON 协议约定，资源为 `data/system/system/prompts/free_chat.txt`，遵循现有 txt reload listener。
- `tools(ToolProvider)`：预留当前上下文可用工具类型的提供接口。本轮不读取该目录，也不执行工具调用；正式 tool call 和完整提示词策略后续接入。

扩展可在现有 AssistantRegistrationEvent 取得 engine 设置上述接口。免费 / 收费请求仍必须经过执行上下文，提示词接口不能改变玩家或管理员的权限。

本轮只执行编译、资源处理和静态核对；未启动 Minecraft，也未调用真实模型 API。
