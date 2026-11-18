# 无限出售与无限收购

`system:value_shop` 让系统按 Evaluate 估值出售物品，`system:value_buyback` 让玩家把物品卖给系统。两者是独立的模块定义，与固定库存共用交易底层；购买仍在「系统商城」，出售仍在「物品收购」，不会把两个方向混在一个页面。

内置模块 `system:value_shop`、`system:value_buyback` 已加入默认模块 tag。自定义系统需在 `modules` 中引用对应定义或自行配置。模块无需 AI；使用系统币时还需启用钱包功能，报酬及商品通过系统空间交付。

## 搜索与交易

- 打开页面或空查询只列固定条目，并提示无限库存需要搜索；不会生成、下发或渲染整个物品注册表。
- 在页面搜索，或在系统输入框使用 `/shop diamond`、`/buyback minecraft:iron_ingot`。客户端先用原版 `SearchTree<ItemStack>` 匹配当前语言的名称、tooltip、注册 ID 或 `#标签`，再上传有界候选供服务端处理；集合范围及服务端备用检索见[客户端物品检索](item-search.md)。
- 每批上传最多 32 个带完整组件的物品，服务端合并固定与生成条目后最多显示 128 项，沿用六列、最多三行的本地分页。还有候选时显示「继续搜索」，请求下一批；即使本批全被过滤，也保留该入口。单个来源及聚合仍最多保留 129 项判断截断，不缓存完整库存。
- 点击商品获取服务端报价，确认后才扣款或收走物品。同一已完成订单重复确认只返回原回执。
- 无限表示不设置库存或购买次数上限，每份交易为一个物品，保留候选组件。非默认组件必须是服务端 Evaluate 已知的精确组合，或在收购时由玩家实际持有；不会仅凭客户端附件和通配估值生成任意组件商品。收购严格匹配报价的完整组件。

## 无限出售定义

路径：`data/example/system/system_module/value_shop.json`。

```json
{
  "order": 60,
  "icon": { "id": "minecraft:emerald" },
  "server": {
    "type": "system:value_shop",
    "currency": "system:coin",
    "value_per_currency": 1.0,
    "price_multiplier": 1.0,
    "ceiling_multiplier": 1.3,
    "hide_fixed": true,
    "items": ["#minecraft:planks", "minecraft:iron_ingot"],
    "excluded_items": []
  },
  "client": "system:shop"
}
```

| `server` 字段 | 类型／默认值 | 说明 |
| --- | --- | --- |
| `type` | `system:value_shop` | 无限出售类型。 |
| `currency` | 币种 Holder；必填 | `system:currency` 注册表引用。 |
| `value_per_currency` | 正数；`1.0` | 每一个币种单位对应多少 Evaluate 价值。 |
| `price_multiplier` | 正数；`1.0` | 换算价格的倍率；与解锁上限独立。 |
| `ceiling_multiplier` | 正数；`1.3` | 可售单件价值上限为玩家 P90 × 此倍率；使用与离线签到相同的阶段校准事件。 |
| `hide_fixed` | 布尔；`true` | 隐藏与当前仍可购买的固定库存重复的生成条目。 |
| `items` | 可选 Saturn matcher | 省略允许全部物品；`[]` 允许零项；支持物品 ID、`#tag`、混合数组和 matcher 对象。 |
| `excluded_items` | Saturn matcher；`[]` | 排除物品，优先于 `items`。 |

倍率及汇率范围为 `[0.000001, 1000000000]`。Evaluate 单件价值为 `V`、汇率为 `R`、价格倍率为 `M` 时，出售单价为 `ceil(V / R × M)` 个币种单位。未知、零、非有限或超出安全整数范围的价值不会变成免费商品。

P90 按背包与已解锁配方的不同物品／组件样本计算，使用下取整经验分位数；单一样本不能建立发展水平。无有效进度时不开放无限出售，不使用签到的无上限起步窗口。阶段事件可进一步钳制上限，调整价格倍率不会提高物品解锁上限。

## 无限收购定义

路径：`data/example/system/system_module/value_buyback.json`。

```json
{
  "order": 60,
  "icon": { "id": "minecraft:gold_nugget" },
  "server": {
    "type": "system:value_buyback",
    "currency": "system:coin",
    "value_per_currency": 1.0,
    "price_multiplier": 0.5,
    "hide_fixed": true,
    "excluded_items": ["minecraft:barrier"]
  },
  "client": "system:shop"
}
```

| `server` 字段 | 类型／默认值 | 说明 |
| --- | --- | --- |
| `type` | `system:value_buyback` | 无限收购类型。 |
| `currency` | 币种 Holder；必填 | 玩家获得的报酬币种。 |
| `value_per_currency` | 正数；`1.0` | 与出售相同的换算口径。 |
| `price_multiplier` | 正数；类型默认 `1.0` | 收购单价为 `floor(V / R × M)`；内置定义配置为 `0.5`。 |
| `hide_fixed` | 布尔；`true` | 隐藏与仍可交易的固定收购项重复的生成条目。 |
| `items` / `excluded_items` | 可选／默认 `[]` | 与无限出售相同的准入、排除规则。 |

收购不受 P90 上限限制。向下取整后不足一个币种单位的物品不生成收购项，可调整汇率或倍率。建议同一币种的收购倍率不高于出售倍率，避免人为配置产生买卖套利。

## 固定库存优先与重新解锁

去重只比较同一交易方向、当前玩家启用模块提供的固定条目，且该条目必须可见并满足执行条件。出售按实际物品和组件比较；收购按固定条目的 `saturn:item` matcher 比较，tag 和自定义 matcher 同样有效。

没有限量的固定条目持续优先。有 `purchase_limit` 的条目仅在当前周期已结算次数达到上限时解除遮蔽；待付款或付款未知不算已经售完。额度按原有持久订单及归档累计数计算。每日／每周／每月额度重置后重新隐藏无限条目；隐藏不会删除历史订单或已获得的权益。

`hide_fixed:false` 仅关闭当前无限模块的去重，其余来源继续执行各自规则。多份无限模块有各自的 ID 与价格，不会互相吞掉商品。

## 报价和性能边界

生成条目不写入 `shop_offer` 注册表，不持久化整张库存。报价仅保存短期引用和可信交易快照，仍有两分钟有效期、每玩家 32 份／服务端 4096 份的上限。

候选批次限制为每堆叠 4 KiB／每批 24 KiB。服务端验证数量、组件、物品功能开关、查询与游标后签发玩家专属引用；生成商品 ID 绑定带组件的服务端选择器，选择器五分钟过期并有数量上限。旧卡片的引用过期或被淘汰时需重新搜索。

搜索、报价和付款使用同一来源解析逻辑；确认时重新检查模块权限、Evaluate 价值、价格版本、P90、固定库存与条件。旧卡片不能绕过当前规则。搜索结果子集不参与固定库存列表版本，逐项报价另绑来源与价格版本，防止从搜索列表进入详情时误报过期。

订单／额度读取在工作线程；游戏线程只进行世界查询、有限商品生成与支付。跨越限额周期边界的快照会被拒绝，重新查询即可。固定库存已结算次数在同一周期单调增加，稍旧的快照至多继续隐藏条目，不会提前解锁。Evaluate 失效后的首次查询仍可能触发其自身价值重建。

本轮执行 `compileJava testModClasses` 和资源静态检查；未启动 Minecraft，也未进行游戏内功能验收。
