# 奖励与非物品结果

签到、成就、商城、抽奖和在线作业共用 `Reward`。结果由服务端数据包确定，AI 只能选择已有奖励 ID；按钮只携带权益 ID，不能修改数量或上传 action。

奖励先进入[系统空间](system-space.md)，领取时才执行。纯系统币、经验和 action 奖励不要求背包空位。原有 `items` 保留；新增 `contents`，可以单独使用或混用：

```json
{
  "label": "example.reward.training",
  "items": [{ "id": "minecraft:bread", "count": 2 }],
  "contents": [
    { "content": { "type": "system:currency", "currency": "system:coin" }, "amount": 100 },
    { "content": { "type": "system:experience" }, "amount": 80 },
    { "content": { "type": "system:experience", "levels": true }, "amount": 2 },
    {
      "content": {
        "type": "system:action",
        "name": "example.reward.heal",
        "icon": { "id": "minecraft:golden_apple" },
        "action": { "type": "saturn:heal", "amount": 4 }
      },
      "amount": 1
    }
  ]
}
```

此定义可直接作为签到奖励；商城再添加 `costs`，在线作业添加 `duration`，奖池则放在 `entries[].reward` 内并补 `id`。名称字符串直接作为翻译键；字面量可写 `{"text":"恢复生命"}`。

商品、奖池、签到奖励和作业的根定义还支持 `display_condition` 与 `condition`，见[条目条件与奖励效果](catalog-conditions.md)。它们限制新权益的获得，不写入通用 `Reward`，也不在领取已获得奖励时重新检查。嵌套奖品不单独配置这些条件。

签到奖励的根定义还可声明 `offline_value`（有限正数），表示整个奖励包的离线估值；非物品或 Evaluate 无法估值的奖励需要此字段才能进入本地候选。该字段只参与[离线签到筛选](sign-in.md)，不写入通用奖励快照，也不改变奖励数量或系统币汇率。

| 奖励字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 翻译键 / Component | 必填 | 奖励名称 |
| `items` | ItemStackTemplate 数组 | `[]` | 0～36 项物品产出 |
| `contents` | SpaceStack 数组 | `[]` | 0～36 项通用产出 |

启用的签到奖励、商品要求至少有一项产出。奖池可配置两者都为空的空奖，仍需要 ID、名称和权重；空奖结算后没有待领取内容。

| SpaceStack 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `content` | SpaceContent | 必填 | `type` 从 `system:space_content_type` 注册表分派 |
| `amount` | 正 long | `1` | 数量、经验点 / 等级数，或 action 执行次数 |

## 内容类型

复用空间内容注册表，不另建奖励结果注册表；扩展方式见[系统空间](system-space.md#新内容类型)。

| `content.type` | 其他字段 | 领取行为 | Shift 每批 |
| --- | --- | --- | --- |
| `system:item` | `item`：ItemStackTemplate | 放入背包，保留组件；数量以外层 `amount` 为准 | 原版堆叠上限 |
| `system:currency` | `currency`：币种 ID | 增加对应钱包余额；要求当前经济模块和币种仍可用 | 64 |
| `system:experience` | `levels`：boolean，默认 `false` | `false` 增加经验点，`true` 增加原版等级 | 点数 100 / 等级 1 |
| `system:action` | `name`、`icon`（IconReference）、`action`（Saturn EntityAction）均必填 | 每份执行一次可信数据包动作 | 1 |

经验数量及合计不能超过 int 上限，另检查当前经验状态避免溢出。一次领取最多执行 64 个暂存 action，大包可在空间按份取出。Saturn 顺序 / 条件动作可复用，但技能、宠物和实体名册等业务系统仍需另外实现。

币种在数据包解码阶段保留 Holder 引用，不提前读取尚未绑定的定义；写入奖励时保存 ID 与展示快照。删除币种后玩家文件仍能解码和显示，再次领取要求当前币种存在。预览覆盖签到候选、商城报价 / 货架、奖池、作业和领取回执，显示名称、数量与图标；简约文本模式保留数量说明。

## 结算与恢复

- 商城 / 抽奖扣费前准备所有可能奖励，不执行效果，也不检查背包空位。未支付订单先预留空间容量；支付成功才发布条目，支付未知继续占位。
- 系统币入账与空间数量扣除在同一个玩家存档事务提交。余额溢出保持待领取；所有存档 I/O 在存储线程执行。
- 物品、经验和 action 在游戏线程执行，执行前校验参与条目并预检钱包。混合奖励随后在最终事务一起写入钱包与空间状态。
- 尚未产生游戏效果的失败恢复为可领取。开始修改游戏状态后异常、或重启发现中断的领取，标为待核对并禁止自动重发。原版玩家数据与系统文件不属于同一事务，跨文件崩溃或回档不能保证严格一次执行。

Saturn EntityAction 返回 `void`，原版经验与恢复事件也可能被其他模组取消或改写；没有异常表示已调用动作，不额外推断最终世界变化。示例的恢复动作没有可恢复生命时也会正常消耗一次。

商城和抽奖的已结清订单会自动归档，完整奖励及原费用保留，旧领取按钮仍返回已领取回执；未领取或结果未知的记录不会自动归档。支付未知可由管理员使用 `/system commerce resolve` 核对，选择 `paid` 只解锁原奖励，`unpaid` 只释放占位，不自动退款；此入口不处理领取效果未知。完整规则见[订单维护](commerce-orders.md)。

开发示例位于 `src/test-mod/resources/data/system/system/`，不进入发布 JAR。本次进行了编译与静态资源检查，未启动游戏验收。

## 里程碑与事实权益

签到、图鉴和可信任务事件可以先生成 `EarnedReward` 奖励快照。玩家在 `/records` 点击领取时，持久标记与系统空间入库在同一个玩家事务内保存；重复点击不重复入库。已获权益不因原定义被修改、删除或禁用而丢失。取出仍使用本页既有内容类型和恢复语义，见[进度奖励](progress-rewards.md)。
