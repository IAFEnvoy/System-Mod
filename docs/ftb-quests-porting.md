# FTB Quests 跨版本同步记录

记录日期：2026-10-30。`1.21.1` 的功能修改已同步至 Minecraft **26.1.2 / master**。来源为 `b601fd7..03ca46a`；先切换 master，再执行 `git pull --ff-only`，目标从 `2c459f9` 更新至 `b6afc9d` 后才合入修改。此次不提交、不推送。

## 来源与范围

1.21.1 开发依赖为 Quests `2101.1.37`、Library `2101.1.39`、Teams `2101.1.11`。最初问题来自用户整合包 `D:\PrismLauncher\instances\『化龍』导入包-1.4\minecraft`：其 Quests / Library / Teams 为 `2101.1.34 / 2101.1.35 / 2101.1.10`，现有日志加载 24 个章节、837 个任务和两种语言。只读取了定义、依赖与日志，没有修改整合包或存档。

例如 `359B897165997027`（“鸣谢名单”）为勾选目标、物品与 100 经验组合，暴露了旧礼包分类只接受物品奖励的问题。标题问题则来自服务端共享回退语言，不能用直接 getTitle 替代玩家语言解析。

只迁移功能，不带入 1.21.1 的版本降级、嵌入式 Saturn、兼容 ItemStackTemplate 或导入整理。目标保留 Java 25、NeoForge `26.1.2.114`、AUI `1.2.6`、外部 Saturn，以及 Quests / Library / Teams `26.1.2.8 / 26.1.2.9 / 26.1.2.4`。保留拉取后已有的通知立即替换、手动切换和任务完成勾叉展示。

## 已同步功能

| 功能 | 实现入口与行为 |
| --- | --- |
| 玩家语言与格式文本 | `FtbQuestText` 按玩家语言调用 TranslationManager，保留缺译回退；覆盖列表、搜索、章节、详情、提交、通知和播报。无标题任务递归取首个目标标题，Component / § / & 统一处理，不修改共享 locale 或 title cache。 |
| 勾选任务与礼包分类 | `FtbQuestReader` 精确匹配原生类型：不可重复、全部为勾选且至少一个目标、至少一项非空物品，可附带经验 / 等级；隐藏、命令、自选、随机和第三方奖励保留普通处理。无目标或纯勾选且无奖励作为介绍任务，不进入进度列表。 |
| 原生类型图标与 tooltip | `QuestIconOutput` 在服务端准备任务 / 奖励类型 ID、本地化文字和数量；客户端 `system:icons` 反射加载 FTB 图标元素。物品仍走原版 item tooltip，非物品复用 FTB 类型图标与文本 tooltip；缺少依赖或类型时回退文字。 |
| 紧凑奖励布局 | 任务详情、完成通知和礼包只显示一次奖励标题，物品和类型图标横排、超宽换行，自选项保留编号和操作。 |
| 解锁汇总 | 先发送当批逐项完成通知，再分别发送礼包汇总与普通解锁通知；单项普通解锁直接详情，多项合并列表，每个接收者每批至多一条。列表通知每页 10 项，本地翻页；按 ID 查看详情后可返回原页，不覆盖主动查询编号。重登、重置基线和静默操作不补发旧解锁。 |
| 说明书检索 | 仅收录勾选 / 无目标、有正文、非礼包任务；`guideCandidates / guideReadable` 包含介绍任务，仍核对章节与任务可见性、详情解锁及正文隐藏条件。返回前回到服务端复核队伍、语言、任务对象和内容。 |
| 聊天历史领取礼包 | 保留“领取礼包”与本地“确定”；礼包凭证在同一连接内不再按两分钟失效，仍校验玩家、队伍、任务 / 奖励对象、完成时间及原生状态。登出、撤销或停用模块清理，普通奖励短期凭证不变；磁盘历史不重新授权。 |
| 奖励合计 | `FtbQuestPreviews` 合并同物品与组件、数量 / 随机区间，以及经验和等级；六份 100 经验显示 +600。物品模板 count 为 1，only_one 条件物品独立，不提前随机或发奖。 |
| 分层本地数据库 | `agent.database` 为世界共享和 UUID 独立库提供同一套 Codec 数据表与稀疏 TF-IDF API；存于 `<world>/system-database/shared.json` 和 `players/<UUID>.json`。revoke / reset 删除私有库，累计用量不变；无新增模型、native、外部服务或依赖。 |
| FTB 重新收录 | `/system ftb reload` 要求管理员权限，可由控制台执行；重新收录 FTB 已加载定义并重建索引，不读写原生任务文件或进度。缓存失效事件取消旧代次结果。 |
| 数据库优先问答 | 核心 `system:question` 先用 `database.query` 查询当前玩家 / 共享知识与已启用 FTB 的授权说明。有资料时至多一次模型整理，失败可展示原文；存储错误不当作空结果。只有查库为空、搜索模块启用、当前明确允许联网且外部服务开启才运行 WebResearch。 |
| 搜索工具与授权 | `SearchSystemModule` 为 ToolModule，导出 search / fetch；`Descriptor.optionalTools` 保留工具所属模块的权限，缺少搜索不影响本地问答。`/question` 只查本地，`/search` 允许空结果联网，测试默认 tag 不再自动赋予搜索。预算键为 `system:question`。 |

