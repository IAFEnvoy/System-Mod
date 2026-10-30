# FTB Quests 跨版本修复记录

记录日期：2026-10-30。以下 1.21.1 修复已同步至 26.1.2 / master（`b97aa1c`）；同日将 master 的交互式任务通知窗口回同步至 1.21.1。下方旧节保留首次修复时的迁移分析，“也需同步”等措辞为历史清单；最新结果见本文末尾的同步记录。

## 依据与复现数据

- 开发依赖：FTB Quests `2101.1.37`，FTB Library `2101.1.39`，FTB Teams `2101.1.11`。
- 用户整合包：`D:\PrismLauncher\instances\『化龍』导入包-1.4\minecraft`；实际安装 Quests `2101.1.34`、Library `2101.1.35`、Teams `2101.1.10`。只读取了任务定义、依赖 JAR 和现有日志，没有修改整合包或存档。
- 整合包日志已加载 24 个章节、837 个任务、两种语言。任务定义为 `.snbt`，翻译位于 `quests/lang/zh_cn.snbt` 与 `en_us.snbt`。因此本次整合包问题不是任务文件没有加载。
- 任务 `359B897165997027` 的中文标题为“鸣谢名单”，仅有 `CheckmarkTask`，奖励为 100 经验和一件物品。这是旧礼包判定遗漏“物品 + 经验”组合的具体例子；分类依据是任务结构，不是名称。
- 对照了上述两个 Quests 版本的反编译源码，以及当前 Minecraft / NeoForge sources JAR。没有启动游戏；以下结论来自静态阅读与编译，不代表游戏内验收通过。

## 必须同步的修改

| 修改入口 | 根因与修改 | 同步到 26.1.2 时的核对点 |
| --- | --- | --- |
| `compat/ftb/FtbQuestText` | 在 1.21.1，服务端 `ServerQuestFile.getLocale()` 固定取任务文件的回退语言；直接 `getTitle()` / `getRawDescription()` 无法按接收玩家的语言读取。新增单一文本入口，使用玩家客户端语言与 FTB `TranslationManager` 的 TITLE / QUEST_DESC，并保留其原生缺译回退。 | 核对 TranslationManager API、语言键和玩家语言字段；不能临时修改共享 file locale 或 cachedTitle，否则不同语言的玩家会互相影响。 |
| `FtbQuestText.title` | 未命名任务沿用原生“第一个目标标题”回退，但第一个目标也递归按玩家语言读取。JSON 文本通过原版 lenient Component 解析器处理，再交给 LegacyText 处理 § / & 格式；普通文字和无法解析的 JSON 保留字面量。 | Component 解析 API 在两个 MC 版本可能不同。不要调用 FTB `TextUtils.parseRawText`：当前实现会调用客户端类，不能在专服使用。 |
| `FtbQuestAccess`、Query / Find / Submit 工具 | 列表、详情、章节、目标、奖励、自选项、名称搜索和提交回执统一调用 FtbQuestText。查询与显示使用同一种本地化标题。 | 完整迁移所有调用点，不能只修列表；保留 ID 查询、精确优先、玩家列表编号和授权校验。 |
| `FtbQuestSpeech`、`FtbQuestNotifications` | 按接收玩家生成标题、描述与播报快照；TASKS / REWARDS 格式化器带入玩家，未知类型的模型输入也用相同标题。 | 同步快照签名和所有调用方；保留已有按任务 ID、语言和内容区分的播报 / 背景缓存。 |
| `FtbQuestReader.kind` | 旧逻辑要求所有奖励均为物品。现在：至少一个原生勾选目标、全部目标均为原生勾选、不可重复、至少一项非空物品；允许附带原生 XPReward / XPLevelsReward。全部奖励必须非隐藏。 | 保留精确类匹配。仅经验、无目标有奖励、命令 / 自选 / 随机 / 第三方奖励、混合目标和可重复任务仍是普通任务。无目标或纯勾选且无奖励仍是介绍任务。 |
| `FtbStarterGifts`、`StarterGiftOutput`、`cards/starter_gift.json` | 整体预览包含 `items` 与 `icons: List<QuestIconOutput>`，通过原生类型图标展示未领取的经验 / 等级合计；各 Gift 仅保留 ID、引用和错误。不显示来源任务名称。 | 同步公共载荷 Codec、构建参数与客户端模板；两端一起更新。保留原生任务提交、奖励领取、服务端凭证、静默刷新及不重复发完成通知。 |
| `FtbQuestClaimTool` 与双语 `system.error.claim_rejected` | 1.21.1 的 `Reward.claim` 返回 boolean，`tryClaim` 还运行领取事件。普通奖励交给 `TeamData.claimReward`；自选奖励按原生 ClaimChoiceRewardMessage，先 `selected.tryClaim` 成功再标记外层奖励。未标记时返回明确失败，不能先标记再忽略处理器结果。 | **版本专属差异，不要机械复制**：先核对 26.1.2 的 Reward.claim / tryClaim 返回类型、TeamData.claimReward 以及自选奖励网络处理器，遵守目标版本原生顺序。 |

