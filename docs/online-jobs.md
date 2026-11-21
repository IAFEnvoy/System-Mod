# 在线作业与队列

模块类型、内置配置与客户端实现 ID 均为 `system:online_jobs`，已加入默认模块 tag。发布内置配置使用 2 格队列，Codec 省略 queue_size 时仍为 4。系统对话框输入 `/jobs`、`/作业`，或点击“在线作业”进入。选择项目后先展示材料／费用报价，确认后扣除投入并入队；队列按入队顺序依次运行，完成的每项产物独立领取到系统空间。

下方操作区使用 `system:module_controls` JSON 卡片：左侧为标题与作业说明，右侧为“刷新作业”。它是本地工具栏，不增加聊天记录或通知。正文分别显示队列和可选项目，两者复用通用 grid 本地分页，只创建当前页控件。排队时仍可选择其他项目，队满时禁用新入队按钮。

## 模块定义

路径：`data/<namespace>/system/system_module/<path>.json`。

```json
{
  "order": 40,
  "server": {
    "type": "system:online_jobs",
    "jobs": "#system:default",
    "queue_size": 2
  },
  "client": "system:online_jobs"
}
```

| server 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | Identifier | 必填 | `system:online_jobs` |
| `jobs` | ID、`#tag` 或混合数组 | 全表 | 允许选择的作业；`[]` 表示无可选作业 |
| `queue_size` | int，1～32 | `4` | 所有未领取作业的容量，包含排队、运行、待领取和付款待核对记录 |

同类模块变体按已有工作流规则选择玩家启用的首个配置，其 `jobs` 和 `queue_size` 决定本次可选项目与入队上限；建议变体条件互斥。所有变体共用一份玩家队列。已有队列超出新配置容量时不删除作业，但不能继续入队，直至腾出容量。任一同类模块启用时继续已有队列，多个变体同时启用不会加速计时或产生并行作业。

## 队列行为

- 每次在线模块 tick 只推进最早的一项未完成作业；前方完成后，下一次 tick 自动开始下一项。
- 已完成但未领取的作业仍占用容量，**不阻塞后面的正常作业**。可以独立领取任意一项，入账后释放名额。
- 离线或所有同类模块禁用时暂停；重新登录／启用继续原有进度，不按离线墙钟补发收益。
- 计时按实际成功分发的 tick 推进，20 tick 为一秒；服务器卡顿时推进速度也会变慢。完成后停在待领取状态，不自动重复生产。
- 排队和运行中的未完成作业均可取消，放弃该项且不退款。已完成项目只能领取，付款待核对项目必须由管理员处理。
- 付款状态 `PREPARED`／`UNKNOWN` 会阻塞其后的作业；排在它前面的正常作业仍可继续。核对为已支付后回到原位排队，不额外开一个并行作业。

卡片显示查询时的快照，点击“刷新作业”更新状态和进度；无需保持界面打开。每次确认入队和领取在服务端事务内复核容量及运行 ID，旧按钮不能绕过限制或操作新作业。

## 作业定义

原生服务端数据包注册表：`system:online_job`。路径：`data/<namespace>/system/online_job/<path>.json`，条目 ID 为 `<namespace>:<path>`。不向客户端同步完整注册表，只发送玩家所需的分页卡片快照。

```json
{
  "label": "example.job.wood",
  "description": "example.job.wood.description",
  "duration": 120,
  "costs": [{ "type": "saturn:item", "items": "#minecraft:logs", "amount": 2 }],
  "items": [{ "id": "minecraft:charcoal", "count": 2 }],
  "enabled": true,
  "order": 30
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 翻译键字符串或 Component | 必填 | 名称，字符串直接作为翻译键 |
| `description` | 翻译键字符串或 Component | 空文本 | 简要说明，字符串直接作为翻译键 |
| `duration` | 正整数 | 必填 | 在线秒数，转换后必须能用 long 表示 tick |
| `costs` | Saturn Cost 数组 | `[]` | 确认入队时一次性投入；物品、系统币等通用费用，先报价再确认 |
| `items` | ItemStackTemplate 数组 | `[]` | 0～36 个物品模板 |
| `contents` | SpaceStack 数组 | `[]` | 0～36 项系统币、经验或 Saturn action；见[奖励与非物品结果](rewards.md) |
| `enabled` | boolean | `true` | 是否可新增该项目 |
| `order` | int | `0` | 可选列表优先级，越高越靠前，同值按 ID 排序；不改变已付费队列顺序 |
| `display_condition` | Saturn EntityCondition 对象或数组 | 始终成立 | 不满足时隐藏，也不能直接按 ID 新增 |
| `condition` | Saturn EntityCondition 对象或数组 | 始终成立 | 不满足时禁用新增按钮；服务端确认入队前复核 |

详见[条目条件](catalog-conditions.md)。条件只影响新增作业；已确认作业保存定义和投入快照，开始下一项时不重新扣费、不重新求值条件。指定位置持续生产等运行条件尚未接入。

作业 tag 路径为 `data/<namespace>/tags/system/online_job/<path>.json`，格式与原版 tag 相同。发布内置 `#system:default` 包含木炭生产（3 原木 → 2 木炭，120 秒）、面包制作（6 小麦 → 2 面包，60 秒）和铁锭冶炼（4 粗铁 + 1 煤炭 → 4 铁锭，80 秒；需原版“来硬的”进度）。免费原木／圆石采集已移到 test-mod，正式加工均有投入；不模拟实际开采或熔炉，完整基线见[发布默认数据](release-data.md)。作业等级及离线收益尚未接入。

