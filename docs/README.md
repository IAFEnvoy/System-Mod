# System 文档

- [自由聊天、阈值模式与管理员 AI 权限](free-chat.md)
- [自由聊天表情、资源包状态机与说话动作](emotions.md)

当前实现已经切换到新的 Agent 架构。旧的数据包定义、绑定与页面流程已移除；旧版说明不再适用。

| 文档 | 内容 |
| --- | --- |
| [使用与配置](usage.md) | 打开助手、服务端密钥、功能入口、隐私及奖励状态 |
| [统一 API 管理](api-management.md) | Credential 类型注册表、自动识别环境变量、CredentialManager 构造实际服务、多字段鉴权和两端隔离 |
| [DeepSeek TTS / ASR](deepseek-speech.md) | 共享 DeepSeek 凭据、流式 PCM、整段 WAV 识别、字符及音频时长计费 |
| [模型接口与 DeepSeek 兼容](model-api.md) | OpenAI Chat Completions、提供商配置、DeepSeek 思考参数与用量格式 |
| [数据库优先问答与网页搜索](search-api.md) | 当前玩家／共享知识、授权说明书、DeepSeek 免费服务端搜索、独立付费回退与预算 |
| [外部 API 请求清单](external-apis.md) | 模组与独立音色工具访问的接口、客户端/服务端归属、触发条件和发送的数据 |
| [包职责与数据流](architecture.md) | Agent / assistant / data / module / speech 边界、依赖注入、线程与关闭顺序 |
| [开发与架构](development.md) | Agent 包组织、模块与工具接口、预算、持久化、MC 接入和当前限制 |
| [功能模块化](feature-modules.md) | 功能装配、两端指令、生命周期、页面/通知/口播注册与可选集成 |
| [模块合并](module-merging-design.md) | MergeableModule、类型化合并组、共享入口 / 卡片、成员权限、生命周期与固定商城接入 |
| [模块全量清单与推荐优先级](module-roadmap.md) | 23 类玩法候选、149 个调研细项、现有模块标记、基础服务及分阶段开发建议 |
| [通用任务接口与任务来源](quests.md) | Quest／QuestProvider、可选能力、跨来源查询、来源展示、编号与领奖校验、FTBQ 适配 |
| [固定交付委托](commissions.md) | 原生委托定义、接取／放弃、材料确认交付、周期次数、独立领奖与异常核对 |
| [活动时间窗口](activity-windows.md) | 共用日期／星期／时段规则、跨午夜、状态展示及新增资格复核 |
| [每日签到与本地进度评估](sign-in.md) | 固定循环、累计／连续／周月计数与里程碑，离线 / AI 路径及 Evaluate P90 / P95 |
| [成就总览与系列](achievements.md) | 数据驱动累计成就、系列统计、进度和幂等领奖 |
| [收藏图鉴](collections.md) | 数据包系列与物品谓词、无消耗登记、分页进度及里程碑领奖 |
| [事实记录、里程碑与任务积分](progress-rewards.md) | 可信服务端事件、永久去重、周期奖励、领奖恢复与 FTBQ 任务积分 |
| [商城周期轮换](shop-rotation.md) | 日／周／月固定选品、共享／个人库存、刷新时间与跨周期报价校验 |
| [固定物品收购](shop-buyback.md) | 数据包定义输入与报酬、独立收购列表、批量报价与确认、周期限额和系统空间结算 |
| [无限出售与无限收购](value-commerce.md) | Evaluate 价格、P90 解锁、汇率与倍率、固定库存优先和搜索生成上限 |
| [奖励与非物品结果](rewards.md) | 物品、系统币、经验点 / 等级、Saturn action，统一入库、预览与领取事务 |
| [商城与抽奖订单维护](commerce-orders.md) | 抽奖分批浏览、已结清订单归档、限购计数与管理员支付核对 |
| [抽奖规则与记录](lottery.md) | 数据驱动硬保底、连抽、按周期限抽、冷却、个人分页记录及结算恢复 |
| [条目条件与奖励效果](catalog-conditions.md) | 商品、奖池、签到奖励、在线作业的可见性 / 执行条件、服务端复核与已获权益 |
| [系统空间](system-space.md) | 通用内容类型、物品分页、点击 / Shift / 语音取出、奖励来源与实体扩展接口 |
| [多选一奖励](reward-choices.md) | 通用选择包、候选快照、永久确认票据、空间预留与资源包卡片 |
| [传送锚点](teleport-anchors.md) | 私人／公共目的地、公式容量、传送费用、安全落点、跨维度与冷却 |
| [主手物品鉴定](item-inspection.md) | 实际物品、耐久与附魔快照，获取来源入口 |
| [装备工坊](workshop.md) | 主手维修与分解入口、Saturn Cost 付款和目标保护 |
| [装备分解](salvage.md) | 分解规则注册表、耐久／附魔／Evaluate 数量公式、确认与持久订单；[设计推导](salvage-design.md) |
| [在线作业](online-jobs.md) | 数据驱动生产表、材料投入确认、串行作业队列、异常核对与独立领取 |
| [系统币与多币种钱包](economy.md) | 币种定义、余额、管理员指令、Saturn Cost、实体 action/condition |
| [物品相对价值 API](evaluate-api.md) | 内嵌 Evaluate、服务端查询入口、未知值、组件匹配、重载及 Java 估值器扩展 |
| [客户端物品检索](item-search.md) | 原版 SearchTree 名称／tooltip 检索、组件候选、分批传输与服务端校验 |
| [物品来源卡片](feature-modules.md#物品来源查询) | 本地配方 / 掉落 / 标签查询、材料格与产物图标、候选 tooltip、独立翻页及覆盖范围 |
| [FTB Quests 跨版本修复记录](ftb-quests-porting.md) | 1.21.1 标题 / 勾选礼包 / 领奖修复，以及待同步到 26.1.2 的入口与 API 差异 |
| [FTB 本地说明书检索](quest-guides.md) | 勾选说明任务收录、纯 Java 稀疏向量索引、查询入口、可见性与存档缓存 |
| [FTBQ 开发任务集](test-quests.md) | 12 章 80 任务、开发配置安装、分页 / 图钉 / 提交 / 解锁通知 / 说明书检查 |
| [分层本地数据库](databases.md) | 存档共享库、玩家独立库、Codec 数据表、FTB 重新收录指令与生命周期 |
| [JVector 索引评估](jvector-evaluation.md) | 发布体积实测、Java / SIMD、磁盘存储、稀疏 TF-IDF 适配与性能取舍；尚未接入 |
| [注册表速查](registries.md) | 正式注册表 ID、条目类型、公开入口、查询方式及扩展注册示例 |
| [客户端 JSON 卡片](cards.md) | CardData/CardBuilder、纯文本约定、通用模板解析、按钮动作、物品和资源包覆盖 |
| [卡片节点分类与职责设计](card-nodes-design.md) | 已实施：18 种节点、能力接口、通用网格 / 分组与 KOOK 风格按钮；保留重构前后的设计对照 |
| [Card JSON 编辑器](card-editor.md) | Vite 多文件工具、独立模板 JSON、节点树与属性编辑、示例 Context、预览和资源包导出 |
| [卡片与消息数据流](card-flow-design.md) | 已实现的两端边界、完整 DTO、按 ID/版本更新、结果状态枚举与 BuiltinTools 职责 |
| [轻量本地意图路由](semantic-routing.md) | 纯 Java 领域词表/TF-IDF、玩家意图缓存、只读直达、低把握回退与扩展 API |
| [路由选型与后续计划](semantic-routing-plan.md) | 10MB 分发约束、fastText/LIBLINEAR 等方案核实、校准与后续方向 |
| [扩展配置](datapacks.md) | 服务端配置、成就规则及签到/商城数据包；不使用旧注册表 |
| [界面与本地化](ui-localization.md) | 新工作台、AUI 文本安全、语言键约定 |
| [页面布局审计](ui-layout-audit.md) | spark 尖峰采样、GPU 同步回读、transform 提前测量、边框颜色动画误触发全量布局与针对性修正 |
| [AI / 联网 / TTS / ASR 用量统计](token-usage.md) | 服务端 AI/联网账本、本地语音用量、按次估价与撤销后保留 |
| [语音合成与音色设计](tts.md) | 阿里云 TTS、客户端密钥与语种、音色配置、Python 设计/临时上传工具 |
| [语音识别](asr.md) | NLS 一句话流式识别、AccessKey 自动鉴权、Token 缓存刷新、麦克风 session 与按次估价 |
| [文案与提示词](system-voice.md) | 系统流公开参考、语气规则与模型/模板边界 |
| [编译与验收](testing.md) | 本次已执行检查、尚未执行的验收和可选开发依赖 |
| [原始设计稿](agent-design.md) | 完整目标架构；其中未落地的部分以开发文档中的实现范围为准 |

默认未绑定，管理员先用 `/system grant <系统ID> [玩家]`（例如 `/system grant system:default Steve`）授予，自己绑定可用 `/system grant system:default`，系统 ID 从数据包注册表补全，当前每人最多一个系统；再用 `/system`、`/system simple` 或 `/system full` 打开界面。`/system revoke [玩家]` 撤销并清空该玩家业务记录，累计 AI / TTS / ASR 用量保留，可用 `/usage` 查看。服务端首次初始化会分别创建执行预算、模型连接、语义路由和成就规则配置；模型连接位于 `config/system/system-model.json`，凭据及用途绑定统一位于 `config/system/system-api.json`。默认使用 DeepSeek，设置环境变量 `SYSTEM_DEEPSEEK_API_KEY` 后启动服务端即可使用模型路由和 AI 签到，也可配置其他 OpenAI 兼容服务。新配置默认启用轻量本地路由，无需额外模型；已有关闭配置保留。高把握只读请求可直接处理。结构化玩家、物品、任务查询不需要模型密钥。
