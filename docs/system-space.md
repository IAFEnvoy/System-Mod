# 系统空间

系统空间按玩家保存内容，独立于对话、签到与商城。当前内置物品、系统币、原版经验、Saturn action 和多选一奖励类型，配置见[奖励与非物品结果](rewards.md)；内容通过 `SpaceContent` / `SpaceContentType<T>` 扩展，后续可以接入实体、灵兽等类型，不需要改动空间账本、列表或取出动作。

## 使用

- 在系统界面点击“系统空间”，或输入 `/space`、`系统空间`。
- 点击条目取出 1 份；按住 Shift 点击取出一批。物品使用堆叠上限，系统币为 64、经验点为 100、等级 / action 为 1；数量不足一批时取出剩余数量。
- 输入或语音说“取出铁锭”“取出十个铁锭”“从系统空间取出一组铁锭”。英文可使用 `withdraw 10 minecraft:iron_ingot`、`take a stack of minecraft:iron_ingot`。
- 卡片按共享网格分页，只创建当前页的控件；取出后原地更新数量。简约模式也可通过带编号的选项取出。
- 点击“存入物品”打开服务端背包列表；点击存入一个，Shift 点击存入一组。`/space_inventory` / `/存入物品` 可直接打开列表，`/deposit`、`/deposit 10`、`/deposit stack` 分别存入主手一个、指定数量或一组。
- 输入或语音说“存入手持物品”“存入十个手持物品”“存入一组手持物品到系统空间”，可直接操作主手。自由聊天由 AI 选择存入能力时只返回列表，仍由玩家点击确认。
- 待选择奖励点击后先预览再确认，见[多选一奖励](reward-choices.md)。

离线、标准和扩展模式优先解析明确的取出请求，无需模型。自由模式中的普通对话由 Agent 调用查询 / 取出工具；以 `/` 开头的明确指令仍直接执行。语音识别本身是否调用外部服务取决于 ASR 配置。

客户端先用最新空间快照精确匹配本地化名称／ID；其余通过原版 `SearchTree<ItemStack>` 检索 tooltip、名称、ID 或标签。已有快照时，搜索树收录空间中的物品并保留改名、附魔等组件；还没打开空间时使用创造搜索集合。存入成功或确认自选奖励后，会立即追加本次产物的完整物品模板作为搜索候选，同时使旧条目 ID 缓存失效；首次完整查询前将这些候选与创造搜索集合合并，不会因为只收到部分更新而漏掉普通物品。历史回执可能留下已取走的候选，下一次完整查询会替换它们。客户端只发送有界物品候选，不发送可信数量或账本内容。服务端始终从当前玩家账本查询并精确比较组件，遇到多个候选或客户端尚有未读匹配项时返回列表供选择，不随意选一个取出。详见[物品检索](item-search.md)。

相同内容按完整组件合并显示。附魔、名称等组件不同的物品保留为不同条目。未知交付状态显示为不可取出，不与可取出数量合并。

## 存储与奖励来源

`SpaceDataStorage` 位于 `module.builtin.storage`，通过 `SpaceSystemModule.STORAGE` 访问；内容 Codec 注册在 Saturn DataStorage 注册表。为了继续读取现有奖励记录，模块、动作、客户端实现和存储的原有 ID `system:inbox` 保留；界面、代码类名和新工具使用 Space 名称。旧 `/inbox` / “收件箱”仍作为入口别名。

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `entries` | ID → `SpaceEntry` | 任意内容类型的持久化条目 |
| `deliveries` | ID → `Delivery` | 签到、商城、抽奖等来源的交付记录 |
| `choices` | 票据 → `ChoiceReceipt` | 永久选择快照及已选选项，旧按钮重试不重复发放 |
| `deposits` | ID → `SpaceDeposit` | 主动存入的扣除、预留、结算和核对回执 |

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

数量属于账本，物品模板归一为 1 个且完整保留组件；不会把大数量塞进一个原版 ItemStack。空间最多使用 4096 格，未支付和支付未知订单的奖励、主动存入事务及待选择奖励的最坏展开大小都预留对应容量。

