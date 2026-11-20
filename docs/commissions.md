# 固定物品交付委托

原生来源为 `system:commission`，不依赖 FTB Quests。它接入通用任务列表、分组、详情、提交、领奖和固定 HUD；任务来源显示为“系统委托”。在系统界面使用 `/commissions`、`/委托`、左侧“交付委托”入口，或者使用通用 `/quests`、`/view`、`/submittable`、`/claimable`。这些业务指令不注册到游戏的 `/system` 指令树。

## 模块定义

`data/example/system/system_module/commission.json`：

```json
{
  "order": 39,
  "icon": { "id": "minecraft:paper" },
  "server": {
    "type": "system:commission",
    "commissions": "#example:deliveries",
    "capacity": 16
  },
  "client": "system:commission"
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.commissions` | ID、`#tag` 或混合数组 | 全部条目 | 可接取的委托；已接取履约不随 tag 删除而丢失 |
| `server.capacity` | 整数 1～128 | 16 | 同时占用的委托数，已完成未领奖和结果未知也占用 |

同一类型的多份模块配置共用来源与玩家账本，可接取条目取当前启用配置的并集，容量取最高值。内置模块使用 `#system:default`，发布包的该 tag 为空；内容由数据包提供。开发样例仅放在 `src/test-mod/`。

## 委托定义

注册表 `system:commission`，文件 `data/example/system/commission/daily_stone.json`：

```json
{
  "label": { "text": "石料交付" },
  "description": { "text": "交付一批圆石，换取补给。" },
  "chapter": { "text": "日常委托" },
  "icon": { "id": "minecraft:cobblestone" },
  "costs": [
    { "type": "saturn:item", "items": "minecraft:cobblestone", "amount": 16 }
  ],
  "reward": {
    "label": { "text": "交付报酬" },
    "items": [{ "id": "minecraft:bread", "count": 4 }],
    "contents": [{ "content": { "type": "system:experience" }, "amount": 20 }]
  },
  "limit": { "period": "daily", "count": 2 }
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | Component | 必填 | 委托标题，非空 |
| `description` | Component | 空 | 任务正文，按普通文本展示 |
| `chapter` | Component | 交付委托 | 通用任务列表的分组 |
| `icon` | IconReference | 无 | 列表图标 |
| `costs` | Cost 数组 | 必填 | 1～16 条 `saturn:item`，支持物品／tag／组件匹配与 Saturn 数量公式；首版仅收物品 |
| `reward` | Reward | 必填 | 非空报酬，支持物品和通用 `contents`，包括系统币、经验、action 和多选一 |
| `limit` | 对象 | 累计 1 次 | 同一玩家、同一委托的完成次数上限 |
| `limit.period` | 字符串 | `lifetime` | `lifetime`／`daily`／`weekly`／`monthly` |
| `limit.count` | 正整数 | 必填（写出 limit 时） | 当前周期最大完成次数 |
| `enabled` | 布尔 | true | 是否可提供新委托 |
| `order` | 整数 | 0 | 数值大者靠前，同值按 ID |
| `display_condition` | EntityCondition | 始终 | 新委托可见条件 |
| `condition` | EntityCondition | 始终 | 新接取资格 |
| `activity` | ActivityWindow | 始终开放 | [共用活动窗口](activity-windows.md)，限制新接取 |

自然周期使用服务器业务时区，周一为每周第一天。按**交付成功的日期**计数；放弃不增加完成次数，不能清空累计记录。时钟回拨到更早周期不会重新获得次数。同一条委托同时最多一个未结束履约。

## 状态和交互

1. 查看任务详情，点击“接取任务”。不扣材料，保存本次标题、材料规则、报酬与唯一履约 ID。
2. 准备整批材料后点击“提交任务”，或在系统界面使用 `/submit <编号或名称>`。此时只生成两分钟有效的玩家专属材料报价。
3. 核对实际物品及数量，点击“确认交付材料”。确认前重新核验履约状态、报价身份和背包，才扣除整批材料。
4. 交付成功后独立“领取”报酬，报酬完整存入系统空间；再从空间取出物品／系统币等。多选一仍在空间内选择。
5. 未交付的委托可以放弃。处理中、结果未知和已完成待领奖状态不能放弃；周期额度未满时，领奖后可以再次接取。

AI 不能代为确认交付；接取、放弃、确认均要求玩家选择卡片动作。语音／文本可以查询、打开详情、生成交付预览和领取已有权益。任务编号来自当前列表；旧接取／放弃／交付按钮带版本，状态改变后会拒绝旧操作。固定卡片只读取当前背包与存储快照，不进行扣除或持久化。

已有接取视作已获得的履约资格：活动关闭、条件变化、定义修改或删除后，仍按接取快照交付、领奖；所属委托功能仍需启用。不改变 FTBQ 的勾选／提交语义。首版没有玩家发布、多人托管、部分交付、时限失败、复杂目标树或自动接取。

## 持久化与异常恢复

`CommissionDataStorage` 通过模块 DataStorage 保存当前履约、永久完成计数和状态版本，使用现有玩家存档 NBT。每玩家最多记录 4096 种委托，活跃数量另受 capacity 控制；已领取报酬保留原系统空间权益，重复领奖不重复入库。

材料扣除前先在同一玩家事务中持久化 `prepared` 并预留完整报酬的系统空间容量；空间不足时不扣材料。完成后仍保持锁定，玩家领奖时才解锁入库；确认未消耗材料时释放预留。真实物品修改只在服务端游戏线程执行；结算保存失败仅重试存储，不再次扣除。重登或重启发现未结清记录时转为 `unknown`，禁止继续交付或放弃，等待核对。

管理员命令：

```text
/system commission list <player>
/system commission resolve <player> <履约ID> consumed <原因>
/system commission resolve <player> <履约ID> not_consumed <原因>
```

确认已经完整收取材料选 consumed，补记完成后由玩家领奖；确认未收取选 not_consumed，恢复为已接取。必须先核对实际情况，不用重发交付来尝试修复；核对结果、执行者与原因写入日志。部分扣除也保持 unknown，由管理员处理物品差额后再选择结论。
