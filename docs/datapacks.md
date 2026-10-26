# 扩展配置

旧的数据包注册表与 `SystemFunction` 已移除。执行预算、模型连接、本地路由和成就规则分别来自服务端 `config/system/` 中的 `system-agent.json`、`system-model.json`、`system-semantic.json` 和 `system-advancements.json`；签到奖励池、商城目录和模型提示词有独立的数据包入口，不使用旧的 `data/.../system/system` 注册表。客户端不能提供价格、商品内容或触发规则。

完整示例见 [执行预算](../examples/agent/system-agent.json)、[模型连接](../examples/agent/system-model.json)、[本地路由](../examples/agent/system-semantic.json) 和 [成就规则](../examples/agent/system-advancements.json)。首次初始化会从 JAR 内 `defaults/` 复制各默认文件，修改后正常重启。密钥只使用 `apiKeyEnvironment` 指定的环境变量。默认 DeepSeek 与其他 OpenAI 兼容服务的配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

`system-semantic.json` 独立控制轻量本地意图路由。新配置默认 ENFORCE，不需要模型文件，说明见 [轻量本地意图路由](semantic-routing.md)。

## 提示词模板

模型提示词使用 UTF-8 `.txt` 文件，支持 UTF-8 BOM，不经过 JSON 注册表。服务端 `PromptReloadListener` 在启动和 `/reload` 时扫描 `data/<namespace>/system/prompts/<path>.txt`，模板 ID 为 `<namespace>:<path>`。JAR 内自带以下默认模板；在数据包中放置同路径文件即可按数据包优先级整份覆盖，不必复制另一份未修改的模板：

| 数据包文件 | 模板 ID | 可用占位符 |
| --- | --- | --- |
| `data/system/system/prompts/routing.txt` | `system:routing` | `{{player_language}}`、`{{capabilities}}` |
| `data/system/system/prompts/sign_in.txt` | `system:sign_in` | `{{player_language}}` |

`{{player_language}}` 由执行上下文自动提供，例如 `zh_cn`、`en_us`，来源为 MC 同步的玩家语言，模块不能通过同名参数覆盖它。`{{capabilities}}` 由路由器传入当前已注册的交互模块 JSON，包含说明和输入 schema；路由模板必须保留这个占位符。签到的玩家快照、候选奖励和日期仍通过用户消息的结构化 JSON 传入，不内联成系统指令。

例如，签到模板中可以写 `Write one short reason in {{player_language}}.`。占位符名使用小写字母、数字或下划线，首位必须为字母；不支持表达式、脚本或递归替换。变量值中的 `{{...}}`、`$`、反斜杠均按原文保留，不执行第二次展开；普通 JSON 的单层或嵌套花括号可直接使用。`{{` 保留给占位符语法。内置模板使用未知占位符、语法错误、空文件、缺失必要模板或移除路由的 `{{capabilities}}` 都会拒绝重载。

模板数量和资源文件大小不设额外硬上限；实际模型请求仍受模块的输入和 Token 预算约束。成功安装服务器资源后，在下一服务端 tick 发布不可变模板快照；失败时继续使用之前的快照，不发布半份模板。已经开始执行的任务保持原版本，新任务读取新版本；精确意图缓存的键也包含版本，旧任务的模型决策不会被新版模板复用。移除覆盖文件后恢复 JAR 内默认模板，无需复制到 `config`。

扩展模块可添加其他命名空间或子目录，例如 `data/example/system/prompts/notice.txt`，然后使用 `context.prompt("example:notice", Map.of("event_name", name))` 渲染，再将结果传给原来的 `context.model(...)`。扩展占位符由模块提供，缺失变量在请求模型前报错；提示词文件本身不会自动注册模块或获得工具权限。预算、奖励候选校验、玩家隔离与支付确认仍由 Java 代码强制执行。

## 奖励目录

奖励和商品只从下述数据包目录读取，不再由 `AgentSettings` 提供列表或兜底。条目 ID 来自文件路径，正文包含简短 `label` 和 `items` 列表；商品另有 `price`。物品使用注册 ID 和正整数数量；每包至少一项，产出每项占一个物品堆，数量不得超过该物品自身的堆叠上限。付款材料可超过一堆，从多个背包格扣除。没有任意 NBT 或组件注入。

AI 只返回候选奖励 ID，不能修改条目数量。入账保存完整奖励快照，修改目录不会改写已经获得的权益。正常签到必须成功调用 AI；成就固定奖励不受此要求影响。

## 数据包目录

