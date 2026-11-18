# 成就总览与系列

`system:achievement` 是双端模块，内置配置已加入默认模块 tag。系统界面点击“成就总览”，或输入 `/achievements`、`/成就`，即可查看已达成、未达成、系列统计、目标进度与奖励。服务端每批发送 18 项，批内使用卡片 grid 的本地分页；系列统计展示当前批次涉及的系列，计数仍涵盖全部可见项目；查询本身不会增加计数或生成奖励。

## 成就定义

服务端数据包注册表为 `system:achievement`，文件路径 `data/<namespace>/system/achievement/<path>.json`，条目 ID 是稳定身份，不在 JSON 中另填 `id`。

```json
{
  "label": "example.achievement.signin_7",
  "description": "example.achievement.signin_7.description",
  "series": "example.achievement.series.signin",
  "event": "system:signed_in",
  "target": 7,
  "reward": {
    "label": "example.achievement.signin_7",
    "contents": [
      { "content": { "type": "system:currency", "currency": "system:coin" }, "amount": 10 }
    ]
  },
  "order": 20
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 翻译键字符串或 Component | 必填 | 名称 |
| `description` | 翻译键字符串或 Component | 空文本 | 目标说明 |
| `series` | 翻译键字符串或 Component | 通用系列 | 相同 Component 汇总为同一系列，不另设系列注册表 |
| `event` | Identifier | 必填 | 可信事实事件，如 `system:signed_in`、`system:collected`、`system:quest_completed`、`system:salvaged` |
| `source` | Identifier | 不限制来源 | 可选；计数限定到对应事件来源 |
| `target` | 正 long | 必填 | 终生累计事件数，不是物品数量或当前背包数量 |
| `reward` | Reward | 必填 | 物品、系统币、经验、Saturn action 等，见[奖励](rewards.md) |
| `order` | int | `0` | 越高越靠前，同值按条目 ID 排序 |
| `enabled` | boolean | `true` | 是否接受新的达成 |
| `display_condition` | Saturn EntityCondition | 始终成立 | 未达成时是否显示；达成后保留权益展示 |
| `condition` | Saturn EntityCondition | 始终成立 | 新事件产生权益时的资格条件 |

模块定义为 `{"server":{"type":"system:achievement"},"client":"system:achievement"}`，没有另设成就列表筛选。此注册表仅在服务端加载，客户端只收到展示快照，不执行条件或奖励规则。更改定义需重开世界／重启服务器。

## 记录与领取

复用核心 `FactDataStorage`：服务端适配器采集事实、去重、增加事件／来源计数，在同一次玩家事务中检查阈值并保存奖励快照。每项成就仅有一个 `achievement:<条目ID>` 权益，同一事实重放或重复领取不会重复授予。

进度使用已有终生计数，但只在下一次匹配、条件成立的新事实发生时生成新成就权益；打开列表不会追溯补发。没有达成资格时，即使数值已达目标，也仍显示“未达成”。内置首次签到、累计签到七次、登记五项收藏三项定义；它们不调用 AI。

点击待领取成就会进入共用进度奖励列表，并将该奖励转入系统空间；随后从空间实际取出物品／执行非物品结果。已获得的奖励快照不会因定义修改而变化。删除成就定义后，总览不再列出该定义，但已达成奖励仍能从 `/records` 的记录中领取。重置玩家业务数据会清除相应进度。

新事件由可信服务端适配器通过 `PlayerFacts.emit`／`PlayerFactEvent` 提供；按钮、客户端和模型不能直接提交进度。世界首杀裁决、职业计数采集和独立研究系统尚未接入。