所有新增 FTB 引用仍在 `compat.ftb` 隔离层；公共礼包 DTO 只持有原版 Component 和物品预览，不引用 FTB 或客户端类。

## 2026-10-30 原生类型图标与 tooltip（也需同步）

新增 `QuestIconOutput`（类型分类、ResourceLocation 类型 ID、标题、数量 / 进度、tooltip），`FtbQuestPreviews` 在服务端统一准备非物品的图标描述。扩展 QuestTaskOutput / QuestRewardOutput / QuestChoiceOutput、QuestNotice 和 StarterGiftOutput，对应卡片 context 填入 task_icons / reward_icons / choice_icons。物品预览保持不变，自选奖励候选的状态取外层奖励。

新增客户端 `system:icons` 节点及注册，`IconsNode` 仅在首次渲染且安装 FTB 时通过反射加载 `compat.ftb.client.FtbQuestIconElement`。后者直接使用 TaskTypes / RewardTypes 的 `getIconSupplier().draw`，所以图标与 FTB 创建菜单一致，支持第三方注册类型，无须复制纹理。缺少依赖 / 类型时退回文字。新增 `AssistantTooltip`，沿用 `AssistantItems.tooltip` 的屏幕 / HUD 绘制后时机，非物品显示文字 tooltip，物品仍调用原版 ItemStack tooltip。

同步范围包括新增 DTO / helper / 客户端图标元素 / 节点、SystemCardNodeTypes、四份任务 / 礼包模板和 assistant.css，以及 `system.ui.quest_icon_progress` 双语键。26.1.2 必须重新确认：FTB 类型表和图标供应器方法、Icon.draw 的参数、AUI 自定义元素绘制阶段 / clip / opacity 机制、GuiGraphics / pose API 和 tooltip API；不能直接复制 1.21.1 中构建 GuiGraphics、复制矩阵和恢复 GL 深度状态的代码。公共载荷不允许引用 FTB / AUI / 客户端类，专服不解析图标。TTS 的 SpeechContent 不改成图标数据。

后续人工验收还需检查：各类内置图标、第三方能量目标、非物品自选奖励、礼包经验数量、中文 / 英文 tooltip、通知按 T 交互、滚动裁剪与展开淡入动画，以及未安装 FTB 时历史卡片的文字回退。此次只有编译与静态检查，没有启动游戏。

后续布局修正也需一起同步：`starter_gift.json`、`quest.json`、`quest_notice.json` 将“奖励”标题移至循环外层，使用共享的 `reward_section` / `reward_grid` / `reward_entry` 样式横排物品和类型图标，超宽换行，去掉子节点重复标签。`QuestNotice.buildCard()` 新增派生状态 `has_rewards`，防止无奖励时出现空标题；自选奖励编号、按钮、礼包错误提示和 tooltip 保留。

## 2026-10-30 解锁任务批量通知（也需同步）

`FtbQuestNotifications.flush` 仍按队伍在 tick 内汇总进度事件、对比可推进任务基线；同批新增且未完成的普通任务按原顺序合并成一个列表，每位有效在线接收者最多收到一条解锁通知。先发送所有完成项，再发送礼包汇总与普通任务解锁列表；静默操作、队伍锁定切换、新加入玩家和重登不补发已有解锁。礼包继续使用原本的汇总与领取流程。

新增公共 DTO `QuestUnlockedOutput` 及 `SystemCardDataTypes.QUEST_UNLOCKED` 注册，复用 QuestList 的按任务 ID 查看动作和 quest_list 模板，设置 `unlock_notice` 隐藏编号及“查看 1”提示，不写入或覆盖主动查询列表缓存。增加 `system.tts.message.quests_unlocked` 双语键，摘要和播报仅提示数量。原 notification 方法改为 completionNotification，只构建完成通知；任务要求、奖励和提交按钮在点击后的详情中按原逻辑提供。

