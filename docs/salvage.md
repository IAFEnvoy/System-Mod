# 装备分解

分解属于 `system:workshop`，与维修共享工坊入口。主手拿数量为 **1** 的物品，点击“分解主手装备”，或在系统界面输入 `/salvage`、`/分解`。自然语言和语音请求也只打开方案；核对原物品 tooltip、额外费用和确定产物后，点击“确认分解”才会销毁整件物品。产物存入系统空间，可以分次取出。

该流程支持离线模式，不依赖 AI。自由聊天和扩展兜底可以调用报价，但确认工作流不进入模型目录，确认工具也检查当前调用必须是玩家的结构化确认动作，事件与嵌套工具不能代为销毁。取消按钮会废弃当前方案。

首版不包含批量处理、自动反推合成材料、随机返材或附魔提取。附魔及其他组件随原物品一起消耗，不复制到材料上。

## 开启分解

模块条目：`data/<namespace>/system/system_module/<path>.json`。

```json
{
  "order": 45,
  "icon": { "id": "minecraft:anvil" },
  "server": {
    "type": "system:workshop",
    "costs": [
      { "type": "system:currency", "currency": "system:coin", "amount": 1 }
    ],
    "salvage": "#system:equipment"
  },
  "client": "system:workshop"
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.costs` | Saturn Cost 数组 | 必填 | 只用于维修；分解费用由各条规则定义 |
| `server.salvage` | ID、`#tag` 或混合数组 | `[]` | 工坊允许使用的分解规则；省略或空数组关闭分解 |

内置工坊启用 `#system:equipment`，包括铁／金工具与护甲、钻石剑／镐／斧／锄与护甲。默认规则按制造材料的一半和剩余耐久返材，向下取整；铁／金以粒为单位，钻石以钻石为单位。没有新增碎片物品。低耐久或不足一个单位时可能没有产物，此时拒绝生成销毁方案。内置规则可由数据包替换、扩充或通过 tag 重新筛选；它们不尝试还原整合包修改后的合成成本。

规则注册表仅服务端加载。修改条目或模块参数后重开世界／重启服务端；tag 遵循原版重载。服务端重载标签时，现有临时报价全部失效。

## 分解规则

注册表：`system:salvage_rule`。条目路径：`data/<namespace>/system/salvage_rule/<path>.json`；tag 路径：`data/<namespace>/tags/system/salvage_rule/<path>.json`。

例如 `data/example/system/salvage_rule/iron_sword.json`：

```json
{
  "label": "example.salvage.iron_sword",
  "priority": 100,
  "input": { "items": ["minecraft:iron_sword"] },
  "costs": [],
  "outputs": [
    {
      "content": {
        "type": "system:item",
        "item": { "id": "minecraft:iron_nugget" }
      },
      "amount": "floor(9 * salvage_durability)"
    }
  ]
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 文本组件 | 必填 | 字符串按翻译键解析 |
| `input` | 原版 ItemPredicate | 必填 | 匹配实际 ItemStack，支持原版物品 tag 和组件谓词 |
| `priority` | int | `0` | 越大越优先；相同优先级按完整规则 ID 升序 |
| `outputs` | 数组，1～36 项 | 必填 | 一条规则的全部确定产物 |
| `outputs[].content` | SpaceContent | 必填 | 复用 `system:item`、`system:currency`、`system:experience`、`system:action` 及扩展类型 |
| `outputs[].amount` | Saturn NumberProvider | `1` | 常量、公式、上下文变量、求和或显式注册的确定性扩展 |
| `costs` | Saturn Cost 数组，0～36 项 | `[]` | 消耗装备之外的额外费用，不叠加工坊的维修费用 |
| `enabled` | boolean | `true` | 是否参与匹配 |
| `display_condition` | Saturn EntityCondition | 恒真 | 是否可见、参与候选 |
| `condition` | Saturn EntityCondition | 恒真 | 是否允许执行 |

先筛选当前模块允许且可见的候选，再选择唯一最高优先级规则。该规则的执行条件或费用不满足时直接拒绝，**不会回退到低优先级规则**。

物品模板 `content.item.count` 必须为 1，实际数量只由 `amount` 定义。固定返材使用数字常量，例如 `"amount": 9`。数量在报价时求值并冻结；负数、非有限数、超出 long 范围、无效变量或无效产物会拒绝报价。0 项省略，相同内容合并并检查求和溢出，全部为 0 时不销毁物品。Action 的数量是兑现时的执行次数，不在销毁装备时执行。

## 数量上下文

这些变量只在当前报价内提供，不修改玩家的全局属性。

| 变量 | 值 |
| --- | --- |
| `salvage_durability` | 剩余耐久比例，0～1；无耐久为 1 |
| `salvage_damage` | 已损耗耐久；无耐久为 0 |
| `salvage_max_damage` | 最大耐久；无耐久为 0 |
| `salvage_enchantment_count` | 原生附魔条目数 |
| `salvage_enchantment_levels` | 原生附魔等级总和，包含诅咒；未经价值加权 |
| `salvage_value` | Evaluate 对实际单件 ItemStack 的价值；未知时不可读取 |

例如把价值的 25% 按每 8 点兑换一枚系统币：

```json
{
  "content": { "type": "system:currency", "currency": "system:coin" },
  "amount": "floor(salvage_value * 0.25 / 8)"
}
```

只有使用价值变量或扩展求值器时才查询 Evaluate。未知价值不按 0 处理；固定返材和只读耐久的公式仍可使用。系统不自动再乘一次耐久，避免估值方已考虑耐久时重复折损；P90 不限制玩家分解自己持有的装备。

内置允许 `Constant`、`Expression`、`ContextVariable` 和 `Sum`。表达式支持 exp4j 的确定性数学函数及 Saturn 的 `min`、`max`、`clamp`、`round`，可用 `params` 嵌套上述提供器，最大嵌套 32 层。内置变量提供器不会对缺失的 `salvage_value` 使用 fallback；其他全局变量和随机提供器被拒绝。严格求值会保留异常，不使用 Saturn 普通场景中“公式错误返回 0”的容错行为。

第三方 NumberProvider 先正常登记 Saturn Codec，再在模组初始化阶段调用：

```java
SalvageNumbers.register(MyNumber.CODEC, MyNumber.class,
        (provider, context) -> provider.evaluateStrictly(context));
