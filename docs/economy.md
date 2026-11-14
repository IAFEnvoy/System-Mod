# 系统币与多币种钱包

经济模块的服务端类型、内置配置和客户端 ID 均为 `system:economy`，已加入默认模块 tag。系统输入框执行 `/balance`、`/wallet`、`/余额` 或 `/钱包`，也可点击侧栏“钱包”。游戏聊天栏可用 `/system balance`。查询不需要 AI，只返回请求者的余额；管理员另有管理指令。

内置币种为 `system:coin`（系统币），初始余额为 0。首版使用非负 `long` 整数单位，不提供小数、透支、玩家转账或自动兑换。多币种直接新增定义，不需要增加 Java 类型。

## 币种定义

原生数据包注册表为 `system:currency`，路径为 `data/<namespace>/system/currency/<path>.json`。文件 ID 就是币种 ID。元数据同步客户端，钱包余额不随注册表广播。

```json
{
  "name": "example.currency.contribution",
  "description": "example.currency.contribution.description",
  "icon": "minecraft:emerald",
  "order": 50
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | 翻译键字符串或 Component | 必填 | 币种名称，字符串直接作为翻译键 |
| `description` | 翻译键字符串或 Component | 空文本 | 币种说明，字符串直接作为翻译键 |
| `icon` | ItemStackTemplate | 必填 | 展示图标，不是实体货币，不消耗这个物品 |
| `order` | int | `0` | 越高越靠前，同值按 ID 排序 |

翻译键由客户端资源包提供。修改注册表后重新进入世界或正常重启服务器。暂时删除一个定义只隐藏该币种，不删除已保存余额；恢复相同 ID 后仍使用原余额。模块变体共用同一钱包。

模块定义路径为 `data/<namespace>/system/system_module/<path>.json`：

```json
{
  "order": 55,
  "server": { "type": "system:economy" },
  "client": "system:economy"
}
```

## 管理指令

以下游戏指令需要管理员权限，目标目前为在线且已绑定、已启用经济模块的玩家。玩家和币种支持原版补全。

| 指令 | 用途 |
| --- | --- |
| `/system economy currencies` | 列出当前定义的币种 ID |
| `/system economy get <玩家> <币种>` | 查询余额 |
| `/system economy add <玩家> <币种> <数量>` | 增加余额 |
| `/system economy remove <玩家> <币种> <数量>` | 扣除余额，余额不足拒绝整个操作 |
| `/system economy set <玩家> <币种> <数量>` | 设定余额 |

数量为非负整数，例如 `/system economy add Steve system:coin 100`。管理员增减在存储工作线程完成，保存成功后反馈新余额。不会导出可供 AI 铸币或管理别人钱包的 Tool。

## Saturn Cost：使用货币付款

费用类型为 `system:currency`，注册到 Saturn 的 `COST_TYPE`；商城、抽奖、维修的 `costs` 均可直接使用，沿用报价、确认和支付防重入逻辑。

```json
{
  "type": "system:currency",
  "currency": "system:coin",
  "amount": 10
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currency` | 注册表引用 | 必填，引用 `system:currency` 中的币种 |
| `amount` | Saturn NumberProvider | 必填，可为常量或表达式；定价时求值一次 |

费用必须是有限、非负、能由 double 精确表示的整数，单项最多 `9007199254740991`；不对小数自动取整。钱包余额本身保存为 long，最多 `9223372036854775807`，超出时拒绝，绝不回绕。

报价保留玩家连接和存储代次，退出重登、撤销或 reset 后的旧付款不能改新钱包。余额不足时拒绝扣款；同一绑定费用重复执行由 Saturn CostTransaction 返回原回执。一个账单应合并对同一币种的需求，多个费用或混合物品费用不提供跨项回滚。

费用展示统一调用 Saturn 的 `CostTarget.display(transaction.amount())`。各个目标返回物品预览和文字说明，`CurrencyCost.Target` 从币种 Holder 读取名称；`CommerceCosts` 只汇总展示数据，不再判断具体目标类型。新增自定义费用时，重写自己 Target 的 `display` 即可接入报价，展示过程不重新求值或执行支付。当前使用包含此 API 的 Saturn 1.3.1 发布版（CurseMaven 9606807），依赖方式见[开发文档](development.md)。

内置商城 `system:torch_bundle` 使用 16 系统币购买 4 个火把，补给奖池 `system:supplies` 每次消耗 1 系统币，装备工坊每次维修消耗 1 系统币。金额沿用原默认费用中的数量，数据包仍可覆盖 `costs`，替换币种或混合物品 / Saturn 资源费用。支付从经济模块钱包扣除，不消耗币种的展示图标；要求玩家已绑定且启用经济模块，初始余额仍为 0，不自动发币。

开发源集中的普通商品和付费奖池也使用系统币，保留原数量；任意原木 / 木板保留物品匹配费用，熟食套餐和金苹果使用系统币加物品的混合费用，免费奖池仍免费。可先执行 `/system economy add <玩家> system:coin 100`，再从原有报价、确认入口付款；这些开发条目只进入开发运行，不打入发布 JAR。

## EntityAction：增减余额

`system:add_currency` 注册到 Saturn 的实体 action 注册表，可放入支持 EntityAction 的触发器或其他数据驱动功能：

```json
{
  "type": "system:add_currency",
  "currency": "system:coin",
  "amount": 100
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currency` | 注册表引用 | 必填，目标币种 |
| `amount` | long | 必填；正数增加，负数扣除，0 不改变数额 |

只对服务端玩家生效，要求已绑定并启用经济模块。不足或溢出时不修改余额并记录拒绝原因。action 每次执行都会增减一次，本身不对触发事件去重；一次性奖励应由所属玩法记录领取状态。

## EntityCondition：检查余额

`system:currency` 注册到 Saturn 的实体 condition 注册表，复用 Saturn `Comparison`，只检查，不扣款：

```json
{
  "type": "system:currency",
  "currency": "system:coin",
  "comparison": ">=",
  "compare_to": 20
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currency` | 注册表引用 | 必填，目标币种 |
| `comparison` | string | 必填，支持 `<`、`<=`、`>`、`>=`、`==`、`!=` |
| `compare_to` | double | 必填，与当前余额比较的目标值 |

比较字段与 Saturn 的其他数值条件保持一致，替代原来的 `min`。例如上述配置判断余额是否至少为 20，`comparison: "=="` 搭配 `compare_to: 0` 判断余额是否为零。比较沿用 Saturn 的 double 精度，超过 `2^53` 的相邻整数可能无法区分；余额存储和增减运算仍为精确 long。

未绑定、经济模块未启用或玩家数据尚未就绪时返回 false。不要用它作为经济模块自身首次启用的前提。condition 通过 AgentStore.readLoaded 读取最后一次已发布的内存快照，不等待文件锁，也不会因后台保存而暂时判为 false；成功 action 的内存变更立即可被后续 condition 读取。action 遇到存储锁正忙会排入持久化工作线程，避免一次性奖励因忙碌丢失；这种情况下需等排队事务完成后才能看到新余额，不应假定后面的 condition 已能读取它。

## 存储、线程与限制

`WalletDataStorage` 位于 `module.builtin.storage`，owner/type 为 `system:economy`，币种 ID → long 余额保存于存档 `system/players/<UUID>.dat` 的压缩 NBT 模块数据中，不使用玩家附件。所有修改调用 markDirty，复制时隔离 Map；停用模块保留余额，revoke/reset 按现有语义删除业务数据。

管理员编辑走持久化事务。游戏线程的费用及 action 走 FileAgentStore.updateLoaded：拿不到锁直接拒绝，成功后由 ModuleManager 合并异步 flush；正常退出与服务端关闭也会保存脏数据。突然崩溃存在最后一次内存变更尚未落盘的窗口，钱包、物品背包和其他模组效果之间不提供跨存储事务。

查询工作流/动作是 `system:balance`，只读工具是 `economy.balance`，卡片类型为 `system:wallet`，模板为 `assets/system/cards/wallet.json`。客户端只拼装服务端快照；按钮只请求刷新，所有余额判断和修改留在服务端。

钱包按两列展示币种，每项在一行内显示图标、名称和右侧余额，沿用三行分页。币种介绍不占正文空间，悬停图标时通过原版物品 tooltip 查看。