已有奖励通过统一的 `putDelivery` / `putDeliveryIfAbsent` / `setDeliveryState` 接入：只有 `PENDING` 的已结算权益才生成内容，未支付与支付状态未知的商品不会提前变成可取出条目。旧存档的待领取奖励在登录恢复时入库一次，`Delivery.stored` 防止重复转换。

来源记录保留到内容被取完，“领取奖励”按钮只领取该来源剩余的内容。已从空间取走的部分不会再次发放；全部取完后来源标记为 `DELIVERED`。未结算订单仍可在打开空间时查看摘要。签到状态改由签到模块的 `/signin_status` / “查看签到状态”独立查询。

签到主记录最多保留 32 条近期快照，较早的完整奖励单独归档。未领完或状态未知的交付记录及空间内容继续保留；已全部取完的重复收据可在签到维护时清理。聊天历史中的领取按钮仍会核对归档，已经领取完毕时直接返回已领取，不会重新发放或因收据清理而报失效。

## 扩展存入

在服务端存储线程的 `AgentStore.update` 事务中调用：

```java
context.store().update(context.player(), data -> {
    SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
    space.deposit(UUID.randomUUID().toString(), new ItemSpaceContent(template), 128);
    return null;
});
```

`deposit` 不创建奖励包、不修改 MC 背包，扩展应先完成来源校验与自身结算。传入 ID 必须是新条目 ID；此接口不提供永久幂等凭证，条目取完后会删除。需要业务幂等时，用自己的持久账本或原有奖励交付记录维护唯一性。玩家从 MC 背包主动存入应使用下述独立事务，不直接调用这个底层入口。

## 新内容类型

内容实现必须是不可变值，提供正确的 `equals` / `hashCode`；它们决定哪些条目可合并。实现 `SpaceContent` 并返回自己的 `SpaceContentType<T>`，再通过 `DeferredRegister` 注册到 `SystemResourceKeys.SPACE_CONTENT_TYPE`。公开内置入口为 `SystemSpaceContentTypes.ITEM`、`CURRENCY`、`EXPERIENCE`、`ACTION`、`CHOICE`，类型定义两端都需存在，不要引用客户端专用类。

| `SpaceContentType<T>` 方法 | 用途 |
| --- | --- |
| `codec()` | 该内容的 MapCodec，保存自定义数据 |
| `name(content)` | 可翻译名称 Component |
| `icon(content)` | 通用 IconReference |
| `searchId(content)` | 可输入或由工具使用的稳定内容标识 |
| `batchSize(content)` | Shift 取出的一批数量；实体通常为 1 |
| `capacity(content, amount)` | 纯元数据：预留展开所需格数，默认 1；超过 4096 表示装不下 |
| `prepareReward(content, amount, transfer)` | 付款前校验；默认沿用 prepare，choice 分别校验所有候选 |
| `prepare(content, amount, transfer)` | 校验并暂存取出效果；此时不得修改世界 |

展示方法必须是纯元数据读取，允许在两端和不同线程调用，不能访问玩家或世界；准备方法只在服务端游戏线程调用且不得立即执行效果。物品类型调用 `SpaceTransfer.item(stack)`；其他类型可通过 `transfer.player()` 检查玩家、世界和生成位置，再用 `transfer.effect(Runnable)` 暂存实际操作。例如实体类型可以保存完整实体数据并在取出时校验位置，但实体捕获、生成和相关权限尚未实现。

## 取出事务

1. 在玩家账本中选出可用内容，将参与条目标记为 `TAKING` 并持久化预留数量。
2. 在游戏线程准备效果，模拟主背包已有堆叠与空位。全部可放入时才应用同一份槽位计划，不会因创意模式吞物品，也不把溢出物品丢到地上。
3. 在存储线程把系统币余额增加和空间数量扣除一起提交，清除空条目并更新关联奖励状态。钱包提前在独立草稿中预检，余额溢出不会先执行游戏效果。

