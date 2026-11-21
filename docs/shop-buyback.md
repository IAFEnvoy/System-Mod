# 固定物品收购

固定收购由数据包声明要交出的物品和报酬，复用商城的确认、限额及订单底层，不依赖 AI 或 Evaluate 估价。收购现在有独立的侧边栏页面和列表，只显示可出售的收购项；它与商城页面分开，数据定义仍可由不同模块文件提供后[合并](module-merging-design.md)。

## 使用

在系统界面的「物品收购」侧边栏页面搜索，或在系统输入框使用：

- `/shop [查询]`：只浏览购买条目。
- `/buyback [查询]`（别名 `/收购`）：仅查看收购条目，可搜索名称、ID、要交出的物品或 tag。
- `/sell <条目ID> [份数]`（别名 `/出售`）：打开指定收购项的报价确认卡片。
- `/buy <条目ID> [份数]`：仅接受购买条目。

这些业务命令只在系统界面内使用。自然语言也可匹配收购浏览／报价，最终出售仍需要玩家点击确认。

默认商城包含 `system:cobblestone_buyback`：交出 **64 个圆石**，获得 **1 系统币**；每人每天最多出售 **4 份**。另有小麦收购：32 小麦换 2 币，每日最多 2 份，见[发布默认数据](release-data.md)。可覆盖同路径文件或用 `{"enabled":false}` 禁用。

列表展示需要交出的物品，tooltip 展示收购要求与报酬。点击后显示「交出物品」「收购报酬」与「确认出售」。物品不足、条件不满足或额度用完时，不会生成可支付报价；补齐物品后可重新报价。

确认成功后，报酬记入系统空间，点击「领取」交付；币种报酬在领取时加入钱包。重复点击已完成的确认操作只返回原订单，不再次扣除物品。未领取报酬遵守原有系统空间与订单恢复规则。

## 条目定义

注册表仍是 `system:shop_offer`，文件路径：

`data/example/system/shop_offer/cobblestone_buyback.json`

```json
{
  "label": "example.shop.cobblestone_buyback",
  "trade": "buyback",
  "costs": [
    { "type": "saturn:item", "items": "minecraft:cobblestone", "amount": 64 }
  ],
  "contents": [
    { "content": { "type": "system:currency", "currency": "system:coin" }, "amount": 1 }
  ],
  "purchase_limit": { "count": 4, "period": "daily" }
}
```

| 字段 | 类型／默认值 | 说明 |
| --- | --- | --- |
| `label` | 翻译键字符串或 Component；必填 | 非空名称；字符串按客户端语言解析。 |
| `trade` | `purchase` / `buyback`；默认 `purchase` | 原有商品不必修改；收购使用 `buyback`。 |
| `costs` | 非空 Saturn Cost 列表 | 收购只接受 `saturn:item`，表示从背包收走的物品。 |
| `items` | ItemStackTemplate 列表；默认 `[]` | 给玩家的物品报酬。 |
| `contents` | SpaceStack 列表；默认 `[]` | 给玩家的系统币、经验等非物品报酬；与 `items` 至少一项非空。 |
| `purchase_limit` | 可选对象 | 沿用商品的每玩家限额，按出售份数计数。支持 `daily` / `weekly` / `monthly` / `lifetime`。 |
| `display_condition` | 可选 Saturn EntityCondition | 是否显示，默认始终显示。 |
| `condition` | 可选 Saturn EntityCondition | 是否可出售，默认始终允许；确认前复核。 |
| `enabled` | 布尔值；默认 `true` | 为 `false` 时禁用，可省略其他字段。 |

`saturn:item.items` 支持物品 ID、`#tag`、混合数组及 Saturn 注册的 matcher。一个 Cost 内的匹配项是可替代关系，总共交出 `amount` 个；多个 Cost 则都必须支付。普通物品 ID 不区分命名、附魔等组件，匹配方式与选材顺序由 Saturn 决定。不要给多个 Cost 配置重叠材料；跨 Cost 不提供原子回滚，见[费用与订单语义](datapacks.md)。

这里的「固定」表示输入和报酬由数据包指定，不按 Evaluate 动态生成。`amount` 仍支持 Saturn NumberProvider；报价冻结本次求值，确认时重新检查背包。报价两分钟有效，重启、目录或权限变化后需重新获取。

## 加入收购目录

条目不会因为存在于注册表中就自动加入默认系统。向 `data/system/tags/system/shop_offer/default.json` 追加条目，或自建 tag 与模块配置：

`data/example/tags/system/shop_offer/buyback.json`

```json
{ "values": ["example:cobblestone_buyback"] }
```

`data/example/system/system_module/buyback.json`

```json
{
  "order": 60,
  "server": { "type": "system:shop", "offers": "#example:buyback" },
  "client": "system:shop"
}
```

在系统定义的 `modules` 中启用 `example:buyback`。它会显示在独立的「物品收购」页面；服务端仍与商城共用交易账本、确认和系统空间交付。不同成员仍可单独启停。同一收购条目被多个成员暴露时只列一次，共用限额。

收购订单仍归 `shop` 账本，管理员可通过 `/system commerce ... shop` 核对与归档。订单保存交易方向、报酬及本次收取物品的展示快照；数据包修改后不会改写已经获得的权益。定义和 tag 在世界加载时读取，修改后需重开存档或重启服务端。

## 与无限收购配合

启用 `system:value_buyback` 可按 Evaluate 自动估价收购物品，两份模块定义仍独立。无限来源的 `hide_fixed` 默认为 `true`，优先显示仍可交易的固定收购项；当前周期固定限额真正用完后开放同物品的无限收购，额度重置后重新隐藏。无限来源仅在搜索时出现，完整参数见[无限交易](value-commerce.md)。

## 批量购买与出售

固定商城、固定收购、Evaluate 动态商城／收购共用批量报价：`/buy <ID> [份数]`、`/sell <ID> [份数]`，默认 1 份，允许 1～100 份。报价卡提供 1／8／16／64 份切换按钮，切换只更新报价，仍需点击最终确认。份数乘以每份商品和费用，不是直接指定物品总数。例如默认圆石收购每份交出 64 圆石换 1 系统币，4 份即交出 256 圆石、获得 4 系统币，同时用完当日该条目的额度。

费用公式先按一份求值，再整体乘以份数并冻结；限购按份数预留与结算，不按订单条数计算。数量超过剩余额度会拒绝，不能用一次大订单绕过限额。动态商品仍需满足当前 P90、固定库存优先和修订校验，不接受客户端自报商品内容。

批量物品通过通用 SpaceStack 数量表示，避免构造超过堆叠上限的 ItemStack；物品、系统币、经验及 action 的数量均按份数增加。确认成功后整个批次只创建一份订单及唯一奖励权益，重复确认返回原订单。旧订单没有 `quantity` 时按 1 份读取。

## 周期轮换库存

固定购买和收购模块均可添加可选的 `server.rotation`，按每日／每周／每月稳定选取本期条目，并在卡片显示下次刷新时间。两个交易方向独立选品，限额周期独立计算；已结算报酬不受轮换影响。配置字段、共享／个人模式、模块合并和旧报价处理见[商城周期轮换](shop-rotation.md)。
