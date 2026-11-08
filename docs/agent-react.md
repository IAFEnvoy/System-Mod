# 通用 ReAct 循环

`agent.react.ReActLoop` 统一执行模型决策 → 一个工具 → 观察结果 → 下一次决策，最终生成答案。它由普通多步分析、联网搜索和自由模式的功能调用共用；精确指令、本地语义直达、任务查看、签到等现有确定性工作流仍直接执行，不额外调用循环。

## 入口和默认预算

核心工作流 `system:reasoning` 处理需要组合多个本地只读能力的问题，例如查找任务、读取要求，再查询物品来源。模型路由只在需要多步组合时选择它，单一操作和普通知识问题继续选择原有能力。也可在助手输入框输入 `/reason <请求>` 或 `分析 <请求>` 显式进入；指令、补全和客户端指令快照随核心工作流自动注册，不新增页面。

| 工作流 | 循环上限 | 模型上限 | 工具上限 | 总步数 | 总期限 | 单次模型超时 |
| --- | --- | --- | --- | --- | --- | --- |
| `system:reasoning` | 3 | 4 | 8 | 14 | 60 秒 | 12 秒 |
| `system:question` 的联网回退 | 6 | 6 | 9 | 20 | 90 秒 | 20 秒 |
| `system:free_chat` 的自由模式 | 6 | 6 | 16 | 32 | 90 秒 | 30 秒 |

一轮是一趟模型决策尝试，最终回答、无效输出和重复动作都算一轮。最后一轮强制 `finalOnly`，不允许继续执行工具。因此 3 轮通常最多执行两个工具，再整理答案。路由使用的模型调用计入工作流总模型预算，但不算循环轮数；自然语言搜索经过一次模型路由后，最多还剩 5 次模型调用，即使循环上限是 6。查库、数据库内部的 FTB 查询、复核和联网步骤共用工具 / 步数预算。

配置为 `config/system/system-agent.json` 中 `policies.<工作流ID>.reactIterations`；0 禁用该工作流的循环调用，缺失或无效时回退 3。没有覆盖策略时，使用模块默认策略，问答默认 6。策略覆盖仍为完整对象，其他字段也要提供；可复制 [完整配置示例](../examples/agent/system-agent.json)。例如：

```json
{
  "policies": {
    "system:reasoning": {
      "modelCalls": 4,
      "toolCalls": 8,
      "steps": 14,
      "writeSteps": 0,
      "inputBytes": 65536,
      "outputTokens": 1536,
      "totalTokens": 262144,
      "ttlMillis": 60000,
      "modelTimeoutMillis": 12000,
      "reactIterations": 3
    }
  }
}
```

`ExecutionPolicy` 仍控制 Token、UTF-8 输入字节、模型 / 工具次数、写步骤和总期限。所有循环调用继续通过同一个 `ExecutionContext.model/tool` 执行内核，保留模型全服并发、玩家每日额度、取消、记忆代次、撤销和计费；创建新循环不补充这些额度。轮数按循环快照读取，修改策略后供新请求使用，不改变正在执行的循环。

## 上下文和工具权限

- `ExecutionContext`：本次玩家请求的生命周期、模块权限和总预算；同一请求内共享。
- `ToolContext`：每次循环独立创建，冻结工具 Type、实际 handler 和配置实例 owner，生成独立 stepId 前缀。使用前、返回后和最终输出前检查玩家状态与已用工具的 owner；模块失效、撤销、忘记、取消和外部偏好关闭不会进入补偿整理。
- `ReActContext<O>`：独立的问题、提示词快照、工具目录、类型化观察、重复动作集合和轮数。它不挂在玩家存档、全局单例或其他玩家上下文中。
- `ReActTool<I,O>`：一次工具动作，提供参数 SchemaMapCodec、说明、去重键和观察投影。返回值仍通过 Codec 编码；普通分析的 `ToolObservation` 按本次工具清单构建 dispatch，保留原始类型化参数和结果，不手动拼接业务 JSON。

普通分析只读取注册表中由 `Tool.forReasoning(description)` 主动声明的只读操作。该声明要求输入具备 SchemaMapCodec，写工具不能声明。当前包括数据库、玩家信息、物品查询 / 来源、FTB 任务查询 / 查找 / 详情；按玩家启用的模块配置选择 handler，未安装 FTB 时不加载 FTB 实现类。未声明的工具不会自动授予。

扩展模组可在自己的只读工具工厂上追加 `.forReasoning("English description")`，由所属模块照常注册。不要给有副作用的 handler 标记 read-only；与现有 AgentModule 一样，Java 扩展本身是可信代码，不是沙箱。

需要自定义循环时，用 `context.tools(TYPE_A, TYPE_B)` 获取该工作流已声明工具的子集，在 `ReActTool` handler 内通过 `tools.call(type, input)` 调用，再构造 `ReActContext` 并运行 `ReActLoop.run`。工作流自行提供最终答案 MapCodec、完成 / 降级函数与业务授权 guard；循环不包含数据库、搜索、商城或 FTB 的具体逻辑。

可恢复原因集合使用 `Set<? extends Failure>`，降级回调接收 Saturn `Failure`；内置原因来自 `AgentFailure`。循环直接使用 `AgentException.failure()`，不再按字符串错误码判断，权限与取消失败仍优先终止。

## 搜索迁移与降级

`WebResearch` 只保留 Search / Fetch 参数、摘要裁剪、已观察 URL 限制、引用复核和卡片构建。旧的 SearchStep 分派和私有 while 循环已删除，SearchObservation 保留为搜索证据。调用格式统一为：

```json
{"type":"web.search","input":{"query":"短检索词"}}
```

抓取使用 `web.fetch`，最终回答使用 `{"type":"answer","text":"回答","sources":[1]}`。最后一轮也接受只含答案字段的对象。搜索仍固定先查库，本地有结果不会转网；仅在本地为空、本次明确允许联网、搜索模块可用且外部服务开启时创建搜索工具上下文。普通分析的工具目录不包含 Search / Fetch，不会因为启用搜索模块而自动联网。

重复动作不重新执行工具，转入最终整理。已有证据时，可恢复模型或搜索错误最多转入一次剩余额度内的整理；授权和取消错误直接终止。轮数、模型、Token、工具或时间预算不会为补偿自动增加，也不自动重试收费请求。搜索保留已获取来源；普通分析整理失败时保留已获得的卡片结果并提示分析未完成，不把工具 JSON 当成玩家回答。

提示词在 `data/system/system/prompts/react.txt` 和 `web_search.txt`，通过既有 txt reload listener 和 `/reload` 重载。每次模型请求仍为 `system` 与 `user` JSON，观察结果显式放入输入；不使用供应商原生 tool_calls，不保存模型的私有推理文本。

本次校验为编译、资源和静态检查；未启动游戏，也未请求真实模型或搜索 API。

自由模式从现有工作流目录绑定工具，同一 ExecutionContext 内嵌套执行，计数共享、子策略收紧、写步骤前缀隔离；保留原确认动作及真实结果卡。扩展模式的聊天兜底只回答一次，不运行工具循环。详见 [四种模式](free-chat.md)。