名称和描述的翻译键需要客户端资源包提供双语语言文件。修改定义后重开世界／正常重启服务器；已有队列保留原定义快照，不受后续删表或修改产物影响。

## 存储与领取

`OnlineJobsDataStorage` 位于 `module.builtin.storage`，通过 Saturn DataStorage 保存在已有的玩家压缩 NBT 文件中，owner/type 均为 `system:online_jobs`。`runs` 按入队顺序保存随机运行 ID、展示用定义快照、累计 tick、是否已入账、付款状态和费用快照；可执行费用与条件不进入快照或客户端卡片。保留旧的单项 `run` 字段读取，后续只写新队列；旧作业缺失状态时按 RUNNING 读取，不重新扣费。

每次状态变更调用 markDirty；计时写缓存，由 ModuleManager 合并后异步保存，不逐 tick 写文件。作业遵守现有玩家业务重置／revoke 语义。

领取在同一玩家存储事务内创建唯一 `job:<运行ID>` 的空间权益并标记已入账。重复点击返回原权益，不重复发产物；队列只保留有界的已领取回执，淘汰后仍可从系统空间权益返回原领取结果。领取要求 deliver 能力可用；空间容量不足时作业保留为待领取，背包不足不影响产物先入账到空间。

| 动作 | 工具 | 用途 |
| --- | --- | --- |
| `system:online_jobs` | `jobs.query` | 查询项目列表及队列快照 |
| `system:job_start` | `jobs.start` | 报价并确认入队；界面内可 `/work <ID>` / `/开工 <ID>` |
| `system:job_claim` | `jobs.claim` | 独立领取，按钮带服务端运行 ID |
| `system:job_cancel` | `jobs.cancel` | 取消未完成项目，按钮带运行 ID，不退款 |

领取和取消采用 structuredOnly，不由自然语言自动路由或模型擅自执行。客户端模板为 `assets/system/cards/online_jobs.json`，可选项目每批最多 18 项，批内与队列均复用通用 grid 本地分页。

## 材料投入与报价

所有项目（包括免费项目）先报价再由玩家确认；报价两分钟有效、仅属于发起玩家且只能使用一次。`/work <ID>` 或 AI 工具只生成报价，不能代替玩家确认。列表与入队使用相同 `CostContext`：来源 `system:online_jobs`，`system:source` 为项目 ID。数量公式在报价时求值一次，确认时重检模块、容量、条件和支付能力。

先持久保存 PREPARED 占位，再在游戏线程支付冻结账单，成功后转 QUEUED。在线 tick 将队首转 RUNNING；付款不发生在自动开始时。无法确认扣费是否发生时转 UNKNOWN；崩溃遗留的 PREPARED 在重新登录时转 UNKNOWN，不自动重扣。取消排队／运行项目均**不退还材料或费用**，已有产物只能领取。

## 异常作业／传送核对

以下为管理员维护命令，可在游戏聊天或控制台使用，需要管理员命令权限，模型不可调用。正常玩法指令仍只在系统界面内使用。

| 命令 | 行为 |
| --- | --- |
| `/system activity show <玩家>` | 列出所有未领取作业及最近传送的 ID、状态、名称和费用快照 |
| `/system activity job_paid <玩家> <运行ID> <原因>` | 核对 UNKNOWN 费用已支付，恢复到原位排队，不再扣款 |
| `/system activity job_unpaid <玩家> <运行ID> <原因>` | 核对 UNKNOWN 未支付，移除该项，不退款 |
| `/system activity close_travel <玩家> <传送ID> <原因>` | 人工结案 UNKNOWN 传送，不重复传送或自动退款 |

恢复只接受匹配的 UNKNOWN 记录，操作与原因写入日志。存档写入失败时先恢复文件可写并重新登录，再处理遗留状态。玩家 NBT 与背包／钱包不保证跨文件原子提交，无法确认效果时必须核对，不用自动退款或重试猜测结果。