```

`evaluateStrictly` 是扩展实现自己的方法。求值器必须确定、无副作用，在缺少上下文时抛出异常；不能读随机数或吞掉错误。相同 Codec 只能登记一次。新增奖励种类仍扩展 SpaceContent，不需要另建“固定／价值”策略注册表。

## 目标保护与订单

报价两分钟有效，新报价替换旧报价。服务端核对绑定代次、原连接、所属模块、规则、主手栏位、ItemStack 对象与完整组件快照。切槽、更换物品或组件、禁用模块、断线和重载后需重新报价。额外物品费用的 Container 排除目标栏位。

非空原版容器、收纳袋、装填中的弩默认拒绝分解。扩展可监听服务端 `SalvageCheckEvent`，通过 `reject(Failure)` 拒绝绑定装备、内部库存或任务物品。事件在报价和确认时触发，`input()` 每次返回副本；监听器不得修改背包或产生效果。

订单归 `WorkshopDataStorage`，与玩家业务数据保存在存档 `system/players/<UUID>.dat`。临时报价不写盘；确认时保存订单和冻结产物，并先预留空间账本容量。

| 状态 | 恢复语义 |
| --- | --- |
| `prepared` | 已保存条款与占位，未允许游戏副作用；重新登录恢复时安全关闭 |
| `executing` | 已持久化执行意图；重启后转 `unknown` |
| `completed` | 扣除成功，产物与成功状态已在同一次玩家事务保存 |
| `failed` | 已确认未产生副作用的失败；释放占位 |
| `unknown` | 部分付款或消耗结果不确定；锁定产物等待核对 |

订单 ID 等于报价 ID，空间权益为 `salvage:<订单ID>`。重复确认不重复扣款或交付；付款后仍复核原装备再移除。成功结算写入 `system:salvaged` 可信事实，source 为规则 ID，subject 为物品 ID，occurrence 为订单 ID，供事实奖励使用。人工核对仅处理原冻结权益，不重跑奖励条件。

不可逆操作后的结算独立于页面和在线连接。正常进程内落盘失败保留执行回执，间隔五秒重试结算，不再扣装备；撤销并重新绑定后旧句柄无法写入新账本。MC 背包与 System NBT 是两份存储，不保证跨文件的崩溃原子性；重启后的不确定订单不能根据背包里是否有相同物品自动推断结果。

分解记录通过系统界面 `/salvage_history`、`/分解记录` 查看，每页 10 条。首版保留最多 4096 个订单 ID，不自动删除未结清记录；达到订单或系统空间容量上限时拒绝新确认。

## 管理员核对

这些是管理指令，分解业务本身不注册到游戏 `/system`：

```text
/system workshop list <玩家> [offset]
/system workshop show <玩家> <订单ID>
/system workshop resolve <玩家> <订单ID> consumed <原因>
/system workshop resolve <玩家> <订单ID> not_consumed <原因>
```

`consumed` 解锁原冻结产物，`not_consumed` 关闭订单、不发产物。两者都不再次扣物品或自动退费用，保存核对人与原因，并打印审计日志。只允许处理没有运行中执行器的未知订单；产物已经开放、入库或领取的订单不能改判。操作目标沿用原版在线玩家参数。

本轮执行编译与资源静态校验，没有启动 Minecraft；目标变化、重复点击、费用部分失败、断线、磁盘失败和管理员核对仍需游戏内验收。