同步文件包括 FtbQuestNotifications、QuestUnlockedOutput、SystemCardDataTypes、quest_list.json 和两份语言文件。26.1.2 需保留自身 FTB 事件接入方式，仅同步批量发送与公共卡片逻辑；人工验收应覆盖一次解锁多个任务、同 tick 多次完成、队伍多玩家、单任务解锁、礼包混合解锁、滚动与点击替换、重登及静默重置。此次未启动游戏。

## 2026-10-30 本地说明书索引（也需同步）

新增 QuestKnowledgeCache、QuestGuideInput / Output / Module、FtbQuestGuideTool 和 `system:quest_guide` 动作 / 卡片注册，任务页及 guide / 说明书 / 查说明 指令可直接查本地说明。采用用户确认的收录规则：仅原生勾选或无目标、有说明正文、非礼包任务。独立 guideCandidates / guideReadable 允许读取被进行中视图过滤的 INTRODUCTION，保留原生任务与章节可见性、详情解锁和完成后显示正文的限制。

静态文档按存档与玩家语言缓存，复用纯 Java LexicalIndex，后台切段和向量检索，不引入模型运行库、外部 embedding 或数据库服务；当前文档变化会重建，返回前回到服务端再次检查队伍、语言、任务对象及正文。原文使用 FtbQuestText 新增 rawDescription 读取，只有现有 description 才是权限感知的展示入口，不得直接把 rawDescription 返回给玩家。

同步还包括 LexicalEncoder 非领域模式保留注册 ID 词、BuiltinTools、FtbQuestAdapter / Access、QuestSystemClientModule、assistant.html / css、quest_guide.json 和双语语言键。26.1.2 需核对其 FTB 原生任务类型、可见性与正文隐藏 API，沿用目标版本的玩家语言和 Java / MC 类型；不机械复制版本专属接口。说明与未验收范围见 [本地说明书检索](quest-guides.md)。

## 2026-10-30 聊天历史领取礼包（也需同步）

礼包通知保留独立的“领取礼包”和本地“确定”，不使用 PRIMARY。原礼包凭证两分钟后逐条报 STALE_REFERENCE，导致关闭通知后再从聊天历史领取失败。`FtbStarterGifts` 移除仅针对礼包的时间过期限制，仍检查玩家、队伍、任务 / 奖励对象、完成时间、原生前置和领奖状态；同一连接内可稍后领取，已领项不重复发放。普通任务的短期奖励引用保持原规则。

`FtbQuestAccess.clear(UUID)` 统一清理礼包凭证、普通奖励引用与列表缓存，由撤销和 `FtbQuestNotifications.remove` 的模块会话结束入口调用，登出或停用任务模块后不保留凭证。同步这三个类及使用说明；26.1.2 沿用自身模块会话结束入口，不机械复制 1.21.1 的 API。历史从磁盘恢复仍只读，不重新授权旧卡片。

## 2026-10-30 礼包奖励预览合计（也需同步）

`FtbQuestPreviews` 在服务端分别合并经验值和经验等级，数量使用 long，tooltip 和图标旁数字都使用总量；六份 100 经验只显示一个 +600。相同物品及组件的数量 / 随机区间合计，展示模板的物品 count 保持 1，避免超过堆叠上限而无法渲染；`only_one` 条件物品保持独立。读取 FTB 原生 writeData 的 `xp` / `xp_levels` / `random_bonus` / `only_one` 字段，不解析客户端按钮文字，也不提前随机或执行领奖。

`StarterGiftOutput` 改为整体 `gifts + items + icons`，Gift 仅保留 questId / claimRef / error。`FtbStarterGifts.snapshot` 统一对仍未领取、未屏蔽的原奖励准备汇总，首次查询与部分失败后的刷新共用；每个任务仍分别提交、领取和记录失败。`starter_gift.json` 只渲染一份汇总 items / icons，错误单独循环，has_rewards 避免空标题。同步上述四个文件与文档；26.1.2 需核对原生数值访问 API / 存储字段，并使用目标版本 ItemStackTemplate 的组件匹配方式。当前未修改 26.1.2。

## 2026-10-30 分层数据库与 FTB 重新收录（也需同步）

QuestKnowledgeCache 已拆成通用 agent.database 下的 AgentDatabases / AgentDatabase / DatabaseTable / KnowledgeIndex。共享库位于 `<存档>/system-database/shared.json`，原文与背景分别使用 `system:ftb_guides` 和 `system:quest_backgrounds` 表；玩家私有库为 `players/<UUID>.json`，预留同一套 Codec 读写及检索 API。AgentEngine 统一持有并关闭数据库，revoke / reset 删除私有库；不迁移玩家业务记录或累计用量，不读取旧的两个缓存文件。QuestBackgrounds 改为直接访问通用表，原来独立的文件 / Map 缓存逻辑删除。

