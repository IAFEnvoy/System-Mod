# 扩展配置

旧的数据包注册表与 `SystemFunction` 已移除。助手行为、模型和成就规则来自服务端 `config/system/system-agent.json`；签到奖励池、商城目录和模型提示词有独立的数据包入口，不使用旧的 `data/.../system/system` 注册表。客户端不能提供价格、商品内容或触发规则。

完整示例见 [服务端配置](../examples/agent/system-agent.json)。首次初始化会生成默认配置，修改后正常重启。密钥只使用 `apiKeyEnvironment` 指定的环境变量。默认 DeepSeek 与其他 OpenAI 兼容服务的配置见 [模型接口与 DeepSeek 兼容](model-api.md)。

主配置的 `semantic` 对象控制轻量本地意图路由，与奖励/商品数据包分别配置。新配置默认 ENFORCE，不需要模型文件；旧配置开关保留，说明见 [轻量本地意图路由](semantic-routing.md)。

## 提示词模板

模型提示词使用 UTF-8 `.txt` 文件，支持 UTF-8 BOM，不经过 JSON 注册表。服务端 `PromptReloadListener` 在启动和 `/reload` 时扫描 `data/<namespace>/system/prompts/<path>.txt`，模板 ID 为 `<namespace>:<path>`。JAR 内自带以下默认模板；在数据包中放置同路径文件即可按数据包优先级整份覆盖，不必复制另一份未修改的模板：

| 数据包文件 | 模板 ID | 可用占位符 |
| --- | --- | --- |
| `data/system/system/prompts/routing.txt` | `system:routing` | `{{player_language}}`、`{{capabilities}}` |
| `data/system/system/prompts/sign_in.txt` | `system:sign_in` | `{{player_language}}` |

`{{player_language}}` 由执行上下文自动提供，例如 `zh_cn`、`en_us`，来源为 MC 同步的玩家语言，模块不能通过同名参数覆盖它。`{{capabilities}}` 由路由器传入当前已注册的交互模块 JSON，包含说明和输入 schema；路由模板必须保留这个占位符。签到的玩家快照、候选奖励和日期仍通过用户消息的结构化 JSON 传入，不内联成系统指令。

例如，签到模板中可以写 `Write one short reason in {{player_language}}.`。占位符名为 1..64 位小写字母、数字或下划线，首位必须为字母；不支持表达式、脚本或递归替换。变量值中的 `{{...}}`、`$`、反斜杠均按原文保留，不执行第二次展开；普通 JSON 的单层或嵌套花括号可直接使用。`{{` 保留给占位符语法。内置模板使用未知占位符、语法错误、空文件、缺失必要模板或移除路由的 `{{capabilities}}` 都会拒绝重载。

每个模板最多 32KiB，最多 128 个模板、UTF-8 正文总计最多 1MiB。成功安装服务器资源后，在下一服务端 tick 发布不可变模板快照；失败时继续使用之前的快照，不发布半份模板。已经开始执行的任务保持原版本，新任务读取新版本；精确意图缓存的键也包含版本，旧任务的模型决策不会被新版模板复用。移除覆盖文件后恢复 JAR 内默认模板，无需复制到 `config`。

扩展模块可添加其他命名空间或子目录，例如 `data/example/system/prompts/notice.txt`，然后使用 `context.prompt("example:notice", Map.of("event_name", name))` 渲染，再将结果传给原来的 `context.model(...)`。扩展占位符由模块提供，缺失变量在请求模型前报错；提示词文件本身不会自动注册模块或获得工具权限。预算、奖励候选校验、玩家隔离与支付确认仍由 Java 代码强制执行。

## 奖励目录

`rewards` 中每个条目包含稳定 `id`、简短 `label`、`items` 列表；`shopOffers` 中每个条目另有 `price`。它们是对应数据包目录为空时的默认值。物品使用注册 ID 和数量；每包最多 8 项，每项 1..64，产出数量不得超过该物品堆叠上限。没有任意 NBT 或组件注入。配置中使用 `[]` 可关闭默认奖励池或商城。