## 26.1.2 API 适配

此次直接核对目标依赖 source JAR，提取在 `build/ftbsrc/26.1.2.8`、`build/ftblibsrc/26.1.2.9` 与 `build/auisrc/26.1-1.2.6`；Minecraft 签名由实际 26.1.2 JAR 确认。

- **服务端实例**：目标 `ServerQuestFile.getInstance()` 返回不可空的实例，并非 Optional；新增入口先检查 `exists()`，不保留来源代码中不适用的 Optional 调用。
- **原生事件**：保留 master 的 NeoForge `FTBQuestsEvent` 与 `ProgressEventData / ProgressType`，没有带入 Architectury 事件。ClearFileCache 按 ServerQuestFile 定位桥接，并同时使说明书代次失效。
- **领取顺序**：普通奖励继续交给 TeamData.claimReward。目标 Reward.claim 返回 void，无 tryClaim；自选严格跟随目标 ClaimChoiceRewardMessage，成功 markRewardAsClaimed 后调用所选奖励 claim，最后只检查原生已领取标记，不宣称第三方实际交付成功或提供回滚保证。
- **奖励数据**：目标 writeData 接受 Json5Object，以 Json5Util 读取 xp / xp_levels / random_bonus / only_one，不使用 1.21 的 CompoundTag。物品预览采用原版 immutable ItemStackTemplate，按物品与组件相等合并。
- **文字解码**：JSON Component 通过 ComponentSerialization.CODEC 与注册表 JsonOps 读取，无法解析则保留字面量，不加载 FTB 客户端文本辅助类。
- **客户端绘制**：目标类型使用 getIcon，Icon 的 renderer 向 GuiGraphicsExtractor 记录渲染状态。新增 FtbQuestIconRenderer 在 AUI BODY 阶段，将几何、文字及物品重放到当前 AUI 输出目标、变换和投影，复用活动 scissor；不调用会重置目标的完整 GuiRenderer。tooltip 使用 setTooltipForNextFrame。不存在旧 GuiGraphics / flush 或全局 GL 深度恢复代码。
- **命令与命名**：原生 Commands.hasPermission；公共资源 ID 使用 Identifier。可选 FTB 类仍隔离于 compat.ftb，专服不引用客户端图标实现；未安装时不加载可选类。

## 数据格式

1.21.1 原生任务使用 `.snbt`，语言为 `quests/lang/<locale>.snbt`。26.1.2 继续使用目标原生 `.json5` 示例与布局；此次未把旧版示例、run 目录或整合包文件复制过来，也未增加旧存档迁移代码。说明书与背景从新库按需重建，旧的独立缓存文件不再读写。

## 验证与待验收

已执行 `gradlew compileJava testModClasses`，主代码与测试源集编译通过；最终执行 `gradlew compileJava testModClasses jar --console=plain` 成功，JAR 为 1,542,493 字节；双语各 513 键、23 份卡片与 13 个节点类型核对通过。完整静态检查与最终结果见 [编译与验收](testing.md)。没有启动 Minecraft、运行测试、创建存档或调用收费 API，因此不能据此认为游戏内验收已经通过。

后续在目标版本人工验收：

1. 中英文玩家的任务标题 / 描述 / 搜索 / 通知 / 播报，以及缺译、无标题、JSON、§ 和 & 文本。
2. 勾选与物品加经验礼包、介绍任务排除、普通任务提交、原生依赖 / 暂停 / 顺序 / 自动领奖及队伍共享。
3. 图标、数量、物品与文字 tooltip、第三方类型、滚动裁剪、淡入合成和按 T 交互；缺少 FTB 的历史文字回退。
4. 单项解锁直接详情、多项合并列表且每页 10 项；本地上 / 下页不发包或切换通知，查看详情后返回原页。完成通知逐项发送且优先，底部确定关闭整个窗口，播报结束不自动翻页。
5. 礼包稍后从聊天历史领取、重复点击与部分失败、经验 / 等级和组件物品合计。
6. 共享 / 私有库隔离和持久化、重载权限与并发失效、数据库错误处理；本地有结果不搜索，查库为空且明确授权才联网，禁用搜索模块不影响本地问答。

未经明确要求，不为上述验收启动游戏、插入探针或创建临时数据。

## master 后续变更：交互式任务通知窗口