新增 FtbQuestKnowledge，按语言缓存服务端静态文档，查询只重算允许读取的 ID 并在返回前复核来源内容；后台使用有界维护队列完成数据库操作与检索。FTB 原生 ClearFileCacheEvent 使快照代次失效，旧查询不得返回；多个 FTB 配置复用同一份世界知识。QuestSystemModule 声明管理员 / 控制台指令 `/system ftb reload`，通过已隔离的适配器重新收录当前已加载的定义、移除过期来源、重建索引并反馈数量，不读写原生任务文件、进度或奖励。

26.1.2 需同步上述通用类、FtbQuestAccess / GuideTool / Adapter / Notifications、QuestSystemModule 指令、FtbQuestText 的显式语言重载、QuestNarration / Backgrounds、AgentEngine / AssistantServer 装配、双语提示及文档；沿用目标版本的 FTB 获取实例、缓存失效事件与 HolderLookup API，避免直接复制当前 Optional 签名。编译与静态检查通过，未启动游戏，玩家隔离、持久化、命令权限、编辑 / 重载并发和专服缺少 FTB 的行为仍待实际验收。详细约定见 [分层本地数据库](databases.md)。

## 2026-10-30 数据库优先问答（也需同步）

核心 QuestionModule / `system:question` 新增通用 `database.query`，汇总当前 UUID 私有库与共享库的 `system:knowledge` 文档，并通过已启用 FTB 模块的 `ftb.guide` 读取授权说明；禁止直接扫描共享 `system:ftb_guides` 原始表。FtbQuestAdapter.modules 改收 ModuleContext，在当前模块 owner 下导出并复用同一个 GuideTool；QuestSystemModule 的反射签名同步变更，依赖缺席时依然不加载适配器。

有本地资料时至多一次模型整理，发送前再次查库确认来源仍相同；外部 AI 关闭、密钥缺失、额度不足或可恢复失败时展示原文，不转为搜索。仅查库为空、搜索工具模块启用且当前 QuestionInput.allowWeb=true 时，ExecutionContext 才开放本次联网门禁。SearchSystemModule 改为 ToolModule，移除旧的 WebSearchInput / WebSearchModule，网页 ReAct 由 WebResearch 提供；开发默认 tag 不再授予搜索能力。

26.1.2 需同步 AgentModule.Descriptor.optionalTools、BuiltinModule / ModuleRegistry / ExecutionContext、数据库查询与问答 DTO / 卡片、Core / Search 模块、工具与动作 / 卡片注册、routing / knowledge_answer 提示词、双语键及搜索页。FTB 只同步导出与调用链，保留目标版本的原生权限、队伍、语言与后台复核逻辑。预算键改为 `system:question`；不能复制旧的 `system:web_search` 独立工作流或默认 tag 追加。当前未修改 26.1.2，游戏内授权与供应商请求仍待人工验收。

## 另一个开发数据问题

仓库 `examples/ftbquests` 和已有 `run-test-client/config/ftbquests` 仍保留 26.x 的 `.json5` 示例；当前 1.21.1 FTB 读取 `.snbt`，语言表也应是 `lang/<locale>.snbt`，不是 `lang/<locale>/chapters/*.json5`。现有 test-client 日志加载 0 章节 / 0 任务源于此，不能拿该环境的空列表判断此次整合包修复是否成功。

这次没有改写开发运行目录或示例，也没有自动转换用户整合包。后续维护两个版本的示例时，应分别保留目标 FTB 原生格式，不要把 1.21.1 的文件布局直接同步到 26.1.2。

## 后续人工验收

1. 中文与英文玩家分别查询、按名称查找及查看同一任务，确认标题、章节、描述、通知和播报跟随各自语言；再检查只在回退语言有翻译的任务，以及无标题、JSON / § / & 格式标题。
2. 新绑定时，确认可开始的“勾选 + 物品 + 经验 / 等级”进入汇总礼包；奖励卡片显示物品和经验，不显示任务名。已绑定玩家重登不重复发绑定引导。
3. 确认纯介绍任务仍被忽略，仅经验、重复任务和命令 / 自定义奖励不会被当作礼包；普通勾选任务继续显示“完成此任务”。
4. 领取礼包后确认物品与经验原生发放、静默刷新、不可重复领取；前置、暂停、队伍共享、任务顺序和原生自动领取继续生效。
5. 让原生奖励条件拒绝普通或自选奖励，确认不会提前标记已领取；解除限制后可重新请求领取。

