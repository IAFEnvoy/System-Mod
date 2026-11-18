# 在线作业

模块类型、内置配置与客户端实现 ID 均为 `system:online_jobs`，已加入默认模块 tag。系统对话框输入 `/jobs`、`/作业`，或点击“在线作业”进入。选择项目后先展示材料／费用报价，确认后最多进行一项；完成后手动领取产物到系统空间，再按份取出或领取整包。物品进入背包，非物品执行对应结果。入账后可开始下一项，旧产物仍可在空间领取。

下方操作区使用 `system:module_controls` JSON 卡片：左侧为标题与作业说明，右侧为“刷新作业”按钮。它是本地工具栏，不增加聊天记录或通知；刷新仍走原有作业查询请求。

计时来自成功分发的在线模块 tick（20 tick 为一秒），不使用离线墙钟时间；服务端卡顿时按实际 tick 速度推进。登出或所有同类模块禁用时暂停，重新登录/启用继续原进度。完成后停在待领取状态，不自动循环。卡片显示查询时的快照，可点击“刷新作业”更新；未完成时可明确取消并放弃进度，完成后只能领取。

## 模块定义

路径：`data/<namespace>/system/system_module/<path>.json`。

```json
{
  "order": 40,
  "server": { "type": "system:online_jobs", "jobs": "#system:default" },
  "client": "system:online_jobs"
}
```

| server 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | Identifier | 必填 | `system:online_jobs` |
| `jobs` | ID、`#tag` 或混合数组 | 全表 | 允许选择的作业；`[]` 表示无可选作业 |

同类模块变体按已有工作流规则选择玩家启用的首个配置，其 jobs 决定本次可选项目；建议变体条件互斥。所有变体共用一份玩家作业账本，任一同类模块启用时继续已有项目，多个变体同时启用不会加速计时或多发奖励。

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
| `costs` | Saturn Cost 数组 | `[]` | 开工一次性投入；物品、系统币等通用费用，先报价再确认 |
| `items` | ItemStackTemplate 数组 | `[]` | 0～36 个物品模板 |
| `contents` | SpaceStack 数组 | `[]` | 0～36 项系统币、经验或 Saturn action；格式见[奖励与非物品结果](rewards.md) |
| `enabled` | boolean | `true` | 是否可开始新作业 |
| `order` | int | `0` | 列表优先级，越高越靠前，同值按 ID 排序 |
| `display_condition` | Saturn EntityCondition 对象或数组 | 始终成立 | 不满足时隐藏该作业，也不能直接按 ID 开工 |
| `condition` | Saturn EntityCondition 对象或数组 | 始终成立 | 不满足时禁用开工按钮；服务端开工前复核 |

详见[条目条件](catalog-conditions.md)。这两个条件只影响开始新作业，不影响已开始项目的计时和领取；指定位置持续生产等运行中条件尚未接入。

作业 tag 路径为 `data/<namespace>/tags/system/online_job/<path>.json`，格式与原版 tag 相同。内置 `#system:default` 包含林地采集（60 秒，4 橡木原木）、石料采集（60 秒，8 圆石）和木炭生产（120 秒，2 木炭）。前两项不需要输入，木炭生产需投入 2 个原木；不模拟实际开采或熔炉。队列、作业等级及离线收益尚未接入。

名称和描述的翻译键需要客户端资源包提供双语语言文件。修改注册表定义后重新进入世界/正常重启服务器；已开始作业保存定义快照，不受后续删表或修改产物影响。

## 存储与领取

`OnlineJobsDataStorage` 位于 `module.builtin.storage`，通过 Saturn DataStorage 保存在已有的玩家压缩 NBT 文件中，owner/type 均为 `system:online_jobs`。只保存一条当前作业：随机运行 ID、展示用定义快照、累计 tick、是否已入账、付款状态与费用快照。可执行费用和条件不进入运行快照或客户端卡片。旧作业缺失付款状态时按 RUNNING 读取，不重新扣费。每次状态变更调用 markDirty；计时写缓存，由 ModuleManager 合并后异步保存，不逐 tick 写文件。它遵守现有玩家业务重置/revoke 语义。

领取在同一玩家存储事务中创建唯一 `job:<运行ID>` 的收件箱记录并标记已入账。重复点击不重复发产物，旧运行按钮无法领取新作业。领取要求收件箱的 deliver 能力可用；背包不足等情况由现有收件箱交付流程处理，不损失已入账产物。

| 动作 | 工具 | 用途 |
| --- | --- | --- |
| `system:online_jobs` | `jobs.query` | 查询列表和当前状态；提供给只读推理工具 |
| `system:job_start` | `jobs.start` | 报价并确认指定作业，系统对话框也可 `/work <ID>` / `/开工 <ID>` |
| `system:job_claim` | `jobs.claim` | 手动入账，按钮带服务端生成的运行 ID |
| `system:job_cancel` | `jobs.cancel` | 取消未完成作业，按钮带运行 ID |

领取和取消采用 structuredOnly，不由自然语言自动路由或模型擅自执行。客户端模板为 `assets/system/cards/online_jobs.json`，列表每批最多读取 18 项，批内复用通用 grid 的本地分页。

## 材料投入与报价

所有项目（包括免费项目）先报价再由玩家点击确认；报价两分钟有效，只属于发起玩家，一次使用后即失效。`/work <ID>` 或 AI 工具只生成报价，不能代替玩家确认。列表费用与开工使用相同 `CostContext`：来源 `system:online_jobs`，`system:source` 为作业 ID；数量公式在报价时求值一次，确认时重新检查模块、条件和支付能力。

先持久保存 PREPARED，再在游戏线程支付冻结的账单，成功后转为 RUNNING 才开始计时。无法确认扣费是否发生时转为 UNKNOWN 并暂停；崩溃遗留的 PREPARED 在重新登录时转为 UNKNOWN，不自动重扣。取消正常运行的作业放弃进度，**不退还材料或费用**。已完成作业只能领取。

## 异常作业／传送核对

以下是管理员维护命令，可以在游戏聊天或控制台使用；需要管理员命令权限，模型不可调用。正常玩法指令仍只在系统界面中使用。

| 命令 | 行为 |
| --- | --- |
| `/system activity show <玩家>` | 显示当前作业与最近传送的 ID、状态、名称和费用快照 |
| `/system activity job_paid <玩家> <运行ID> <原因>` | 核对 UNKNOWN 作业的费用已支付，将原作业继续运行，不再扣款 |
| `/system activity job_unpaid <玩家> <运行ID> <原因>` | 核对 UNKNOWN 作业未支付，清除该次运行，不退款 |
| `/system activity close_travel <玩家> <传送ID> <原因>` | 人工结案 UNKNOWN 传送，解除阻塞，不重复传送或自动退款 |

恢复命令只接受当前匹配的 UNKNOWN 记录，操作及原因写入日志。若存档写入本身失败，应先恢复文件可写并重新登录，处理遗留状态后再操作。玩家 NBT 与背包／钱包不保证跨文件原子提交，无法确认效果时必须核对，不用自动退款或重试猜测结果。