完整可复制的示例位于 [examples/commerce-pack](../examples/commerce-pack)。把该目录复制到服务器存档的 `datapacks/` 下，使用正常的数据包加载流程或 `/reload`。示例不会自动安装，不进入发布 JAR。`pack.mcmeta` 格式根据当前依赖的 Minecraft 26.1.2 `version.json` 设置为 101.1。

| 文件路径 | 条目 ID | 用途 |
| --- | --- | --- |
| `data/<namespace>/system/signin_rewards/<path>.json` | `<namespace>:<path>` | AI 签到候选奖励包 |
| `data/<namespace>/system/shop_offers/<path>.json` | `<namespace>:<path>` | 商城商品和价格 |

两个目录的所有有效资源按条目 ID 汇总：不同路径会新增条目，同路径按数据包优先级覆盖。JAR 内置奖励为 `system:food`、`system:mining`、`system:building`，商品为 `system:torch_bundle`，默认内容和自定义内容使用同一加载流程。`{"enabled":false}` 可以覆盖并禁用单个条目；需要空目录时，禁用该类所有有效条目，或通过数据包过滤移除资源。移除覆盖文件会恢复下层数据包的同路径条目；若最终没有有效资源则保持空目录，不从配置补回。奖励包、商品及包内条目数量、资源文件大小均不设额外硬上限。

签到包文件：

```json
{
  "label": "采矿补给",
  "items": [{ "id": "minecraft:torch", "count": 16 }]
}
```

商城兑换文件：

```json
{
  "label": "火把补给",
  "items": [{ "id": "minecraft:torch", "count": 4 }],
  "price": {
    "type": "ITEMS",
    "items": [{ "id": "minecraft:cobblestone", "count": 16 }]
  }
}
```

`price.type` 支持 `ITEMS` 和预留的 `SYSTEM_COIN`。一个条目选择一种支付方式；需要两种购买方式时定义两个商品条目。物品价格的所有材料都必须满足；重复物品 ID 的数量会合并。系统币格式为 `{"type":"SYSTEM_COIN","coins":20}`，金额为正整数（Java `long`），未安装可信支付适配器时只展示、不能购买。价格不能同时使用物品与系统币。

标签为非空纯文本；无效 ID、非正数数量、产出超过实际堆叠上限、缺失价格或无效物品会拒绝本轮重载，不发布半份目录。未知字段遵循原版 Codec 行为忽略。成功重载会更换目录版本，旧报价失效；正在推理的签到决策提交前需要校验版本。已入账的签到/商城奖励、订单和当日唯一标记均保留。

成就 `rewardId` 引用当前奖励池中的完整资源 ID，例如 `system:mining`。

## 成就规则

把 [成就规则示例](../examples/agent/system-advancements.json) 的 `rules` 填入 `config/system/system-advancements.json`。默认文件为 `{"rules":[]}`，示例不会自动启用。每条规则的字段为：

| 字段 | 内容 |
| --- | --- |
| `id` | 稳定规则 ID，普通调整不要换 ID 来重复发奖 |
| `advancementId` | Minecraft 成就 ID；只监听全部条件完成 |
| `moduleId` | 已注册且允许事件入口的模块 ID |
| `input` | 受信管理员配置的模块输入 |

`AdvancementRule.id` 和 `advancementId` 在 Java 中使用 `Identifier`，由原版 `Identifier.CODEC` 解析，JSON 仍写资源 ID 字符串。服务端直接用该类型查询和比较成就；持久化基线、事件任务与模块文本参数在边界转为字符串。

内置 `system:advancement_followup` 接受 `featureId` 与 `rewardId`，执行确保解锁标记存在、将候选目录中的固定奖励入账。适配器另外注入 `ruleId` 与 `advancementId`，不接受玩家伪造事件。

已有成就默认只建立基线，不追溯补发。规则/成就去重记录不随聊天删除，也不随重启清除。更改规则目标、移除规则或删除去重记录会改变业务语义，需要管理员审查；当前无自动历史补发和热重载。

## 策略覆盖

`system-agent.json` 中 `policies` 的键是模块 ID，值必须提供全部 `ExecutionPolicy` 字段。不同模块有自己的模型数、工具数、写步骤、tokens 与期限。`0` 模型次数表示禁止，不能写负数表示无限。未知模块配置会导致助手初始化失败并停止奖励写入。

配置错误不会回退到不校验的发奖方式。诊断在服务端日志中；修改配置文件需要正常重启，修改奖励/商品数据包可正常重载。FTB 自身任务配置仍是独立入口。
