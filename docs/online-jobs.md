# 在线作业

模块类型、内置配置与客户端实现 ID 均为 `system:online_jobs`，已加入默认模块 tag。系统对话框输入 `/jobs`、`/作业`，或点击“在线作业”进入。选择项目后最多进行一项；完成后手动领取产物到收件箱，再用既有领取按钮放入背包。领取入账后可开始下一项；旧产物仍可在收件箱领取。

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
  "duration": 60,
  "items": [{ "id": "minecraft:oak_log", "count": 4 }],
  "enabled": true,
  "order": 30
}
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `label` | 翻译键字符串或 Component | 必填 | 名称，字符串直接作为翻译键 |
| `description` | 翻译键字符串或 Component | 空文本 | 简要说明，字符串直接作为翻译键 |
| `duration` | 正整数 | 必填 | 在线秒数，转换后必须能用 long 表示 tick |
| `items` | ItemStackTemplate 数组 | 必填 | 1～36 个物品模板，复用 Reward 的背包容量约束 |
| `enabled` | boolean | `true` | 是否可开始新作业 |
| `order` | int | `0` | 列表优先级，越高越靠前，同值按 ID 排序 |

作业 tag 路径为 `data/<namespace>/tags/system/online_job/<path>.json`，格式与原版 tag 相同。内置 `#system:default` 包含林地采集（60 秒，4 橡木原木）、石料采集（60 秒，8 圆石）和木炭生产（120 秒，2 木炭）。这些是单纯计时产物，不需要实际开采或输入材料；更复杂的生产规则留待后续扩展。

名称和描述的翻译键需要客户端资源包提供双语语言文件。修改注册表定义后重新进入世界/正常重启服务器；已开始作业保存定义快照，不受后续删表或修改产物影响。

## 存储与领取

`OnlineJobsDataStorage` 位于 `module.builtin.storage`，通过 Saturn DataStorage 保存在已有的玩家压缩 NBT 文件中，owner/type 均为 `system:online_jobs`。只保存一条当前作业：随机运行 ID、定义快照、累计 tick、是否已入账。每次状态变更调用 markDirty；计时写缓存，由 ModuleManager 合并后异步保存，不逐 tick 写文件。它遵守现有玩家业务重置/revoke 语义。

领取在同一玩家存储事务中创建唯一 `job:<运行ID>` 的收件箱记录并标记已入账。重复点击不重复发产物，旧运行按钮无法领取新作业。领取要求收件箱的 deliver 能力可用；背包不足等情况由现有收件箱交付流程处理，不损失已入账产物。

| 动作 | 工具 | 用途 |
| --- | --- | --- |
| `system:online_jobs` | `jobs.query` | 查询列表和当前状态；提供给只读推理工具 |
| `system:job_start` | `jobs.start` | 开始指定作业，系统对话框也可 `/work <ID>` / `/开工 <ID>` |
| `system:job_claim` | `jobs.claim` | 手动入账，按钮带服务端生成的运行 ID |
| `system:job_cancel` | `jobs.cancel` | 取消未完成作业，按钮带运行 ID |

领取和取消采用 structuredOnly，不由自然语言自动路由或模型擅自执行。客户端模板为 `assets/system/cards/online_jobs.json`，列表每批最多读取 18 项，批内复用通用 grid 的本地分页。