准备或容量检查失败且尚未修改世界时恢复 `AVAILABLE`，不扣除数量。开始应用效果后发生异常则标记 `UNKNOWN`；重登时遗留 `TAKING` 也转为 `UNKNOWN`，防止自动重复发放。游戏玩家存档与空间文件不是同一个事务，崩溃或单独回档仍可能需要核对，不保证跨文件严格一次执行。

空间查询 / 取出工具分别为 `space.query`、`space.take`，取出动作是 `system:space_take`，新卡片类型与资源模板均为 `system:space`。按钮仅携带条目 ID、数量或 stack 标记；来源卡片与模块权限仍由通用动作链校验。

## 玩家主动存入

模块 `server` 配置：

```json
{
  "type": "system:inbox",
  "deposit_slots": 256
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `deposit_slots` | int，0～1024 | `256` | 每玩家可占用的主动存入格数；0 关闭新增存入，不影响已有内容取出 |

每次存入一个背包槽位的部分或全部物品，生成一个存储条目；同物品完整组件相同时界面合并显示，但存入格数按尚未取空的实际条目计算。未结束或待核对存入也占格数。此额度只约束主动存入，签到等已获得奖励不受它限制；两者共用总容量 4096 和既有玩家文件体积限制。调低容量不会删除物品或锁定取出。首版只接主背包和快捷栏，不读取装备、副手或外部容器。

服务端下发最多 36 个有界槽位快照及两分钟引用；客户端仅回传引用、数量或 stack 标记，不上传物品模板。提交时检查引用所属玩家、模块配置、会话、有效期、槽位的对象身份、数量及完整组件，持久保存容量预留和 EXECUTING 后，在游戏线程重新核对并扣除同一槽位。任何中途移动、使用、改名或数量变化都会使快照失效，刷新列表后再试。明确的主手请求不依赖客户端缓存，提交前也检查选中的快捷栏槽位。

库存扣除不丢到地上、不经过创造模式发放行为；完整保留附魔、改名、容器等组件。成功后的空间入库与 COMPLETED 标记共用一个存储事务。存储 I/O 全部在工作线程，游戏线程只读取 / 扣除背包和同步容器。

| 状态 | 含义与恢复 |
| --- | --- |
| `prepared` | 已预留、尚未开始扣除；进程恢复时标为 failed 并释放容量 |
| `executing` | 已允许扣除；进程恢复时转 unknown，不自动重扣或补发 |
| `completed` | 已入库；旧引用重试返回原回执，已取走的物品不会再入库 |
| `failed` | 确认未修改背包，释放预留；刷新后可重新操作 |
| `unknown` | 可能已扣除但结果不确定，保留预留并等待核对 |

进程内存储失败会保留结算快照并每 5 秒重试，只重试保存，不重复扣背包。终态回执保留最近约 128 条，未结束 / unknown 不清理；已用引用从内存移除，旧终态被清理后引用失效，不能生成第二笔存入。取消或撤销玩家绑定不会由旧回调重建已删除账本。

管理员管理命令（原版命令树，仅管理权限）：

- `/system space list <player> [offset]`：每次查看最多 20 条存入记录，包含原槽位、物品、数量、状态与核对信息。
- `/system space resolve <player> <id> consumed <reason>`：核实已从背包扣除，只把冻结的原物品入库，不再次扣除。
- `/system space resolve <player> <id> not_consumed <reason>`：核实未扣除，释放预留，不退款、不生成物品。

核对只处理 unknown，正在由本进程结算的事务不能被人工同时改写；核对人和原因保存并打印日志。Minecraft 玩家数据与 System 文件不是同一事务，崩溃或独立回档仍需按实际背包核对，不能保证跨文件严格一次执行。

玩家的 `/space`、`/space_inventory`、`/deposit` 只在系统界面输入，不注册到 `/system` 下。新增动作是 `system:space_inventory` / `system:space_deposit`，工具是 `space.inventory` / `space.deposit`，列表卡片与模板均为 `system:space_inventory`。
