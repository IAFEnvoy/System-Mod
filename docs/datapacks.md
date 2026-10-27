# 扩展配置

旧的数据包注册表与 `SystemFunction` 已移除。执行预算、模型连接、本地路由和成就规则分别来自服务端 `config/system/` 中的 `system-agent.json`、`system-model.json`、`system-semantic.json` 和 `system-advancements.json`；签到奖励池、商城目录、抽奖奖池和模型提示词有独立的数据包入口，不使用旧的 `data/.../system/system` 注册表。客户端不能提供价格、商品内容、抽奖权重或触发规则。

完整示例见 [执行预算](../examples/agent/system-agent.json)、[模型连接](../examples/agent/system-model.json)、[本地路由](../examples/agent/system-semantic.json) 和 [成就规则](../examples/agent/system-advancements.json)。首次初始化会从 JAR 内 `defaults/` 复制各默认文件，修改后正常重启。密钥只使用 `apiKeyEnvironment` 指定的环境变量。默认 DeepSeek 与其他 OpenAI 兼容服务的配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

`system-semantic.json` 独立控制轻量本地意图路由。新配置默认 ENFORCE，不需要模型文件，说明见 [轻量本地意图路由](semantic-routing.md)。

## 提示词模板

模型提示词使用 UTF-8 `.txt` 文件，支持 UTF-8 BOM，不经过 JSON 注册表。服务端 `PromptReloadListener` 在启动和 `/reload` 时扫描 `data/<namespace>/system/prompts/<path>.txt`，模板 ID 为 `<namespace>:<path>`。JAR 内自带以下默认模板；在数据包中放置同路径文件即可按数据包优先级整份覆盖，不必复制另一份未修改的模板：

| 数据包文件 | 模板 ID | 可用占位符 |
| --- | --- | --- |
| `data/system/system/prompts/routing.txt` | `system:routing` | `{{player_language}}`、`{{capabilities}}` |
| `data/system/system/prompts/sign_in.txt` | `system:sign_in` | `{{player_language}}` |
| `data/system/system/prompts/quest_narration.txt` | `system:quest_narration` | `{{player_language}}` |

`{{player_language}}` 由执行上下文自动提供，例如 `zh_cn`、`en_us`，来源为 MC 同步的玩家语言，模块不能通过同名参数覆盖它。`{{capabilities}}` 由路由器传入当前已注册的交互模块 JSON，包含说明和输入 schema；路由模板必须保留这个占位符。签到的玩家快照、候选奖励和日期仍通过用户消息的结构化 JSON 传入，不内联成系统指令。

例如，签到模板中可以写 `Write one short reason in {{player_language}}.`。占位符名使用小写字母、数字或下划线，首位必须为字母；不支持表达式、脚本或递归替换。变量值中的 `{{...}}`、`$`、反斜杠均按原文保留，不执行第二次展开；普通 JSON 的单层或嵌套花括号可直接使用。`{{` 保留给占位符语法。内置模板使用未知占位符、语法错误、空文件、缺失必要模板或移除路由的 `{{capabilities}}` 都会拒绝重载。

模板数量和资源文件大小不设额外硬上限；实际模型请求仍受模块的输入和 Token 预算约束。成功安装服务器资源后，在下一服务端 tick 发布不可变模板快照；失败时继续使用之前的快照，不发布半份模板。已经开始执行的任务保持原版本，新任务读取新版本；精确意图缓存的键也包含版本，旧任务的模型决策不会被新版模板复用。移除覆盖文件后恢复 JAR 内默认模板，无需复制到 `config`。

扩展模块可添加其他命名空间或子目录，例如 `data/example/system/prompts/notice.txt`，然后使用 `context.prompt("example:notice", Map.of("event_name", name))` 渲染，再将结果传给原来的 `context.model(...)`。扩展占位符由模块提供，缺失变量在请求模型前报错；提示词文件本身不会自动注册模块或获得工具权限。预算、奖励候选校验、玩家隔离与支付确认仍由 Java 代码强制执行。

## 奖励目录

奖励和商品只从下述数据包目录读取，不再由 `AgentSettings` 提供列表或兜底。条目 ID 来自文件路径，正文包含简短 `label` 和 `items` 列表；商品另有 `price`。物品直接使用原版 `ItemStackTemplate.CODEC`，包含注册 ID、可选的 `count`（默认 1，原版范围 1～99）及 `components`。签到奖励和商品产出每包为 1～36 项，与交付时主背包预留空位的规则一致。重载阶段只读取模板，不构建 `ItemStack`；实际扣款前或交付时才调用 `create()`，由原版校验最终组件及产出的实际堆叠上限。付款材料可以跨背包格扣除，超过单个模板数量范围时拆成多项。

AI 只返回候选奖励 ID，不能修改条目数量。入账保存完整奖励快照，修改目录不会改写已经获得的权益。正常签到必须成功调用 AI；成就固定奖励不受此要求影响。

## 数据包目录

完整可复制的示例位于 [examples/commerce-pack](../examples/commerce-pack)。把该目录复制到服务器存档的 `datapacks/` 下，使用正常的数据包加载流程或 `/reload`。示例不会自动安装，不进入发布 JAR。`pack.mcmeta` 格式根据当前依赖的 Minecraft 26.1.2 `version.json` 设置为 101.1。

