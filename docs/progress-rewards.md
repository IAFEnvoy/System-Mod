# 事实记录、里程碑与任务积分

核心模块共用 `FactDataStorage`，保存事实唯一键、按事件和来源的累计计数、最近 64 条事实以及已达成奖励。签到和图鉴在自己的玩家事务内同步写入；外部集成发布可信服务端事实，由核心服务分批持久化。没有客户端或 AI 写入事实的接口。

## 查看与领取

在系统输入框执行 `/records`，或从签到／图鉴卡片进入“进度与奖励”。列表优先显示待领项目，每次最多发送 18 项，并支持翻页。`/record_claim <权益ID>` 或卡片按钮将已有奖励存入系统空间，随后在 `/space` 取出或兑现。

“待领取”与“已存入系统空间”分开记录。领取标记和空间入库在同一次玩家文件事务中提交；重复点击、请求重试或重登不会重复入库。已领取内容全部取出后，可清理空间的交付快照，但永久保留权益已转移标记。未知 ID 不会生成奖励，资源定义后来被删除或禁用也不取消已经达成的快照。空间满时保留待领奖励，腾出空间后可以再领。

签到计数、图鉴登记、事实与权益保存在现有玩家压缩 NBT 业务文件内，不另建全局配置或第二份玩家数据库。仍受 `system-storage.json` 的业务文件容量限制；永久去重身份不按最近 64 条事实的展示窗口淘汰。清空聊天不删除这些业务记录；`/system revoke` 仍会清空。

## 事实奖励定义

注册表：`system:fact_reward`，仅服务端加载。文件：`data/example/system/fact_reward/quest_points.json`。

```json
{
  "event": "system:quest_completed",
  "source": "system:ftb_quests",
  "subjects": ["0123456789ABCDEF"],
  "frequency": "subject",
  "reward": {
    "label": "example.reward.quest_points",
    "contents": [
      { "content": { "type": "system:currency", "currency": "system:quest_point" }, "amount": 5 }
    ]
  }
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `event` | Identifier | 必填 | 可信事件类型 |
| `source` | Identifier | 任意来源 | 精确匹配集成／内容来源；只是标识符，不是可选模组的类或注册表引用 |
| `subjects` | 字符串数组 | 空，即全部 | 精确匹配事实对象；FTBQ 使用大写 16 位十六进制任务 ID |
| `frequency` | 枚举 | `subject` | 下表所列的发奖频率 |
| `reward` | Reward | 必填 | 固定、服务端定义的奖励快照 |
| `enabled` | 布尔 | `true` | 是否产生新权益 |
| `display_condition` / `condition` | Saturn EntityCondition | 始终成立 | 在服务端捕获事实时核验，不在领取既有权益时重新阻止 |

| `frequency` | 去重范围 |
| --- | --- |
| `subject` | 同一规则、事件、来源和对象终生一次 |
| `event` | 上述范围下，每个稳定 occurrence 一次 |
| `daily` | 上述范围下，每个业务日最多一次 |
| `weekly` | 上述范围下，每周最多一次，周一开始 |
| `monthly` | 上述范围下，每个自然月最多一次 |

周期按事实发生时间及服务端 `timeZone` 计算，所有规则仍先做事实本身的永久去重。重复上报同一 occurrence 不会因为更改日期、时区或规则而获得第二次奖励。保留规则文件 ID 可防止编辑奖励或阈值后重复发放；改变规则 ID 代表新的奖励身份，不是普通改名。规则只作用于新记录的事实，不对已有历史自动重新算奖。

## 内置事实

| 事件 | 来源 | 对象／occurrence |
| --- | --- | --- |
| `system:signed_in` | `system:signin` | 业务日期／同一日期 |
| `system:collected` | 收藏系列 ID | 条目 ID／固定 `registered` |
| `system:quest_completed` | `system:ftb_quests` | 任务本地 ID／团队 ID 与原生完成时间 |

内置 `system:quest_point` 币种和 `system:quest_completed` 奖励规则，为每名玩家首次完成的每个普通 FTBQ 任务提供 1 积分权益。说明书任务和新手礼包不计入。任务完成事件与通知开关独立，通过原生 FTBQ 页面、系统卡片或团队成员完成都能接入；同一来源同一任务的重置／重复完成默认不重复奖励，可覆盖规则调整频率或对象范围。积分先进入可领奖励，领取到空间并兑现后才增加钱包余额。

第一次为玩家启用 FTBQ 模块时保存观察起点，不补发之前完成的任务。之后重登或文件实例更新时分批核对该起点以来仍保存在 FTBQ 的完成历史，也能发现玩家离线期间团队完成的普通任务；已经记录的事实被去重。核心批量保存失败会记录完整异常并保留队列，延后重试。FTBQ 若已经删除某次原生完成历史（例如可重复任务立即重置），进程崩溃前尚未保存的该次事件无法凭空恢复；其他集成也应在登录时从自己的权威历史重新上报，而非依赖内存队列跨进程存活。

## 其他模组接入

在服务端游戏线程发布 `PlayerFactEvent`，或调用等价入口：

```java
PlayerFacts.emit(player, new PlayerFact(
        PlayerFacts.QUEST_COMPLETED,
        Identifier.parse("example:quests"),
        nativeQuestId,
        stableCompletionOccurrence,
        nativeCompletionTimestamp));
```

`subject` 最多 512 字符、`occurrence` 最多 256 字符，均不能为空。occurrence 必须来自原生权威记录，并且重试时保持相同；不能每次随机生成 UUID 或使用重试时的当前时间。不得从玩家网络包或模型文本直接转成事实。接入其他 QuestProvider 不需要引用 FTBQ 类，只需从该来源的真实完成事件上报相同通用事实。

工坊分解成功结算会在同一玩家事务记录 `system:salvaged`：source 为分解规则 ID，subject 为原物品 ID，occurrence 为订单 ID。失败或未知不会产生成功事实；人工核对只处理原产物，不重跑事实奖励条件。

累计阈值目标、未达成项目与系列统计由独立[成就总览](achievements.md)提供，仍共享上述事实去重和领奖账本。
