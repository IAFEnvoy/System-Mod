# 条目条件与奖励效果

商品、奖池、签到奖励和在线作业支持条目级条件，统一复用 Saturn `EntityCondition`。模块自己的条件决定入口是否启用；这里的条件决定入口内某个具体条目是否可见、是否可执行。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `display_condition` | EntityCondition 对象或数组 | 始终成立 | 不成立时从查询结果和候选中隐藏，也不能直接按 ID 操作 |
| `condition` | EntityCondition 对象或数组 | 始终成立 | 不成立时仍可预览，显示“条件未满足”，不能购买、抽取、选作签到奖励或开始作业 |

两个字段直接写在条目根对象中，不额外包一层 `conditions`。省略时放行；数组表示各条件全部成立，其他组合可使用 Saturn 的组合条件。格式错误或未注册的条件类型会使注册表加载失败，不作为“无条件”处理。

## 支持范围

| 注册表 | 定义路径 | 最终复核时机 |
| --- | --- | --- |
| `system:shop_offer` | `data/<namespace>/system/shop_offer/<path>.json` | 确认订单、实际扣款前 |
| `system:lottery_pool` | `data/<namespace>/system/lottery_pool/<path>.json` | 确认抽取、实际扣款前 |
| `system:signin_reward` | `data/<namespace>/system/signin_reward/<path>.json` | AI 选择候选前，以及选择结束、签到奖励入账前 |
| `system:online_job` | `data/<namespace>/system/online_job/<path>.json` | 开始新作业前 |

`enabled: false` 仍优先禁用条目；模块是否启用、模块的 ID / tag 可选项、限购和费用检查也继续生效。商品和奖池可以打开被执行条件锁定的详情，但操作按钮禁用；作业开始按钮禁用。签到状态展示当前可见候选及锁定提示，AI 只收到可执行候选；没有合格候选时不请求签到模型。

成就规则的 `rewardId` 引用签到奖励注册表，因此新奖励入账也检查该奖励条目的条件，但不要求它属于玩家签到模块的 `rewards` 可选项。条件只限制奖励入账，不撤销同一成就规则独立执行的功能解锁。

本次不提供每个奖品的独立条件或动态权重。抽奖条件限制整个奖池，不在付款后按条件删除某些奖品，避免改变已公示概率。

## 示例：阶段商品

完成“获得升级”进度后才展示，原版经验等级达到 10 才允许购买。购买消耗 8 圆石，结果为 80 经验点，进入系统空间后领取。

```json
{
  "label": "example.shop.training",
  "display_condition": {
    "type": "saturn:advancement",
    "advancement": "minecraft:story/upgrade_tools"
  },
  "condition": {
    "type": "saturn:experience_level",
    "comparison": ">=",
    "compare_to": 10
  },
  "costs": [
    { "type": "saturn:item", "items": "minecraft:cobblestone", "amount": 8 }
  ],
  "contents": [
    { "content": { "type": "system:experience" }, "amount": 80 }
  ]
}
```

同样的两个条件字段可直接添加到奖池、签到奖励和作业根对象。开发示例位于 `src/test-mod/resources/data/system/system/` 的 `shop_offer/test_non_item.json`、`lottery_pool/test_non_item.json`、`signin_reward/test_non_item.json` 和 `online_job/test_experience.json`，不进入发布 JAR。

多条件示例：

```json
{
  "condition": [
    { "type": "saturn:experience_level", "comparison": ">=", "compare_to": 10 },
    { "type": "system:currency", "currency": "system:coin", "comparison": ">=", "compare_to": 100 }
  ]
}
```

余额条件只检查余额，不扣款；真正收费仍写 `costs`。不要把条件当作一次性效果或随机奖励结算器：查询与执行会分别求值，随机条件可能得到不同结果。

## 权益与线程

条件在服务端游戏线程读取当前玩家与世界，客户端只收到筛选后的展示快照和可用状态。预览和实际操作分别检查，因此不能用旧卡片、旧报价或直接提交 ID 绕过条件。存档读写继续在工作线程，不为判断条件同步读取玩家文件。自定义条件也应只读取已加载状态，避免阻塞游戏线程。

已付款订单、已入账签到 / 成就奖励和已开始作业保留获得时的结果快照。条件后来不成立或条目被隐藏，不影响这些结果的正常领取；已经开始的作业不会因为这里的条件暂停或重置。领取仍遵守系统空间、模块能力和对应内容类型的校验。

条件求值抛出运行时异常时按不满足处理，并记录条件类、玩家 UUID 和完整堆栈。刷新列表或重新预览会取得最新状态；注册表定义本身在世界加载时读取，修改 JSON 需要重开世界 / 重启服务器。

## 效果与扩展

执行效果继续使用[奖励 `contents`](rewards.md) 中的 `system:action`，通过 Saturn `EntityAction` 分派；在系统空间领取对应份数时执行。没有新增另一套购买后 / 开工后 action，也不会在预览中执行效果。

四类定义实现 `ConditionalEntry`，共用 `EntryConditions` 的平铺 MapCodec。`CatalogAccess` 负责调度到游戏线程和统一求值；商城服务 `CommerceCatalogs.View` 携带可见目录及锁定 ID 集合，作业服务采用相同求值方法。作业卡片使用展示专用 Codec，不把完整服务端条件写入客户端快照。