未经用户明确要求，不为上述验收启动游戏、插入探针或创建临时存档。当前仅执行编译和静态检查，执行记录见 [编译与验收](testing.md)。


## 2026-10-30 master 通知窗口回同步 1.21.1

切换 `1.21.1` 后先执行 `git pull --ff-only`，远端无新提交（目标 `03ca46a`）；来源为 master `b97aa1c`。此次仅同步交互式任务通知，不合并版本、依赖、内联 Saturn 或 26.x 渲染代码。

- `ActionsNode.notification_page_size=10` 控制通知内任务列表分页，上 / 下页和页码独立位于卡片正文，底部确定结束整个窗口。翻页只改 `CardPageState` 和局部 DOM，不发包、不生成新通知或推进队列。
- `CardButton.Behavior.PUSH` 与来源请求关联保留当前列表；详情正文提供返回，恢复原页且不重复播报列表。更新保留页码，移除清理对应返回项；返回淡出期间的迟到回复不能覆盖待返回视图。
- 单个普通新解锁任务直接发送 `QuestOutput` 详情，使用 `unlocked` 主句；多个任务发送分页列表。完成事件仍每个任务单独通知，礼包流程不变。
- 同步 `CardPresentation`、共享 CSS、`quest_list.json` 和四个双语键。1.21.1 保留 ResourceLocation、项目内 ItemStackTemplate、Architectury 进度事件，以及 ScreenEvent.Render.Post / GuiGraphics tooltip；没有引入 26.x 的 ApricityGuiLayers 提交逻辑。

实际执行 `gradlew compileJava testModClasses --console=plain` 成功，未运行测试或启动游戏。静态资源与语言检查结果见 [编译与验收](testing.md)；仍需游戏内验收 10 / 11 项边界页、大列表、返回原页、任务完成逐条通知和单 / 多任务解锁。改动留在 1.21.1 工作区，未提交或推送。

### 回同步后的分页点击修正（master 尚需同步）

用户反馈点击“下页”无反应。阅读 AUI 1.21.1 / 1.2.6 的 sources JAR 确认：Node.prepareForInsertion 调用 Element.init，普通 Element 会被注册的 Div 实例替换；旧回调持有原始分页容器，其 isConnected 为 false。ActionsNode.render 改为先 Element.init(view.element(...))，再构建分页及回调，使后续挂载和重绘指向同一实际容器。保留连接与交互检查，不绕过通知生命周期。此修正当前仅写入 1.21.1，master 的同源 ActionsNode 也需同步这一行及注释；编译通过，未启动游戏复测。

### 1.21.1 SavedData 文件名修正

用户在 Windows 保存世界时报告 `data/system:token_usage.dat` 的 InvalidPathException。核对 1.21.1 DimensionDataStorage.getDataFile 后确认它直接将字符串键拼接为 `<键>.dat`，不会像 26.x SavedDataType 一样拆分命名空间。TokenUsageData.FILE_ID 改为 `system_token_usage`，实际路径为 `<存档>/data/system_token_usage.dat`；去掉无用的 ResourceLocation import，用量内容及撤销保留规则不变。本修正仅适用于 1.21.1 的文件名 API，不能机械改动 master 的合法 Identifier / SavedDataType。扫描主源码与内联 Saturn，仅此一处使用原版 SavedData；编译通过，未修改用户存档或启动游戏。


### 1.21.1 ASR 收尾与输入法（后续同步注意）

RecognitionRecording 手动停止与帧入队共用锁，耗尽缓冲后直接返回 EOF，不等待设备线程；自然结束的 EOF 也改为在设备 close 之前发布，结束后读取帧队列时复核最后一帧；AssistantRecognition 增加独立 session 计时、完成终态、后台错误和执行器繁忙处理。这两处是跨版本共有的语音链路修正，后续需同步到 master / 26.1.2。

中文输入法修正则针对 1.21.1：`ImBlockerConfigMixin` 用 `@Pseudo` 和字符串目标注入 `IMBlockerConfig.isScreenInWhitelist`，将全部 `AssistantPageScreen` 子类视为白名单，不改用户配置，无 IMBlocker 编译或运行依赖。核对实际整合包 IMBlocker 5.6.1 JAR 及 v5.6.1 源码；不自行管理其控件焦点、候选定位或字符探测，也不再保留 Windows 专用组合串检测与按键保护。文字输入沿用 AUI 和平台原有流程。master 已有 26.1 原生文本输入 / preedit API，后续同步应保留其平台原生支持；白名单 Mixin 需先核对目标版本 IMBlocker 签名。此次只做编译和源码 / JAR 检查，没有游戏内验收。
