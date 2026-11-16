# 系统空间

系统空间按玩家保存内容，独立于对话、签到与商城。当前内置物品、系统币、原版经验和 Saturn action 类型，配置见[奖励与非物品结果](rewards.md)；内容通过 `SpaceContent` / `SpaceContentType<T>` 扩展，后续可以接入实体、灵兽等类型，不需要改动空间账本、列表或取出动作。

## 使用

- 在系统界面点击“系统空间”，或输入 `/space`、`系统空间`。
- 点击条目取出 1 份；按住 Shift 点击取出一批。物品使用堆叠上限，系统币为 64、经验点为 100、等级 / action 为 1；数量不足一批时取出剩余数量。
- 输入或语音说“取出铁锭”“取出十个铁锭”“从系统空间取出一组铁锭”。英文可使用 `withdraw 10 minecraft:iron_ingot`、`take a stack of minecraft:iron_ingot`。
- 卡片按共享网格分页，只创建当前页的控件；取出后原地更新数量。简约模式也可通过带编号的选项取出。

离线、标准和扩展模式优先解析明确的取出请求，无需模型。自由模式中的普通对话由 Agent 调用查询 / 取出工具；以 `/` 开头的明确指令仍直接执行。语音识别本身是否调用外部服务取决于 ASR 配置。

客户端使用最新空间快照匹配本地化名称；还没打开空间时，也可把物品的本地名称转换为注册 ID。它只发送选择条件，不发送可信数量或物品数据。服务端始终从当前玩家账本查询，遇到多个同名但组件不同的条目时返回列表供选择，不随意选一个取出。

相同内容按完整组件合并显示。附魔、名称等组件不同的物品保留为不同条目。未知交付状态显示为不可取出，不与可取出数量合并。

## 存储与奖励来源

`SpaceDataStorage` 位于 `module.builtin.storage`，通过 `SpaceSystemModule.STORAGE` 访问；内容 Codec 注册在 Saturn DataStorage 注册表。为了继续读取现有奖励记录，模块、动作、客户端实现和存储的原有 ID `system:inbox` 保留；界面、代码类名和新工具使用 Space 名称。旧 `/inbox` / “收件箱”仍作为入口别名。

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `entries` | ID → `SpaceEntry` | 任意内容类型的持久化条目 |
| `deliveries` | ID → `Delivery` | 签到、商城、抽奖等来源的交付记录 |

每个 `SpaceEntry` 保存 `id`、`content`、正整数 `amount`、可选 `entitlement` 和 `state`。`content.type` 从正式注册表 `system:space_content_type` 分派，其余字段由内容类型的 MapCodec 定义。内置物品条目示例：

```json
{
  "id": "example-entry",
  "content": {
    "type": "system:item",
    "item": { "id": "minecraft:iron_ingot" }
  },
  "amount": 128,
  "state": "available"
}
```

数量属于账本，物品模板归一为 1 个且完整保留组件；不会把大数量塞进一个原版 ItemStack。空间最多保存 4096 个未清空条目，未支付和支付未知订单的奖励也预留对应容量。

已有奖励通过统一的 `putDelivery` / `putDeliveryIfAbsent` / `setDeliveryState` 接入：只有 `PENDING` 的已结算权益才生成内容，未支付与支付状态未知的商品不会提前变成可取出条目。旧存档的待领取奖励在登录恢复时入库一次，`Delivery.stored` 防止重复转换。

来源记录保留到内容被取完，“领取奖励”按钮只领取该来源剩余的内容。已从空间取走的部分不会再次发放；全部取完后来源标记为 `DELIVERED`。未结算订单仍可在打开空间时查看摘要。签到状态改由签到模块的 `/signin_status` / “查看签到状态”独立查询。

## 扩展存入

在服务端存储线程的 `AgentStore.update` 事务中调用：

```java
context.store().update(context.player(), data -> {
    SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
    space.deposit(UUID.randomUUID().toString(), new ItemSpaceContent(template), 128);
    return null;
});
```

`deposit` 不创建奖励包、不修改 MC 背包，扩展应先完成来源校验与自身结算。传入 ID 必须是新条目 ID；此接口不提供永久幂等凭证，条目取完后会删除。需要业务幂等时，用自己的持久账本或原有奖励交付记录维护唯一性。当前没有提供从 MC 背包主动存入的界面。

## 新内容类型

内容实现必须是不可变值，提供正确的 `equals` / `hashCode`；它们决定哪些条目可合并。实现 `SpaceContent` 并返回自己的 `SpaceContentType<T>`，再通过 `DeferredRegister` 注册到 `SystemResourceKeys.SPACE_CONTENT_TYPE`。公开内置入口为 `SystemSpaceContentTypes.ITEM`、`CURRENCY`、`EXPERIENCE`、`ACTION`，类型定义两端都需存在，不要引用客户端专用类。

| `SpaceContentType<T>` 方法 | 用途 |
| --- | --- |
| `codec()` | 该内容的 MapCodec，保存自定义数据 |
| `name(content)` | 可翻译名称 Component |
| `icon(content)` | 通用 IconReference |
| `searchId(content)` | 可输入或由工具使用的稳定内容标识 |
| `batchSize(content)` | Shift 取出的一批数量；实体通常为 1 |
| `prepare(content, amount, transfer)` | 校验并暂存取出效果；此时不得修改世界 |

展示方法必须是纯元数据读取，允许在两端和不同线程调用，不能访问玩家或世界；准备方法只在服务端游戏线程调用且不得立即执行效果。物品类型调用 `SpaceTransfer.item(stack)`；其他类型可通过 `transfer.player()` 检查玩家、世界和生成位置，再用 `transfer.effect(Runnable)` 暂存实际操作。例如实体类型可以保存完整实体数据并在取出时校验位置，但实体捕获、生成和相关权限尚未实现。

## 取出事务

1. 在玩家账本中选出可用内容，将参与条目标记为 `TAKING` 并持久化预留数量。
2. 在游戏线程准备效果，模拟主背包已有堆叠与空位。全部可放入时才应用同一份槽位计划，不会因创意模式吞物品，也不把溢出物品丢到地上。
3. 在存储线程把系统币余额增加和空间数量扣除一起提交，清除空条目并更新关联奖励状态。钱包提前在独立草稿中预检，余额溢出不会先执行游戏效果。

准备或容量检查失败且尚未修改世界时恢复 `AVAILABLE`，不扣除数量。开始应用效果后发生异常则标记 `UNKNOWN`；重登时遗留 `TAKING` 也转为 `UNKNOWN`，防止自动重复发放。游戏玩家存档与空间文件不是同一个事务，崩溃或单独回档仍可能需要核对，不保证跨文件严格一次执行。

空间查询 / 取出工具分别为 `space.query`、`space.take`，取出动作是 `system:space_take`，新卡片类型与资源模板均为 `system:space`。按钮仅携带条目 ID、数量或 stack 标记；来源卡片与模块权限仍由通用动作链校验。