2026-10-30 在 master 增加以上分页与返回行为。后续同步其他版本时，需一并同步 `ActionsNode` 的 `notification_page_size`、通知持有的 `CardPageState` / 返回栈、`CardButton.Behavior.PUSH` 与请求来源关联、`quest_list.json`、共享 CSS 和四个双语键；服务端 `unlockNotification` 的单项分支复用 `FtbQuestAccess.detail(..., "unlocked")`，完成事件循环不合并。该变更不新增依赖、网络包或数据库格式。


## 2026-10-30 本轮补充移植

以下先保留移植前的核对记录；实际实施结果见后续小节。

### 2026-10-30 待移植清单复核（1.21.1 `7aca25a` → master `b97aa1c`）

本次仅阅读并比较源码、依赖源码和资源，执行 `git fetch origin` 更新远端引用；未切换分支、修改 master、编译或启动游戏。远端 master 当前为 `b6afc9d`，本地 master 多出的 `b97aa1c` 尚未推送，下表以本地 master 为基线。

| 状态 | 范围 | 核对结果与后续处理 |
| --- | --- | --- |
| 待同步 | `screen/card/node/ActionsNode` | 回调捕获前调用 `Element.init`。已直接检查 26.1 / AUI 1.2.6 的 `Node.prepareForInsertion` 与 `Element.init`，目标版本同样会替换普通 Element，分页按钮也有同一问题。 |
| 待同步 | `speech/asr/RecognitionRecording`、`AssistantRecognition` | 同步停止与入队互斥、缓冲耗尽后直接 EOF、终态回调保护、90 秒会话 / 15 秒收尾超时、取消清理和执行器拒绝处理；平台无关，不涉及 Windows 输入法。 |
| 先修正再同步 | `RecognitionRecording.record` 的自然 EOF | 当前 `ended = true` 写在 try-with-resources 外层 finally，实际发生在设备 `close()` 之后，尚未实现“自然 EOF 在设备关闭前发布”。手动停止已可通过 `stopped` 返回 EOF；自然结束则应将发布动作调整至资源关闭前，再同步两版本。这里只记录问题，未改实现。 |
| 按目标依赖确认 | `ImBlockerConfigMixin`、`system.mixins.json` | 当前白名单注入只核对过 IMBlocker 5.6.1 / 1.21.1，不代表适用于 26.1.2；有目标版本集成需求时先检查实际 JAR，缺少可用版本则不迁移。 |
| 保留目标实现 | `AssistantInputScreen`、`TokenUsageData` | 26.1.2 已有原生文本输入 / preedit 生命周期，不能随 1.21.1 的简化一起删除；SavedDataType 使用合法 Identifier，也不应套用 1.21.1 的字符串文件名修正。Windows 专用输入法检测已删除，不加入目标版本。 |
| 已同步 | 任务通知窗口与 FTB 业务 | PUSH / 返回栈、每页 10 项、单个新任务直接详情、完成任务分别通知均已在本地 master；本轮对比两分支的 `assets/system` 卡片 / 语言和共享 CSS 无差异。FTB 事件、奖励调用、ItemStackTemplate 和图标渲染仍保留各版本 API。 |

同步时更新 ASR 和分页相关说明，保留 26.1.2 自己的版本信息、渲染与输入法文档；不能整份覆盖 1.21.1 的文档或直接 cherry-pick `7aca25a`。

### 实施结果

已切换 master 并执行 `git pull --ff-only origin master`，远端无新提交，基线仍为 `b97aa1c`。同步 `7aca25a` 的 ActionsNode 提前初始化、RecognitionRecording 停止 / 帧队列处理和 AssistantRecognition 的终态、超时、取消及错误处理；额外将自然结束时的 `ended = true` 移至 try-with-resources 关闭设备之前，外层 finally 仍保证异常路径最终发布终止。该额外顺序修正目前只在 master，1.21.1 后续需回同步。

26.1.2 的 AssistantInputScreen 原生文本输入 / preedit、TokenUsageData 的 Identifier / SavedDataType，以及版本、依赖和 Mixin 配置保持原实现。2026-10-30 查询 [IMBlocker 的 Modrinth 26.1.2 版本列表](https://api.modrinth.com/v2/project/imblocker/version?game_versions=%5B%2226.1.2%22%5D) 为空，未将仅核对过 1.21.1 的白名单 Mixin 加入目标；没有增加平台专用输入法代码。

实际执行 `gradlew compileJava testModClasses jar --console=plain` 成功。静态 / 产物检查：中英文各 517 键且集合相同，23 份卡片 JSON 可解析，三个修改类均为 Java 25 字节码；`system-1.0-beta.1.jar` 为 1,548,823 字节，无内嵌 Saturn、旧 screen/input 类或 IMBlocker Mixin。没有运行测试、启动游戏、创建存档或调用收费 API；分页点击、设备结束与取消、识别自动发送仍待游戏内验收。改动未提交或推送。