| 文件路径 | 条目 ID | 用途 |
| --- | --- | --- |
| `data/<namespace>/system/signin_rewards/<path>.json` | `<namespace>:<path>` | AI 签到候选奖励包 |
| `data/<namespace>/system/shop_offers/<path>.json` | `<namespace>:<path>` | 商城商品和价格 |
| `data/<namespace>/system/lottery_pools/<path>.json` | `<namespace>:<path>` | 抽奖奖池、奖品权重和单次消耗 |

三个目录的所有有效资源按条目 ID 汇总：不同路径会新增条目，同路径按数据包优先级覆盖。JAR 内置奖励为 `system:food`、`system:mining`、`system:building`，商品为 `system:torch_bundle`，奖池为 `system:supplies`，默认内容和自定义内容使用同一加载流程。`{"enabled":false}` 可以覆盖并禁用单个条目；需要空目录时，禁用该类所有有效条目，或通过数据包过滤移除资源。移除覆盖文件会恢复下层数据包的同路径条目；若最终没有有效资源则保持空目录，不从配置补回。奖励包、商品、奖池及奖品总数和资源文件大小不设额外硬上限；每个奖励包内产出使用上述 1～36 项限制，付款材料列表不设额外项数上限。

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

`price.type` 支持 `ITEMS` 和预留的 `SYSTEM_COIN`。一个条目选择一种支付方式；需要两种购买方式时定义两个商品条目。物品价格至少一项且所有材料都必须满足；物品类型和最终组件必须与模板完全一致。扣款先按背包槽位预留数量，重复模板不会重复使用同一份材料。系统币格式为 `{"type":"SYSTEM_COIN","coins":20}`，金额为正整数（Java `long`），未安装可信支付适配器时只展示、不能购买。价格不能同时使用物品与系统币。

标签为非空纯文本；无效物品 ID、不在原版范围内的数量、无法解析的组件、产出列表不在 1～36 项范围内或缺失价格都会拒绝本轮重载，不发布半份目录。依赖最终物品组件的校验留到运行时；商品产出无法创建时会在扣款前失败，奖励无法创建时保留为待领取。未知字段遵循原版 Codec 行为忽略。成功重载会更换目录版本，旧报价失效；正在推理的签到决策提交前需要校验版本。已入账的签到/商城奖励、订单和当日唯一标记均保留。

成就 `rewardId` 引用当前奖励池中的完整资源 ID，例如 `system:mining`。

## 抽奖奖池

奖池通过同一个目录重载监听器加载，奖池 ID 来自 `data/<namespace>/system/lottery_pools/<path>.json` 的路径。默认 `system:supplies` 消耗 1 个绿宝石，奖品分别为 16 个火把（60%）、8 个面包（30%）、4 个铁锭（10%）；可覆盖同路径文件或用 `{"enabled":false}` 禁用。可复制的示例在 [supplies.json](../examples/commerce-pack/data/example/system/lottery_pools/supplies.json)。

```json
{
  "label": "补给奖池",
  "price": {
    "type": "ITEMS",
    "items": [{ "id": "minecraft:emerald", "count": 1 }]
  },
  "entries": [
    {
      "weight": 9,
      "reward": {
        "id": "example:lottery/food",
        "label": "食物补给",
        "items": [{ "id": "minecraft:bread", "count": 8 }]
      }
    },
    {
      "weight": 1,
      "reward": {
        "id": "example:lottery/diamond",
        "label": "钻石补给",
        "items": [{ "id": "minecraft:diamond", "count": 1 }]
      }
    }
  ]
}
```

`label` 非空，启用的奖池至少有一项 `entries`。`weight` 默认为 1，必须为正整数；单项概率等于该项权重除以奖池总权重，上例为 90% 与 10%。每次独立抽取一个完整奖励包，没有保底、多连抽或额外 AI 调用。`reward` 复用奖励 Codec：`id` 为 `Identifier`，`items` 为 1～36 项原版 `ItemStackTemplate`；奖品内联定义，不依赖签到奖励池。默认和示例均只引用必装的原版物品。

`price` 为每次单抽的消耗，使用与商城相同的 `Price` Codec；可配置 `ITEMS`，或预留的 `SYSTEM_COIN`。省略整个 `price` 表示免费，免费奖池可重复抽取；不支持把空物品价格或 0 系统币当作免费。未接入支付适配器时，系统币奖池可查看，但不提供确认按钮。

完整界面进入“系统抽奖”，或输入 `/lottery [关键词]`；`/draw <奖池ID>` / `单抽 <奖池ID>` 只预览，显示奖品概率和费用后，必须单独点击或用 `选择 编号` 确认。奖池与奖品均按每页 8 项展示。确认引用有效期两分钟，绑定玩家、操作类型及目录版本；成功重载会使旧确认失效。模型和本地语义路由只能浏览或预览，不能直接确认扣款、修改权重或指定中奖奖品。

服务端在确认时抽样并持久化中奖快照，共用商城的订单、扣款与收件箱结算流程；重复确认同一引用只返回原结果，不重复扣款或重抽。待支付与结果未知订单不展示未结算奖品。扣款前在游戏线程验证整个奖池的物品模板，任何奖品无法构建时均失败；资源重载阶段不会构建 `ItemStack`。已结算奖品保留快照，目录修改不会改写；仍需从收件箱领取。

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

配置错误不会回退到不校验的发奖方式。诊断在服务端日志中；修改配置文件需要正常重启，修改奖励、商品或奖池数据包可正常重载。FTB 自身任务配置仍是独立入口。
