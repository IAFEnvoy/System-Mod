# 收藏图鉴

收藏图鉴使用服务端数据包定义系列、条目与里程碑。玩家主动点击“登记背包物品”时，服务端核对当前背包、装备和副手；不消耗物品，也不接受客户端提交的物品证明。登记结果按玩家持久保存，物品后来用掉也不会丢失进度。

系统界面输入 `/collection` 查看系列，`/collection system:default` 查看内置基础收藏，`/collection_register system:default` 登记。完整界面也提供“收藏图鉴”模块入口。列表和详情每次最多传输 18 项，卡片使用现有网格控件继续分页；可以返回系列列表。上述业务命令不注册到游戏的 `/system` 下。

## 模块定义

文件：`data/example/system/system_module/collection.json`。

```json
{
  "order": 75,
  "icon": { "id": "minecraft:book" },
  "server": {
    "type": "system:collection",
    "collections": ["example:minerals", "#example:plants"]
  },
  "client": "system:collection"
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.collections` | ID、`#tag` 或混合数组 | 整张收藏注册表 | 省略允许全部，空数组不提供系列；按当前工作流所属模块的可选项核验 |

系列 tag 位于 `data/example/tags/system/collection/plants.json`。模块仍需加入系统定义的模块列表或 tag。内置 `system:collection` 模块提供 `system:default` 基础矿物收藏，可通过数据包覆盖。

## 收藏系列

注册表：`system:collection`，只在服务端加载。文件：`data/example/system/collection/minerals.json`。

```json
{
  "label": "example.collection.minerals",
  "description": "example.collection.minerals.description",
  "entries": [
    {
      "id": "example:diamond",
      "label": "item.minecraft.diamond",
      "predicate": { "items": ["minecraft:diamond"] },
      "icon": { "id": "minecraft:diamond" }
    },
    {
      "id": "example:iron",
      "label": "item.minecraft.iron_ingot",
      "predicate": { "items": ["minecraft:iron_ingot"], "count": { "min": 4 } },
      "icon": { "id": "minecraft:iron_ingot" }
    }
  ],
  "milestones": [
    {
      "id": "example:first_mineral",
      "target": 1,
      "reward": {
        "label": "example.reward.first_mineral",
        "contents": [
          { "content": { "type": "system:currency", "currency": "system:coin" }, "amount": 2 }
        ]
      }
    }
  ]
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 文本组件 | 必填 | 字符串直接视为翻译键，由客户端资源包提供各语言文本 |
| `description` | 文本组件 | 空 | 系列详情说明 |
| `entries` | 条目数组，1～512 项 | 必填 | 同一系列的条目 ID 必须唯一 |
| `entries[].id` | Identifier | 必填 | 稳定的登记身份，改显示名或顺序不影响进度 |
| `entries[].label` | 文本组件 | 必填 | 条目显示名 |
| `entries[].predicate` | 原版 ItemPredicate | 必填 | 支持物品 ID／tag、数量及组件条件，格式跟随当前 MC 版本 |
| `entries[].icon` | ItemStackTemplate | 无 | 展示用物品，不参与匹配；系列列表使用首个条目的图标 |
| `milestones` | 数组，最多 128 项 | 空 | 同一系列的里程碑 ID 必须唯一 |
| `milestones[].id` | Identifier | 必填 | 稳定领奖身份 |
| `milestones[].target` | 正整数 | 必填 | 已登记条目数，不得超过当前系列条目数 |
| `milestones[].reward` | Reward | 必填 | 支持[统一奖励](rewards.md)的物品与非物品内容 |
| `enabled` | 布尔 | `true` | 禁用后不能新查看或登记 |
| `display_condition` / `condition` | Saturn EntityCondition | 始终成立 | 分别控制可见性与登记资格，均由服务端核验 |

`count` 检查一格 ItemStack 的数量，不合并多个背包格。空格不会匹配任何登记条件。一个实物可以登记多个不同条目，因此整合包应自行保证条目谓词表达所需的收藏语义。

## 达成与领取

一次登记将新增条目、`system:collected` 事实、累计计数与达成的里程碑快照在同一玩家事务中保存。已登记条目不重复累计；重复登记也能发现新配置的、现有进度已经达到的里程碑。实际奖励以首次达成时保存的快照为准。

达成后去 `/records` 的“进度与奖励”领取到系统空间，再从 `/space` 取出或兑现；重复点击不会重复入库。系列的里程碑 ID 是永久身份，调整阈值或奖励不会重发同一个里程碑。移除条目只影响当前进度统计，保留其历史登记；移除系列或禁用模块不会删除已经获得的奖励。清空聊天不清空收藏，撤销系统则按原有规则清空业务数据。

当前只做手动实物登记，不做全物品表自动收录、物品消耗、实体收藏或客户端上报曾经持有的物品。