AI 只返回候选奖励 ID，不能修改条目数量。入账保存完整奖励快照，修改目录不会改写已经获得的权益。正常签到必须成功调用 AI；成就固定奖励不受此要求影响。

## 数据包目录

完整可复制的示例位于 [examples/commerce-pack](../examples/commerce-pack)。把该目录复制到服务器存档的 `datapacks/` 下，使用正常的数据包加载流程或 `/reload`。示例不会自动安装，不进入发布 JAR。`pack.mcmeta` 格式根据当前依赖的 Minecraft 26.1.2 `version.json` 设置为 101.1。

| 文件路径 | 条目 ID | 用途 |
| --- | --- | --- |
| `data/<namespace>/system/signin_rewards/<path>.json` | `<namespace>:<path>` | AI 签到候选奖励包 |
| `data/<namespace>/system/shop_offers/<path>.json` | `<namespace>:<path>` | 商城商品和价格 |

两个目录分别替换：只要存在该类资源文件，就完全使用该类数据包条目，不与 JSON 默认目录混合。资源文件同路径按数据包优先级覆盖；`{"enabled":false}` 可以禁用单个条目。全部禁用会形成空目录，移除所有该类资源则恢复配置默认值。最多启用 16 个签到包、256 个商品；每个文件最多 16KiB。新增记录只使用此处的路径，不恢复旧功能定义。

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

`price.type` 支持 `ITEMS` 和预留的 `SYSTEM_COIN`。一个条目选择一种支付方式；需要两种购买方式时定义两个商品条目。物品价格的所有材料都必须满足；重复物品 ID 的数量会合并。系统币格式为 `{"type":"SYSTEM_COIN","coins":20}`，金额为 1..1,000,000,000 的整数，未安装可信支付适配器时只展示、不能购买。价格不能同时使用物品与系统币。

标签为纯文本，最多 128 字符；未知字段、无效 ID、超限数量、缺失价格或无效物品会拒绝本轮重载，不发布半份目录。成功重载会更换目录版本，旧报价失效；正在推理的签到决策提交前需要校验版本。已入账的签到/商城奖励、订单和当日唯一标记均保留。

成就 `rewardId` 引用当前奖励池 ID。启用示例数据包后，示例中的 `mining` 变为 `system:mining`，应同步调整配置中的规则引用。

## 成就规则

把 [成就规则示例](../examples/agent/advancement-rules.json) 的数组内容填入主配置 `advancementRules`。示例默认没有自动启用，规则字段为：

| 字段 | 内容 |
| --- | --- |
| `id` | 稳定规则 ID，普通调整不要换 ID 来重复发奖 |
| `advancementId` | Minecraft 成就 ID；只监听全部条件完成 |
| `moduleId` | 已注册且允许事件入口的模块 ID |
| `input` | 受信管理员配置的模块输入 |

内置 `system:advancement_followup` 接受 `featureId` 与 `rewardId`，执行确保解锁标记存在、将候选目录中的固定奖励入账。适配器另外注入 `ruleId` 与 `advancementId`，不接受玩家伪造事件。

已有成就默认只建立基线，不追溯补发。规则/成就去重记录不随聊天删除，也不随重启清除。更改规则目标、移除规则或删除去重记录会改变业务语义，需要管理员审查；当前无自动历史补发和热重载。

## 策略覆盖

`policies` 的键是模块 ID，值必须提供全部 `ExecutionPolicy` 字段。不同模块有自己的模型数、工具数、写步骤、tokens 与期限。`0` 模型次数表示禁止，不能写负数表示无限。未知模块配置会导致助手初始化失败并停止奖励写入。

配置错误不会回退到不校验的发奖方式。诊断在服务端日志中；修改主配置需要正常重启，修改奖励/商品数据包可正常重载。FTB 自身任务配置仍是独立入口。
