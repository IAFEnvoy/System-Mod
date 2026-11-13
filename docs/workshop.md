# 装备工坊：维修

模块类型与内置配置 ID 均为 `system:workshop`，客户端实现也是 `system:workshop`。它已加入默认模块 tag。管理员可用现有的 `/system module add/remove` 控制是否开放。

在主手拿一件受损装备，点击工坊中的“维修报价”，或在系统对话框输入 `/repair`、`/维修`。先显示物品、恢复耐久数和费用；只有点击“确认支付并维修”才扣费并恢复全部耐久。两步均不请求 AI。第一版不包括批量维修、重铸或强化。

## 模块定义

路径：`data/<namespace>/system/system_module/<path>.json`。

```json
{
  "order": 45,
  "server": {
    "type": "system:workshop",
    "costs": [
      { "type": "saturn:item", "items": ["minecraft:emerald"], "amount": 1 }
    ]
  },
  "client": "system:workshop"
}
```

| server 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | Identifier | 必填 | `system:workshop` |
| `costs` | Saturn Cost 数组 | 必填 | 每次维修一件物品的费用；显式 `[]` 表示免费 |

内置价格为每件 1 颗绿宝石，价格不随损耗变化。可配置 Saturn 资源或物品费用。费用定义解析失败会拒绝加载，不会因为容错而变成免费维修。修改模块参数需要重进世界或正常重启服务端。

## 报价与扣款

报价由服务端生成，同一玩家只保留最新的一份，两分钟后过期。报价绑定当前玩家连接、模块、主手栏位、原始 ItemStack 对象和包含组件的完整快照。切换栏位、替换物品、消耗耐久或修改组件后，旧报价被拒绝。登出、关闭模块或关闭服务端清理临时报价。

扣款使用现有 CommerceCosts 与 Saturn CostPayment；提供给物品费用的 Container 排除维修目标所在栏位，避免维修物本身被当作材料。自定义 Cost 实现应遵守 CostContext.itemTarget，不应绕过付款上下文直接修改玩家背包。

确认工具先通过 Agent 执行账本持久化 RUNNING，再在游戏线程重新核对目标，并在付款前消费报价。重复点击或重启后的旧按钮不会重复扣款；付款或修复出现不确定结果时返回 EFFECT_UNKNOWN，不自动重试。报价、费用求值和背包修改在游戏线程完成，文件保存仍在存储工作线程。

| 入口 | 值 |
| --- | --- |
| 报价动作 / 工具 | `system:repair_quote` / `game.repair.quote` |
| 确认动作 / 工具 | `system:repair_confirm` / `game.repair.confirm`，仅结构化确认 |
| 报价卡片 / 模板 | `system:repair_quote` / `assets/system/cards/repair_quote.json` |
| 修复后结果 | 新的主手物品鉴定快照 |
