# 编译与验收

## 2026-11-07 Card JSON 编辑器迁移到 Vite

将原 tools/card_editor.html 拆分到 html/card-editor：页面、编辑操作、节点字段与校验、浏览器预览、示例上下文和样式各自维护；27 份模板、双语语言表及共享 CSS 改为独立资源文件。资源通过 Vite glob 加载，新增 sync:resources 命令从游戏资源刷新快照；html/.gitignore 排除依赖、构建产物和缓存。下方提到旧单文件路径的内容保留为历史验收记录，当前入口与命令见 [Card JSON 编辑器](card-editor.md)。

实际执行 npm install、npm run sync:resources 和 npm run build 成功（Vite 8.3.6，42 个模块）。静态核对 27 份模板、两份语言表及共享 CSS 与游戏资源一致；HTML ID 无重复、入口不再内嵌脚本和样式，全部 JS 的 node --check 与 git diff --check 通过。未运行测试、浏览器交互或 Minecraft；本次结论为生产构建与静态检查通过。

## 2026-11-06 Saturn 通知 HUD 编辑器

读取 Saturn 1.2.5 HUD 框架源码，并通过实际依赖 JAR 确认 AbstractHudEntry、HudAnchor 与 HudManager 签名。通知以 public static 的 SystemHudEntries.NOTIFICATIONS 登记，直接使用 Saturn 自带的编辑入口，不另加设置按钮或按键，复用 Saturn 拖动、锚点、显隐与 config/saturn/hud.json；真实 AUI 通知通过 GUI ↔ 文档坐标转换应用位置。尺寸只读取已提交、未变换的根面板矩形，放置未变化时不重复写样式；预览使用被动示意框，无消息队列、业务或播报副作用。

实际执行 `gradlew compileJava processResources --console=plain` 成功。静态核对中英文各 576 键且集合一致，设置页 translation 与控件 ID 有效，26 份卡片 JSON 可解析，编辑器快照与资源一致，git diff --check 通过。未运行测试、Minecraft 或外部 API；拖拽／方向键、锚点切换、保存重启、GUI／文档缩放、内容高度变化、隐藏后恢复、动画与聊天按钮位置仍需游戏内验收。

## 2026-11-06 内置卡片标题统一

逐项检查 26 份卡片模板及对应 CardData：25 份结构化卡片均使用 system:title，其中 23 份使用可提升的顶层一级标题；奖池／奖品保留原二级标题同行数量／概率布局，纯文本模板仍不加标题。商品、订单、奖励、任务名称提升为卡片标题；签到、宿主、查询、说明书、候选操作、礼包和任务通知补充短标题。兑换确认、回执、任务提交／完成／解锁及礼包主句显式保留在正文；物品来源保留物品名与数量。HUD 固定“系统”标题及独立 SpeechContent 不变。

源码检查同时发现标题在组件和模式参数解析前读取，会令 Component 名称留下原始占位符、带模式条件的标题不生效。CardPresentation 调整为使用完整格式化 Context 后选择首个可见、非空、无 repeat 的一级标题，跳过隐藏标题继续查找；QuestNotice 提供按事件翻译的 notice_title。同步更新卡片编辑器的模板与双语快照。

实际执行 `gradlew compileJava processResources --console=plain` 成功。静态核对 26 份 JSON 无重复键、节点与翻译引用有效、25 份有标题且 23 份恰有一个顶层一级标题、确认／结果主句仍存在；中英文各 574 键且集合相同，编辑器内嵌模板／语言／CSS 与资源一致，JavaScript 语法与 `git diff --check` 通过。未运行测试、浏览器或 Minecraft；实际商品／任务名称的样式、HUD 高度、确认文案、条件标题及资源重载后的显示仍需游戏内验收。

## 2026-11-06 Card JSON 离线编辑器

新增 tools/card_editor.html 单文件工具：内嵌 26 份卡片模板、双语语言表与共享 CSS 快照，提供 23 种节点的属性／JSON 编辑、节点树、平铺示例 Context、模式切换、本地分页与折叠预览，以及卡片／工作文件导出、草稿恢复。字段依据 CardTemplate、CardContext、CardRenderer 与实际节点 Codec 核对；未知字段保留，未知节点只作提示。物品、玩家模型和 FTB 图标采用占位，通知按钮及业务调用仅模拟。

实际执行 JavaScript `node --check` 语法检查和 Python 静态核对：42 个 HTML ID 无重复、脚本引用的静态 ID 均存在、23 个节点 ID 与 SystemCardNodeTypes 一致、26 份内嵌模板及 CSS 与当前资源一致、中英文各 565 键且集合相同、文档本地链接存在，`git diff --check` 通过。HTML 约 219 KiB，没有外部脚本或资源标签；CSP 禁止连接和外部资源，预览文档不运行脚本。本次未运行测试、浏览器或 Minecraft，未调用 API；实际文件打开、表单与源码同步、导入导出、草稿恢复和交互布局仍未进行运行验收。

## 2026-11-06 物品来源分类与紧凑分页

在下方原物品来源卡片的基础上，将内容分成配方／掉落／标签三类，按查询结果计数，空类禁用，初始选择首个非空类。ItemSourcesNode 保留分类选择与各类页码，完整模式默认配方每页 4 条／两列，简约和通知最多 2 条／单列；掉落每页 12 条，标签每页 24 条。材料格缩至 36px，加工站并入标题，掉落压成行，标签压成流式小块，减少重复物品和说明。卡片 JSON 可独立配置三类分页容量，切换不发包或重新播报。

此前实际执行 `gradlew compileJava processResources --console=plain` 成功；静态检查 26 份卡片 JSON 的节点与翻译引用有效、中英文各 565 键且集合相同，`git diff --check` 通过。未运行测试、启动游戏或调用 API；实际分类切换、各类翻页／恢复、不同模式的密度和 tooltip 仍需游戏内验收。当前仅修改 master / 26.1.2。

## 2026-11-06 物品来源分页卡片

源码确认原物品来源工作流把完整 ItemSourceOutput 经 CodecIO.write 拼进消息正文。改为注册的 CardData 与 assets/system/cards/item_sources.json，客户端 ItemSourcesNode 复用 CardPagination，每页一条来源，只创建当前页材料 / 产物 / 加工站的物品节点及 tooltip；首尾与单页方向按钮置灰。物品槽改为 ItemStackTemplate 候选与独立数量，保留物品组件；有序 RecipeDisplay 的空槽、重复材料和宽度随服务端快照传输，原版尺寸补齐 3×3。无序、炉类、锻造、切石读取各自展示输入；其他类型回退 placementInfo。候选示例在 tooltip 显示，掉落权重不冒充概率，营火不提示消耗燃料。配方在回复截断前优先排序，空结果与 64 条上限均有提示。

核对实际 Minecraft 26.1.2 RecipeDisplay、PlacementInfo、ItemStackTemplate 及烹饪配方源码。实际执行 `gradlew compileJava processResources --console=plain` 成功；静态检查 26 份卡片 JSON 无重复键、节点与 translate 键均有效，中英文各 562 键且集合相同，git diff --check 无空白错误。没有运行测试、启动 Minecraft 或调用外部 API。完整 / 简约 / HUD 的实际布局、配方前后翻页与页码保持、物品组件和候选 tooltip、资源重载及大型整合包查询耗时仍待游戏内验收；目前仅修改 master / 26.1.2。

## 2026-11-06 共享三行分页网格

将 ActionsNode 的本地分页抽为 CardPagination，新增 CardGrid 统一 columns / max_row / cell 的扁平 MapCodec、真实列数与每页容量。任务、商品、奖池与奖品预览共用；完整模式 6 × 3 = 18 项，通知 / 简约格子最多 3 × 3 = 9 项。分页先切片再创建 DOM、物品与 tooltip，不再将全部货架节点塞进限高滚动区。空列表、少量结果和最后一页都通过固定轨道 / 高度保留三行，分页栏常驻，单页 / 首尾方向禁用按钮。保持原始回调下标，商品格子也复用忙碌 / 通知交互检查；切页不触发业务、发包或重新播报。CSS 移除任务网格的限高裁切与独立通知列数覆盖。

阅读 AUI 1.2.9 的 Element.init、Grid 轨道与边盒计算、Event 传播 API；捕获分页根节点前先初始化，避免插入时替换实例。实际执行 `gradlew compileJava processResources --console=plain` 成功；25 份卡片 JSON 可解析，中英文各 538 键且集合一致，git diff --check 无空白错误。没有运行测试、启动 Minecraft 或调用 API；实际三行高度、少量 / 最后一页不缩小、单页双按钮置灰、后页商品与任务动作下标、章节页码恢复、HUD 详情返回及不同主题仍待游戏内验收。服务端结果上限和网络协议未变；修改仅在 master / 26.1.2，1.21.1 待同步。

## 2026-11-06 多章节 FTBQ 开发任务集

对照 FTB Quests 26.1.2.8 的 BaseQuestFile、Chapter、ItemTask、ItemReward、TranslationManager / TranslationKey 和 ReloadCommand，新增 src/test-mod/ftbquests 下的 12 章 80 任务及双语 JSON5。覆盖 25 项三页列表、6 份礼包、手动消耗 / 混合 / 顺序 / 原生界面限定目标、一次解锁 12 条支线、单任务章节、不同奖励 / 图标、长标题 / 颜色代码与可见性受限说明书。固定 ID 使用 5A71 前缀；测试配置不进发布资源。显式 Python 安装器默认保留修改过的测试文件，两份全局文件始终只在缺失时创建。

实际执行静态检查：38 份 JSON5 均可解析，272 个对象 ID 唯一且为合法正 long，依赖均存在且无环，章节/任务中英标题与正文键、值类型一致，安装器语法可解析。执行安装器将 36 份新章节/翻译复制到 run-test-client/config/ftbquests/quests，保留现有 data.json5、chapter_groups.json5 和其他任务；比对已安装文件与源文件一致。未改 Java，本次未重新编译、运行测试或启动 Minecraft，未改变存档进度；FTB 实际加载、分页与通知等交互仍需按 [开发任务集](test-quests.md) 手动验收。

## 2026-11-06 图钉样式与任务详情固定入口

阅读 AUI 1.2.9 source JAR 的 Transform 及 t08-arwes 主题 CSS，确认支持 rotate / transform 过渡，且主题的最小高度、按下背景、聚焦外框与 hover translateY 会覆盖原图钉选择器。共享样式提高按钮状态的选择器优先级，固定 22px 尺寸、透明背景和无外框；16px SVG 未固定倾斜 35°、固定后转为竖直并着强调色。任务详情 JSON 复用 quest_pin，QuestOutput 只将已有 id 放入 Context，标题预留按钮空间，固定缓存与跨文档更新逻辑共用原实现。

实际执行 `gradlew compileJava processResources --console=plain` 成功，25 份卡片 JSON 可解析、中英文各 538 键且集合一致，静态确认详情图钉绑定 id，`git diff --check` 无空白错误。未运行测试或启动 Minecraft；需人工确认主题切换 / 聚焦 / 悬停后图钉尺寸与方向、列表与详情同步、详情标题避让及通知内详情布局。

## 2026-11-06 Markdown 卡片节点

阅读实际 AUI 1.2.9 source JAR 的 Markdown、DOM 文本节点和初始化 API，确认内置 Txtmark 扩展解析器可复用，但 renderSafe 不过滤所有事件属性或 URL。新增 system:markdown / MarkdownNode 和 AssistantMarkdown：解析结果经 JDK 被动 HTML 读取器，只以固定标签和纯文本重建展示；链接限 HTTP / HTTPS 并调用原版流程，图片只保留 alt，未知属性和脚本不会进入 AUI。AI 知识问答与联网答案通过原始 answer 参数使用 Markdown，FTBQ、来源原文和内置提示保留 text，不改变模型 API、历史 Codec 或语音内容。

实际执行 `gradlew compileJava processResources --console=plain` 成功；25 份卡片 JSON 可解析、中英文各 538 键且集合一致，静态确认两个答案模板只为原始 answer 启用 Markdown，`git diff --check` 无空白错误。问答 / 搜索提示词允许简短 Markdown，但仍要求返回 JSON。未运行测试、启动 Minecraft 或调用模型接口；标题 / 强调 / 嵌套列表 / 引用 / 围栏代码的布局、中文换行、带 & 的 URL / 代码、原版链接确认、HUD 交互及资源重载仍待游戏内验收。没有引入新依赖；不支持 GFM 表格、任务复选框或代码高亮。

## 2026-11-06 任务格子图钉与底边进度条

任务列表 JSON 新增 quest_pin / progress 节点；前者切换客户端连接内去重的任务 ID 列表、阻止点击冒泡并刷新同任务按钮，后者读取已同步的数字进度，在下边线上绘制 3px 横条。固定列表不展示、不影响原排序，系统状态清理时一并清空。核对实际 AUI 1.2.9 source JAR 的 Element.init、SVG、事件冒泡与 Document.getAll API，按钮在初始化后绑定，使用 SVG 避免 emoji 字形缺失；通知模式单独覆盖通用按钮最小宽度，避免图钉被撑大。

实际执行 `gradlew compileJava processResources --console=plain` 成功，25 份卡片 JSON 可解析，中英文各 538 键且集合相同，`git diff --check` 无空白错误。未运行测试、启动 Minecraft 或做游戏内验收；需人工确认任务图标 / 图钉互不遮挡、点击固定不进入详情、翻页 / 重开后保留状态、同任务跨文档同步、登出清理及 0 / 50 / 100 进度条长度。

## 2026-11-06 少量任务章节禁止滚动回弹

阅读实际 AUI 1.2.9 source JAR 的 MouseEvent.resolveScrollTarget、ScrollModel 与 Element.init：overflow:auto 只控制滚动资格，零滚动范围仍可被选为回退目标并产生弹性位移；原始 Element 初始化时不会复制内部监听。GroupedActionsNode 先初始化左栏，再注册内部 wheel 监听；仅在事件路径没有实际滚动范围时阻止默认滚动，保留超长左栏及外层聊天的真实滚动，Shift 滚轮兼顾横向范围。范围查询只在滚轮事件中进行，没有增加逐帧或布局回调测量。

实际执行 `gradlew compileJava processResources --console=plain` 成功，`git diff --check` 无空白错误。未运行测试、启动 Minecraft 或做游戏内验收。仍需人工确认单章节无回弹、多章节超高可滚动、外层聊天滚动及通知按 T 交互。修改位于 master / 26.1.2，1.21.1 待同步。

## 2026-11-06 任务图标边缘裁剪

根据用户截图检查任务格子的 CSS，确认 `.card_action_cell` 的 `overflow: hidden` 会裁掉 FTB 原生图标超出 32px 元素盒的边缘。保留格子尺寸与布局，只将任务格子、图标容器和原生图标元素改为 `overflow: visible`，避免方块 / 物品图标的透明边缘或立体模型被截断。

执行 `gradlew compileJava processResources --console=plain` 前一轮通过；本次仅修改 CSS 与文档，未启动游戏或做视觉验收。仍需确认不同 FTB 图标、滚动边界和通知模式下不会溢出任务列表外框。

## 2026-11-06 任务列表居中与原生 Quest 图标

阅读 FTB 26.1.2.8 的 QuestObjectBase.getIcon、ClientQuestFile.exists / getInstance 和 BaseQuestFile.getQuest，确认任务图标的配置优先级与客户端同步入口。新增通用 `system:quest_icon` 节点，使用结果附带的任务 ID，在隔离适配器中读取 Quest.getIcon，复用原生图标绘制与 tooltip；数据不可用时省略图标。任务格子保留 104px 高度，32px 图标和标题居中，编号 / 进度合为一行；带格式文本的内部块与 span 同时指定居中。

执行 `gradlew compileJava processResources --console=plain` 成功。未运行测试、启动游戏或做视觉验收；自定义物品 / 材质 / 动画图标、长标题居中与 tooltip、通知分页和任务文件尚未同步时的展示仍待游戏内验收。修改仅在 master / 26.1.2，1.21.1 待同步。

## 2026-11-06 任务列表货架布局修正

根据用户截图和源码阅读，旧任务格子把带格式的多行标签直接放在 flex 按钮中，标题没有独立块，且两列格子过宽。右侧改用商店 / 抽奖同一套货架 DOM 与 CSS，完整模式 6 列、104px 高，通知 3 列；标题、编号和进度由 JSON children 分块设置，分页仍复用 ActionsNode。左栏改为自然高度内容容器，只有超过高度上限才滚动，取消 gutter 预留。顶层 title 单独显示“任务列表”，提示缩短，保留原摘要播报。后续按截图移除右侧重复的章节名、数量及整行占位，任务货架直接与左侧导航顶部对齐。

实际执行 `gradlew compileJava processResources --console=plain` 成功；静态检查通过：25 份卡片 JSON 可解析，中英文各 536 键且集合相同，git diff --check 无空白错误。未运行测试、启动 Minecraft 或做视觉验收；仍需游戏内确认少量章节无滚动、超长章节滚动、格子标题换行、分页 / 分组切换及通知查看后返回。修改位于 master / 26.1.2，1.21.1 尚未同步。

## 2026-11-05 任务列表分组卡片

静态阅读 FTB 26.1.2.8 的 Chapter / ChapterGroup / BaseQuestFile 确认章节归属和顺序，读取 AUI 1.2.9 的 Element.init 确认回调容器需在插入前初始化。任务列表现在仅从本次结果生成分组；章节 ID 避免同名合并，左右区域分别滚动，右侧每组每页 10 项，选中章节与各组页码保留在卡片本地状态中。

实际执行 `gradlew compileJava processResources --console=plain` 成功；25 份卡片 JSON 可解析，中英文各 536 键且集合相同，git diff --check 无空白错误。未运行测试、启动游戏、创建存档或调用外部 API。仍需游戏内验收多章节 / 同名章节 / 空分组过滤、左右滚动、分页后原任务编号、HUD 点击详情后返回、窗口缩放及长标题换行。当前仅修改 master / 26.1.2，1.21.1 尚未同步。

## 2026-11-05 通知 HUD 鼠标透传

用户反馈背包内 FTBQ 左上角任务书偶发无法点击，当时通知已隐藏。阅读 AUI 1.2.9 的 Event / MouseEvent / Client 与 FTB Library 26.1.2.9 的侧栏实现，确认 AUI 在原版控件处理前全局分发输入，普通监听器会在回调执行前将事件视为已消费，不能靠回调内的条件判断实现透传。通知面板的鼠标监听改为内部监听，仅可交互状态显式 consumeNative；模板取消文档级自动拦截，HUD 指针规则固定优先于主题。鼠标按下、释放及界面滚轮事件以 HIGHEST 优先级重新检查实际界面，进入背包等非聊天界面时在 AUI 分发前移除残留通知文档。

执行 `gradlew compileJava processResources --console=plain` 通过。未运行测试或启动 Minecraft，未复现并确认此次 FTBQ 按钮故障的唯一原因；背包侧栏点击、通知动画期间透传、聊天栏内通知按钮与滚动仍需游戏内验收。

## 2026-11-05 AgentRequest / AgentResponse 流式分派

请求与响应只用于网络传输，接口的 `codec()` 改为 `streamCodec()`；`SystemRegistries` / `SystemResourceKeys` 的两张协议表改为 `Registry<StreamCodec<? super RegistryFriendlyByteBuf, ? extends ...>>` 并保留 `sync(true)`。根分派用 `ByteBufCodecs.registry(...).dispatch(..., streamCodec -> streamCodec)`，不再经过 `Codec`、`byNameCodec()` 或 `fromCodecWithRegistries` 的整包 NBT。C2S / S2C 载荷直接组合根 `STREAM_CODEC`；`ClientState`、`History`、`Message`、`CardReference`、`CardAction.Server` 新增流式编解码，枚举按序号编码，`AgentResult` / `AgentAction` 因同时用于存档与模型 JSON 仍保留 Codec，并在流式字段中桥接。

执行 `gradlew build testModClasses --console=plain` 通过，`git diff --check` 通过。未运行测试或启动 Minecraft；注册表数字 ID 同步、两端编解码一致性、请求 / 响应收发及未知 ID 的断线行为仍需游戏内验收。

## 2026-11-05 聊天记录底部多余滚动区域

用户截图与调试反馈显示，物品查询卡片已结束，下面仍可滚动到没有内容的区域，重新打开界面也存在。阅读当前 AUI 1.2.9 的 ScrollModel、Flex 和 Grid 源码：滚动范围按直接子元素的位置与整盒尺寸计算，原聊天包装容器还参与 flex 主轴高度分配。将 conversation 改为单列、max-content 行的网格，去掉包装容器的 flex 与自动上边距，让定位和滚动范围共用网格行的尺寸；safe end 在有剩余空间时靠下，溢出时从顶部排列。消息内部保留普通文档流，未增加手动 scrollHeight 修正或重复子树测量。

执行 `gradlew compileJava processResources --console=plain` 编译与资源处理通过；未运行测试或启动 Minecraft。本次按源码检查调整布局，实际底部留白、短对话对齐、长卡片滚动及工具栏切换仍需游戏内验收。

## 2026-11-04 重复包装检查

静态检查 Agent / assistant / module 的服务装配、工具适配与结果转换：抽奖回执改为由交易事务直接填充已有 sourcePool 字段，移除工作流的二次玩家存档读取、临时 LotteryPreviewInput 和第二份回执；商城报价与购买复用 AgentResult.data。语义路由复用 LexicalIndex.Match / Ranking，移除同字段 DTO 复制，保留可用模块过滤、候选排序及覆盖率 / 分差检查。带工具身份、计费、目录权限、玩家代次校验的适配继续保留。

执行 `gradlew compileJava testModClasses --console=plain` 通过，`git diff --check` 通过。testModClasses 仅做开发源集编译检查；未运行测试、启动 Minecraft 或请求收费 API。实际抽奖确认 / 再抽一次、商店展示、语义候选排序与低置信度回退尚未游戏内验收。

## 2026-11-04 Agent 与 MC 业务职责整理

AgentEngine 移除商城目录/报价、数据库、搜索和任务口播的业务所有权；具体工作流移入 module.builtin，游戏 Tool、绑定与交易移入 assistant，输入输出、存储实现、数据库与提示词移入 data，任务口播与背景缓存移入 speech.quest。新增 AgentJournal、AgentLifecycle 和 AssistantBindings；ModuleContext 显式注入服务。存储接口与执行账本契约留在 agent，具体业务存储由宿主注入。目录权限每次使用仍重新求值，搜索的数据库优先及本次联网许可由工作流维持。

执行 `gradlew compileJava testModClasses --console=plain` 与 `gradlew jar --console=plain` 编译、打包通过。静态检查覆盖旧包引用、Agent 对具体功能/游戏运行时类的依赖、Java 包路径、资源与语言键集合，以及 diff 格式。注册表 ID、Codec 字段、配置路径和协议 1 未改变；仅 Java 扩展的导入路径与构造 API 需调整。未运行测试、启动 Minecraft、创建存档或调用外部 API；实际绑定/撤销、商城/签到/抽奖、FTB、语音、客户端展示与专用服务端加载尚未运行验收。

## 2026-10-31 目录注册表、tag 与模块可选项

将签到奖励、商城商品和奖池登记为服务端专用的原生 system:signin_rewards / system:shop_offers / system:lottery_pools 数据包注册表；文件路径不变，删除 CatalogReloadListener 以及 AssistantServer 的目录监听器和逐 tick 刷新。CommerceCatalog 从 RegistryAccess 构建当前服务器的业务快照，条目禁用、原版 ItemStackTemplate 与 Saturn Cost 继续沿用。新增三张表各自的默认 tag、内置模块字段和商业示例包的追加 tag / 混合选择模块。

对实际依赖 Saturn 9349648 JAR 使用 javap 核对 keyOrTagList / matchesKeys / combineCodec，确认单值与数组格式、ResourceKey 编码不要求客户端持有服务端目录。阅读 MC 26.1.2.114 的 RegistryDataLoader、WorldLoader、MinecraftServer.reloadResources 和 TagLoader：Data Pack Registry 在世界加载时读取条目，普通 /reload 为现有注册表更新 tag，不重新读取条目。CatalogModule 使用 rewards / offers / pools 保存可选 ResourceKey/TagKey，同玩家已启用的同类配置取并集，所有业务入口统一读取 availableCatalog；成交前与实际支付前复核来源，成就固定奖励仍使用完整目录。修正同类型多个配置重复装配同一交易存储时被拒绝的问题。

执行 `gradlew compileJava --console=plain` 和 `gradlew testModClasses --console=plain` 编译通过，未运行测试、启动 Minecraft 或请求外部 API。原生数据包的实际世界加载、服务器专用注册表与客户端配置同步、tag 重载、模块条件切换及签到 / 商城 / 抽奖交互仍需游戏内验收。

## 2026-10-30 完整聊天布局与滚动

删除“对话”导航与侧栏分组标题，SETTINGS 类入口由 ClientModuleBindingsCache 放到右上角，模块工具栏移至聊天记录与输入框之间。WorkspaceContext 统一处理当前工具栏入口的重复点击，收起时跳过原打开回调，避免重复查询；即时查询和独立设置页面保持原动作。聊天内容放入普通文档流容器，通过 flex 自动上边距让短对话靠近输入框，长卡片不压缩。

阅读 AUI 26.1 / 1.2.6 的 Flex、Box 与 ScrollModel 源码确认自动边距、滚动范围和 setter 行为。AssistantScreen 在所属 Document 上下文中先提交布局，再按 scrollHeight 减 innerSize.height 计算范围；内容或工具栏变化前记录当前与目标位置，变化后仅在原本接近底部时跟随新消息，否则保留并限制位置到有效范围。

执行 `gradlew compileJava --console=plain` 编译通过，`git diff --check` 通过。未运行测试或启动 Minecraft；工具栏布局、重复点击、短卡片底部对齐及翻阅历史时的滚动仍需游戏内验收。

## 2026-10-30 客户端卡片构建扩展事件

新增 event.client.CardBuildEvent，使用当前 NeoForge bus 8.0.5 的 Event / NeoForge.EVENT_BUS API，提供 ref、原始 CardData 和可变 CardBuilder。将 ClientCards 的 SHOW、REPLACE 和无缓存回退构建收敛到同一个 build 方法，在内置 buildCard 后、不可变 CardView 构建前同步发布事件；历史恢复复用 SHOW 构建，REMOVE / 旧版本与缓存读取不发布。公共 CardBuilder / CardData 和网络 Codec 未改变；新增参数继续由 CardPresentation 统一处理 Component / CardText，然后供 JSON 使用。

执行 `gradlew compileJava --console=plain` 成功。没有运行测试、启动 Minecraft、添加临时探针或调用外部 API。其他模组的实际监听顺序、追加参数后的 GUI / 通知 / 历史显示、资源包重载和专用服务端加载仍需运行验收。文档补充客户端监听及资源包占位符示例，以及事件与缓存的生命周期约定。

## 2026-10-30 玩家模型节点展示参数

核对 AUI 26.1 / 1.2.6 的 Document.getMouseDocumentPosition、Base.toLocalPoint、RenderNode 变换流程，以及 Minecraft 26.1.2 的 MouseHandler.isMouseGrabbed、InventoryScreen 和 LivingEntityRenderer 源码。PlayerNode 新增 follow_mouse、scale、offset_x / offset_y，保留 yaw / pitch 并用于关闭跟随或光标锁定后的固定朝向；固定模式下头部水平旋转与身体对齐。内置 player.json 显式开启跟随并列出所有可调参数，节点缺省仍为固定展示。字段采用 lenientOptionalFieldOf，scale 使用原版 POSITIVE_FLOAT。

编译执行 `gradlew compileJava --console=plain` 成功；未启动 Minecraft、运行测试或请求外部 API。鼠标跟随、GUI 缩放 / 滚动 / CSS 变换、HUD 光标锁定后的回退、固定侧面 / 背面朝向，以及放大 / 偏移后的裁剪仍待游戏内验收。

## 2026-10-30 卡片缓存容错与存储异常

读取用户现有 `run-test-client/logs/latest.log`，确认登录时旧缓存的 `system:player` 卡片缺少新增的 `playerId`，RecordCodecBuilder 的部分结果触发 CardUpdate 构造异常，最终显示通用 MODULE_FAILED。只读核对了玩家文件的缓存类型与字段，没有删除或修改现有存档。

CardUpdate 改为按 mode 分派：SHOW / REPLACE 的 data 必填，缺失时返回 Codec 错误。新增 CodecIO.recoveringMap，仅对 Conversation.requests 逐项捕获解析错误及构造异常，丢弃失败项、保留有效结果，编码不容错。加载时删除无法解析的助手历史卡片，历史结构错误可回退为空；用户消息及严格读取的绑定、业务账本、请求指纹保持原逻辑。FileAgentStore 对 JSON / Codec 运行时异常补充 IOException 包装和错误链，读写失败通过原有日志与 STORAGE_UNAVAILABLE 提示，不覆盖为新的空玩家文件。

执行 `gradlew compileJava --console=plain` 编译通过。没有运行测试、启动 Minecraft、创建临时数据或调用模型 / 语音 API；旧缓存实际自动恢复、历史显示、异常文件的保留与业务防重复仍需运行验收。

## 2026-10-29 通知取消自动关闭与切换

阅读 AssistantMessages 确认原实现以 160 个可见客户端 tick 倒计时，到期 dismiss 并显示下一条，与 TTS 是否结束无关。删除倒计时、消息时长及仅用于暂停计时的按钮字段，可见阶段持续保留通知。确认、关闭、业务按钮的原位替换、服务端 REPLACE/REMOVE 和断线清理继续使用原有流程；中英文提示移除自动收起与暂停计时的描述，相关使用、界面和语音文档同步。

执行 `compileJava testModClasses --console=plain` 编译通过；未运行测试或启动 Minecraft。播报后的持续显示、鼠标/语音确认与队列接续尚未进行游戏内验收。

## 2026-10-29 通知按钮回复原位替换

阅读请求与 HUD 队列调用链确认，原逻辑把通知按钮的 SHOW 回复当作普通消息放到队尾，查看任务等操作会被既有通知阻挡。新增 CardButton.Behavior.REPLACE，服务端按钮默认使用；客户端 pending 记录 HUD 操作的来源 CardRef，直接回复只替换仍为当前项的来源通知，其他排队通知顺序保持。正文、底部和语音按钮统一进入 CardController，页面按钮不关联 HUD。替换沿用淡出/淡入并停止旧语音与录音；隐藏或资源重载时保留新内容。手动确认/关闭后迟到的回复不重新弹出；独立推送和静默业务更新保持各自原有语义。

执行 `compileJava testModClasses --console=plain` 编译通过；未运行测试或启动 Minecraft。通知按钮连续查看、排队顺序、确认后的迟到回复及动画/语音的实际交互尚未进行游戏内验收。

## 2026-10-29 任务完成状态改为标题标记

任务详情移除“已完成：是/否”一行，使用服务端提供的 completed 状态在标题前添加绿色 ✓ 或红色 ✗；任务名称原有颜色与样式保留。CardData 的标题参数与 summary 共用相同组件，沿用卡片现有摘要去重逻辑；SpeechContent 不包含状态符号。

执行 `compileJava testModClasses --console=plain` 编译通过，任务模板 JSON 解析和 `git diff --check` 通过；未运行测试或启动 Minecraft，颜色与布局尚未进行游戏内验收。

## 2026-10-29 查看任务的物品目标显示

阅读 FTB Quests 的 `ItemTask` 源码确认任务目标的物品堆可从 `getItemStack()` 读取，原实现只把 `Task.getTitle()` 写入 `QuestTaskOutput`，因此详情卡片中的“铁锭”只能显示为文字。现在任务输出额外携带 `task_items`，使用现有 `ItemStackTemplate` 和 `ItemsNode` 渲染目标图标及原版 tooltip；数量仍由目标进度文本显示，标签过滤或缺失物品无法生成图标时保留原标题。

`quest` 和 `quest_submit` 模板已统一使用物品节点，奖励和任务目标共用客户端物品渲染路径。执行 `compileJava testModClasses --console=plain` 编译通过，未启动 Minecraft，任务详情的实际视觉布局和 tooltip 尚未运行验收。

## 2026-10-29 重进世界后客户端模块入口恢复

只读检查现有存档确认系统绑定与模块存储仍保留。对照当前 NeoForge TagsUpdatedEvent 源码、Saturn RegistryCache 实现和本项目 ModDev 的 MC 26.1.2 补丁源码，确认共享静态缓存跳过单人连接的 ClientPacketReceived，世界加载的 disconnect 又会触发客户端登出清理；原 get 使用 RegistryAccess.EMPTY 惰性装配后不再纠正，造成已绑定但模块入口消失。

ClientModuleBindingsCache 改为仅按客户端标签同步和登出事件维护自身与命令缓存，包含单人连接，不再继承共享静态 RegistryCache。首次访问优先使用当前连接注册表，配置阶段已收到标签但本地玩家尚未创建时保留快照；连接注册表改变会按需重建。存档与服务端模块权限逻辑无需改动。

执行 `compileJava testModClasses --console=plain` 编译通过，`git diff --check` 无差异空白错误。未执行测试、启动 Minecraft 或修改现有存档；重新进入单人存档、远程重连和界面交互尚未实际运行验收。

## 2026-10-29 可提交任务列表与通知动作

`QuestQueryInput.view` 改为 ACTIVE / CLAIMABLE / SUBMITTABLE，新增 `/submittable [query]`、中文别名、只读语义样本和任务页入口。`FtbQuestReader.submittable` 排除未解锁、暂停、已完成、仅 FTB 界面操作及被目标顺序阻塞的项目；列表不预扣材料，不保证一次完成。详情与通知统一保存 `QuestSubmission` 枚举，由同一方法构建勾选或提交按钮；新增提交按钮的中英文语音别名。执行继续走原生提交并检查当前状态，查询不触发写操作。

阅读当前 FTB 反编译源码确认原生勾选、消耗物品、经验、自定义按钮及目标顺序语义。执行 `compileJava testModClasses --console=plain` 编译通过；未启动 Minecraft、未执行运行测试、未创建测试存档，按钮交互和语音匹配尚未实际验收。

## 2026-10-29 数据驱动模块与客户端分离

新增同步原生数据包注册表 system:system_module，SystemModule 的 server 按 ServerModule.CODEC / type dispatch，client 只传本地实现 ID。静态 SERVER_MODULE 改为 MapCodec 注册表，12 个内置实现使用 MapCodec.unit；SystemDefinition 中模块 ID/tag 的 Either 改为指向动态注册表。新 tag 路径为 tags/system/system_module，FTB 与搜索配置仅放 test-mod。

源码阅读确认：公共注册表不再引用 ClientModule，11 份 UI 实现拆成相邻的 *SystemClientModule。客户端独立 Map 保存实现，绑定缓存按 client ID 合并一次 UI，并以配置 ID 列表检查权限；纯本地贡献在无世界时可用。服务端安装、权限、补全、管理指令、存储枚举和 FTB 通知均读取新配置；同类型工作流与工具保存按 owner 区分的处理器，执行上下文保留已选 owner。ModuleStorage.owner 继续决定账本归属，tick 按真实 storage owner 去重。

执行 `compileJava testModClasses jar --console=plain` 编译与打包通过。未执行测试、未启动客户端或专用服务端、未创建测试存档、未调用外部 API；数据包加载同步、多个参数变体实际执行及客户端页面尚未做运行验收。下方专用服务端启动记录属于之前的联合实现版本。

## 2026-10-29 内置模块与存储包整理

将 15 个内置模块及 AdvancementTracker、ModuleManagementCommands 共 17 个类平铺到 `module.builtin`，将六个内置 DataStorage 实现统一放到 `module.builtin.storage`。模块接口、ModuleManager、ModuleData 等通用设施仍位于 `module`，客户端缓存与工作台契约仍位于 `module.client`；Agent 工作流和工具包保持原组织方式。同步注册表、Agent、FTB 桥接及权限服务的引用，并清理旧功能空目录。

执行 `compileJava testModClasses --console=plain` 编译通过；源码和增量编译产物均已使用新包路径，旧包名引用检查无残留。此次只调整包名、导入与文档，没有启动 Minecraft、执行运行测试、创建测试存档或调用收费 API。

## 2026-10-29 模块接口按职责细分

ServerModule 仅保留服务端生命周期、存储与依赖可用性；ToolModule 导出纯服务端工具，WorkflowModule 声明服务端工作流，SharedModule 组合双端功能及服务端游戏指令，CoreModule 表示必需基础设施。核心 / 绑定 / 用量改为 CoreModule，八个可选双端业务改为 SharedModule，成就改为 WorkflowModule，对话 / 设置 / 语音仍为 ClientModule。删除 required / serverRequired，权限、管理员指令和客户端上下文按接口类型判断；Workspace 复用上下文的权限判断，不另存分类布尔值。FTB 的 available 保留为依赖检测。

静态阅读确认：ModuleManager 在工作流前注册 ToolModule 导出工具及 owner，拒绝把同一个定义同时作为工具模块与工作流 / 客户端模块。纯工具只进入 ToolRegistry，不注册动作、命令或意图入口；消费工作流通过 ModuleContext.tool(type) 取得同一实例并声明工具白名单。AgentEngine.available 与 ExecutionContext.tool 检查导出工具所属功能权限，模块失效时路由、补全、在途工作流和排队游戏操作复用权限复核。原有预算、事件权限、幂等回执及工具身份检查继续执行。

执行 `compileJava testModClasses --console=plain` 编译通过。没有运行测试、启动 Minecraft、创建测试存档或调用收费 API；新增工具模块的实际装配与运行、客户端页面权限和本轮接口继承后的专用服务端类加载尚未运行验收。下方联合实现的两轮服务端记录属于上一轮代码验证。

## 2026-10-29 双端模块联合实现与专用服务端验证

核心、绑定、用量、玩家、物品、签到、收件箱、商城、抽奖、任务和搜索共 11 对模块合并为同一个 `*SystemModule` 实现 `ServerModule, ClientModule`，两张注册表分别注册无状态实例。接口删除 `id()`，装配直接查对应注册表；服务端 / 客户端游戏指令分别使用 `serverCommands` / `clientCommands`。客户端页面和动作放入静态内部类 `ClientHooks`，公共构造和静态初始化不调用客户端实现。成就仍仅服务端，对话、设置和语音仍仅客户端。

实际执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。按本次用户要求，使用隔离运行目录和 `127.0.0.1:25585` 启动两轮 NeoForge 专用服务端：两轮均从运行时类路径排除 ApricityUI 和 Simple Voice Chat；第一轮保留 FTB Quests / Library / Teams，第二轮再排除全部 FTB 依赖。两轮均到达 `Done`，且在 `/reload` 前后成功执行 `system module list`、`system routing`：12 个安全模块定义可列出，路由均为 READY，分别包含 136 / 112 条样例。

通过 JVM `-Xlog:class+load=info` 实际记录确认：`ClientModule` 接口和合并后的公共模块外层确实加载，两轮均未加载 `ClientHooks`、`AssistantClient`、`net.minecraft.client.*` 或 `com.sighs.*`。未安装 FTB 的运行也未加载 `compat.ftb` 适配器，任务定义仍可注册。未发现本次合并引起的类加载或引擎初始化错误，因此保留改动，无需回滚。

两轮均发送 `stop` 正常停止并完成保存，Gradle 以 `BUILD SUCCESSFUL` 结束；确认测试服务端进程退出、25585 和 25565 无监听，删除本次隔离测试存档的 `session.lock`。日志保留在 `run/module-server-check/with-ftb/logs/latest.log` 与 `run/module-server-check/without-ftb/logs/latest.log`，对应运行目录的 `class-load.log` 保存类加载证据，第二轮另有 `gradle-console.log`。首轮临时平坦世界配置缺少 layers 的提示属于测试世界生成配置，第二轮补全后正常加载。两轮新目录还记录 Jupiter 首次读取配置的 NoSuchFileException，随后均生成默认 `config/jupiter.json`，以及可选 AutoConfig 类缺失的 mixin 警告；这些提示没有阻止启动。

以上验证覆盖专用服务端启动、模块装配、控制台命令、资源重载与类加载隔离；没有启动客户端或让玩家入服，没有验收 GUI、绑定 / 领奖等玩家业务，也没有调用模型、搜索或语音 API。

## 2026-10-29 缓存数据与维护逻辑归位

阅读调用链后，将页面列表、本地卡片动作及登记/绑定/分派逻辑合入 ClientModuleBindingsCache；将命令声明、树编译、权限过滤补全及清理逻辑合入 ClientCommandCache。删除 ClientModuleBindings、ClientCommandRegistry 和 Commands 包装记录，缓存直接持有实际数据。绑定缓存接入 Saturn 生命周期并统一协调命令缓存，每轮只遍历客户端模块一次；完成后冻结页面/动作集合并释放临时 Builder，失败同时清空，首次使用仍支持惰性装配。界面异步补全改为核对命令缓存 revision，避免 Cache 本身不再换对象后漏掉失效检查。

执行 `compileJava testModClasses --console=plain` 编译通过；静态检查确认源码无旧类和 Commands 包装记录引用，`git diff --check` 通过。未运行测试或启动 Minecraft；标签重载、登出重连、本地按钮与异步补全的实际交互尚未游戏内验收。

## 2026-10-29 NLS 自动鉴权

阅读阿里云官方 HTTP / HTTPS 获取 Token 文档，按 POP 参数字典序、RFC 3986 编码、UTC 秒时间戳、唯一 nonce 和 HMAC-SHA1 实现 CreateToken，无新增依赖。Token 响应用 RecordCodecBuilder 解析，签发端点和 RegionId 固定上海；识别网关仍使用玩家所选地域。Token 跨 session 仅内存缓存，凭据指纹隔离，提前两分钟按需刷新，同步合并签发；握手 401 / 403 时仅在尚未发送识别命令前重取、重连一次。已提交的识别任务不自动重试。

静态阅读确认：本地校验不联网，签发沿用 HttpTransport 的响应上限、超时和中断取消；旧 session 仅能清除自己使用的 Token。表单移除临时 Token，改为 AccessKey ID / Secret，Secret 遮挡显示；配置 RecordCodecBuilder 全部使用 lenientOptionalFieldOf，三个凭据分别支持环境变量。配置和请求的 toString 不输出密钥，鉴权失败仅显示本地错误码。ASR 计量移至鉴权及握手后、首次识别命令前，签发失败不计 ASR 尝试。

执行 `compileJava testModClasses --console=plain` 编译通过；双语各 490 个语言键无重复且集合一致，ASR 默认 JSON 与七个 lenient 配置字段一致，设置表单 ID / 标签 / 密码控件及 ASR / API 文档本地链接检查通过，`git diff --check` 通过。未运行测试、启动 Minecraft、打开麦克风或请求真实鉴权 / 语音接口；云端签名受理、Token 到期刷新和实际转写尚未运行验收。

## 2026-10-29 客户端模块贡献的 RegistryCache 生命周期

阅读当前依赖 Saturn 文件 9349648 的 RegistryCache 字节码，并对照本地源码，确认构造即加入维护列表、shouldUpdateStaticData 的标签更新触发 rebuild、客户端退出触发 clear。新增客户端专用 ClientModuleBindingsCache，提前从 SystemClientMod 注册，首次读取可惰性装配；标签更新整体替换冻结快照、退出清空，无重复事件监听、计时器或逐 tick 扫描。同步保护重建和惰性初始化，发布完整实例后才供后续读取。

本地命令、页面声明和卡片动作属于同一快照。CardController 不再持有永久动作表，四个内置本地动作移入 Core / Settings 模块；模块通过 localAction 在每次装配时登记到新快照，避免重复注册和旧回调累积。Workspace 列表复用只读视图，玩家可用性继续实时读取，不缓存已打开的 Document。CardTemplates 移除初始化绑定的副作用，仍独立按客户端资源重载 JSON。文档同步更新模块装配、注册表速查与卡片动作流。

执行 `compileJava testModClasses --console=plain` 编译通过；静态检查确认四个内置本地动作均由模块提供，CardController 无永久 Map 或残留 register 调用，双语各 487 个语言键集合一致，`git diff --check` 通过。未运行测试或启动 Minecraft；标签重载、单人 / 多人重连、退出清理和按钮交互的实际事件时序尚未运行验收。

## 2026-10-28 ASR / TTS 工厂恢复普通 Map

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试、启动 Minecraft 或调用语音 API。

阅读代码与静态检查确认：两种供应商改用公开的 PROVIDERS Map，键为完整名称，值为 Supplier；create 继续支持 System 短名称并为每次会话创建独立实例。已移除 ASR / TTS 的资源键、内置注册表及 DeferredRegister 绑定，剩余 8 张注册表声明与登记一致。内置 ASR 键对齐默认配置的 aliyun-nls；文档链接与差异空白检查通过。实际合成、录音与第三方工厂接入未作运行验证。

## 2026-10-28 请求实现与 Codec 拆分

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试或启动 Minecraft。

阅读代码确认：四种请求拆到 `agent.request` 的独立文件，各自声明 MapCodec，SystemRequestTypes 只登记这些实例。AgentRequest 保留注册表分派与公共契约，发送调用及服务端 Handler 同步引用新类。ControlRequest 保留枚举语义，其 MapCodec 直接包含 action 字段；注册 ID、协议字段及网络版本保持不变。

## 2026-10-28 统一注册表声明与客户端实现隔离

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试或启动 Minecraft。

阅读代码确认：SystemResourceKeys / SystemRegistries 移除 Client 内部类，全部 10 张代码注册表在两端统一创建并登记。客户端模块、节点和语音工厂仍仅在 Dist.CLIENT 入口绑定，专用服务端保留空表；共用卡片载荷在公共入口登记，供服务端构建回复。核对当前 NeoForge 26.1.2.114 源码，RegistryBuilder 的 sync 默认 false，sync(true) 仅用于数字 ID 同步；本次沿用名称编码，不新增同步协议。专用服务端实际类加载及联机同步尚未游戏内验收。

## 2026-10-28 响应实现与 Codec 拆分

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试或启动 Minecraft。

阅读代码确认：四种响应拆到 `agent.response` 的独立文件，各自声明静态 MapCodec 并由 `codec()` 返回同一实例。SystemResponseTypes 只登记这些 Codec，AgentResponse 保留注册表分派入口；两端 Handler 和调用点同步引用新类。注册 ID、协议字段和网络版本保持不变。

## 2026-10-28 卡片节点、语音工厂与协议类型注册表

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；未执行测试或启动 Minecraft，未调用外部 API。

阅读代码确认：卡片节点、TTS 工厂、ASR 工厂与协议类型改为正式内置注册表，通过 `NewRegistryEvent` 和 `DeferredRegister` 注册，由模组加载流程冻结。协议类型包含卡片/动作载荷、Agent 请求/响应及任务查询结果，Codec 从注册表按名称分派；旧 PayloadRegistry、DispatchRegistry、CardNodeTypes 和语音工厂 Map 已删除。客户端注册表及绑定入口独立于公共入口，FTB 类型只依赖本模组 DTO，实际可选 API 仍按安装检测加载。完整条目与扩展方式见[注册表速查](registries.md)。

静态检查通过：22 个卡片 JSON 的 130 个节点引用均使用已登记的完整 `system:*` ID，中英文各 455 个语言键且集合相同，文档链接和差异空白有效。网络协议仍为 `1`，未增加依赖。注册生命周期、专用服务端隔离、可选依赖缺席、资源重载和第三方扩展尚未游戏内验收。

## 2026-10-28 两端模块内置注册表

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试或启动 Minecraft。

源码核对确认：功能定义由 `ServerModule` / `ClientModule` 两个正式内置注册表管理，使用 `NewRegistryEvent` 和 `DeferredRegister`。旧模块 Map 目录与两种自定义模块注册事件已删除，模块 ID 从实际注册表取得。ModuleManager 从服务端注册表创建每服务器的工作流和运行时；ClientModuleBindings 从客户端注册表一次装配展示贡献。客户端入口、注册表及注册供应商位于只在物理客户端加载的类中；FTB 服务端条目仍按安装检测条件注册。实际注册加载、专用服务端隔离、可选依赖缺席和第三方模块扩展未在游戏中验收。

## 2026-10-28 模块权限、数据条件与动态侧栏

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；没有执行测试、启动 Minecraft、创建存档或调用外部 API。

阅读代码确认：完整侧栏的 10 个入口从 ClientModule.workspace 动态生成，按 CHAT/MODULES/SETTINGS 分组；SystemDefinition.modules 支持 ID 简写、带 condition 的定义及 Saturn 实体条件。ModuleAccess 在主线程求值并发布按玩家隔离的不可变模块快照，Agent 命令/帮助、本地语义候选与缓存、模型目录、执行器及游戏工具入口共享权限检查。FTB 通知与任务口播入口也检查任务功能开关。

Saturn 新增 add_module/remove_module 实体动作及 has_module/has_system 实体条件；Java 主线程 API 使用同一持久覆盖服务，覆盖由 `system:module_access` DataStorage 保存于玩家文件，重登保留，成功 revoke/reset 随玩家文件清除。核心、绑定和独立用量保留为基础设施。开发源集的默认定义单独启用 FTB，发布默认定义只包含必装业务模块。

静态检查通过：46 个资源 JSON 语法及键唯一性有效，双语各 454 个键且集合相同，动态导航 ID 唯一、默认模块 ID 有对应注册定义，可选 FTB 定义未进入主资源，更新文档链接与 diff 空白检查通过。README-zh.md 和原有 CommerceCosts 修改未调整。实际三分类布局、条件变化后的同步、Saturn 顺序动作、覆盖重登持久化、撤销清理、可选依赖缺席与任务通知尚未游戏内验收；编译与阅读结论不代表这些场景已实测。

## 2026-10-28 节点接口与独立实现

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试或启动 Minecraft，未调用外部 API。

源码检查确认：根接口 `CardNode` 直接定义 codec/render/css/text，并通过 dispatchMap 按 type 分派；CardNodeTypes 仅保留 ID 到 MapCodec 的注册、冻结和错误处理。旧 Type/Part 泛型包装、函数处理器注册和集中式 CardNodes 已移除。10 种节点分别位于 node 子包，文字和容器派生类复用实现；递归子节点使用延迟 Codec，通用样式 Codec 放在根接口，避免模板与嵌套定义之间的静态初始化引用环。

现有 JSON 格式、Context 三张表、通知的代码按钮及回调入口保留。嵌套节点加载、实际界面渲染、通知交互和资源重载尚未作游戏内验收；编译结论不代表这些行为已实测通过。本次未修改 README-zh.md。

## 2026-10-28 平铺 Context、回调与小写占位符

当前卡片 Context 简化为 `Object2DoubleMap<String> states`、`Map<String, Object> parameters` 和 `Map<String, Runnable> callbacks`。源码检查确认 get(id, clazz) 先判断实际类型，不符或缺失返回 null；Builder 构建后复制三张表。整卡共用一个 Context，循环只携带下标，使用平铺键读取参数，不再创建子 Context 或作用域覆盖。

动作按钮已改用 callbacks 表，另有通用 button 节点；未找到回调时禁用按钮。通知仍跳过 actions/button，确认等操作继续由代码生成。模板 ID 全部使用小写下划线、占位符仅支持双花括号，已同步所有内置模板与展示字段翻译键。

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；未运行测试或启动 Minecraft，未请求外部 API。静态检查通过：45 个资源 JSON 无重复键、22 个模板/130 个节点/167 处小写双花括号引用及循环下标深度合法，双语各 451 个键且集合相同，文档链接和差异空白有效。按钮实际点击、HUD 布局、队列及资源重载未作游戏内验收。本次未修改 README-zh.md。

## 2026-10-28 卡片参数与布局分离

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 不执行测试。没有启动 Minecraft、运行测试、打开麦克风或请求外部 API。

阅读代码确认：每个卡片输出只注册一个 `CardContext.Builder` 填充函数；文字、嵌套列表、物品与动作标签参数统一构建，`build()` 复制 Map/List 为不可变 `CardContext`，之后才交给 `CardTemplate.build` 绑定布局。Builder 可复用，列表条目和动作参数均保存不可变子上下文。旧 CardVariables、分开的 variables/items/actions 注册以及模块中的物品展示分组已删除。奖池概率说明、签到候选、任务奖励和新手礼包的分组由 JSON 决定；通知按钮与任务选择行继续由代码生成。

静态检查：主资源和开发资源共 45 个 JSON 文件语法、键唯一性通过；22 个卡片模板、130 个节点的嵌套作用域、占位符、集合引用和翻译参数通过检查，中英文各 451 个键且集合一致。未增加依赖或修改网络协议；本次未修改 README-zh.md。

尚未游戏内验收：奖励与候选分组的实际排版、图标 tooltip、动作标签及通知操作、资源重载与队列快照。编译和静态检查不代表这些场景已实际验证。

## 2026-10-28 卡片节点类型分派与通知主体

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与发布打包通过；未运行测试或启动 Minecraft，未打开麦克风或请求收费 API。

阅读代码确认：`CardNodeTypes` 用 Map 绑定节点 ID、MapCodec、DOM 与文本处理器，递归 `dispatchMap("type", ...)` 统一解析 9 种布局节点。物品通过 `${RESULT_ITEMS}` 等名称直接绑定 Java 集合，可在正文树任意位置展示；商城分别提供商品及价格物品来源。完整/简约界面和通知主体共用布局树，通知模式跳过所有动作区节点，确认/关闭/领取及任务选择行继续由代码生成。入队消息保存不可变模板与数据快照，重载不重播；纯文本和口播入口保持独立。

样式字段统一为 `style/container_style/button_style`，Codec 同时接受单个字符串和字符串数组，数组以空格拼接。内置资源同时包含两种写法，不兼容之前未发布的 class 字段或无 type 模板。

静态检查：主资源与开发资源共 45 个 JSON 文件语法和键唯一性通过；22 种模板与结果类型 ID 一致，119 个节点、102 处文字占位符、45 处集合绑定及条件/列表作用域、翻译参数均检查通过。样式共 12 处单字符串、3 处数组，字段与节点 schema 合法；中英文各 451 个键且集合相同。页面 DOM ID、Java 包路径、文档本地链接和差异空白检查通过。发布 JAR 为 1,167,640 字节，包含当前模板及全部解析器类，未增加依赖、native、模型或开发数据包，协议保持 1；未修改 README-zh.md。

尚未游戏内验收：嵌套横排/竖排和长集合布局、完整/简约界面选项编号、HUD 实际排版与物品悬停、按 T 交互、通知切换/动画/语音取消、资源覆盖和未知 type 回退。编译及静态检查不代表这些行为已经实测通过。

## 2026-10-28 客户端 JSON 卡片

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译和发布打包通过；`testModClasses` 只编译开发源集，不执行测试。没有启动 Minecraft、运行测试、创建存档、打开麦克风或调用模型 / TTS / ASR 接口。另读取了 KOOK 卡片消息公开文档作为结构设计参考。

阅读代码确认：22 种内置卡片按数据类型 ID 选择客户端 assets 模板；递归 `CardTemplate.CODEC` 和通用 `CardRenderer` 处理占位符、翻译参数、条件、循环、容器及按钮标签。`ClientModule` 只提供类型化变量，物品和可信操作继续共用原入口。客户端资源重载后完整/简约界面按模板版本刷新，无效文件依次回退到低优先级资源包和上次有效版本；通知保存入队时的文本快照，不因重载重复播报。变量替换只执行一遍，文字不会作为 HTML 或脚本执行。

静态检查：主资源与开发资源共 45 个 JSON 文件语法及键唯一性通过；22 个模板与内置卡片 ID 集合一致，74 个节点的字段、102 处占位符引用、条件及列表作用域、CSS 类和翻译参数检查通过；双语各 451 个键且集合一致。Java 包路径、修改文档的本地链接、差异空白检查通过。发布 JAR 包含全部模板与解析器类，未引入 native、模型或开发数据包，网络协议保持 `1`。本次未修改 `README-zh.md`。

尚未作游戏内验收：首次资源加载、资源包覆盖与错误文件回退、F3+T 后已有卡片刷新、长列表和嵌套列表排版、物品 tooltip、按钮操作及通知显示。编译与静态检查不代表这些场景已实测通过。

## 2026-10-28 功能模块化

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与发布打包通过；`testModClasses` 只构建开发源集，不执行测试。发布 JAR 为 1,116,233 字节，低于 10 MB。没有启动 Minecraft、运行测试、创建存档、打开麦克风或调用外部 API。

阅读代码确认：`SystemModule` 统一贡献功能工作流、平台指令和安装钩子，`ClientModule` 贡献本地指令、页面绑定、卡片、物品、通知与口播；Agent 工作流继续位于 `com.iafenvoy.system.agent.builtin/<功能>`。共享工具由每台服务器的 `ModuleContext` 缓存实例。成就基线、登录事件缓冲及派发移入成就模块，交付/订单恢复由收件箱模块登记。通用会话只在文件事务成功且连接代次有效时激活功能，退出、重置和服务端停止时关闭功能会话。

任务列表布局、新手礼包静默刷新、详情按钮行为和通知摘要通过注册表贡献；`AgentResult.silent` 是显式 Codec 字段。状态同步已安装功能 ID 列表，客户端据此控制导航；系统定义中的预留模块字段仍不启停功能，网络协议保持 `1`。

静态检查：中英文各 451 个语言键，无重复且集合一致；Java 包与路径一致；工作台绑定的模板元素均存在；修改文档的本地链接及 `git diff --check` 通过。发布 JAR 包含新注册表与功能类，不含旧顶层工作流、旧安装器、开发数据包、native 或 ONNX 模型。公共功能定义没有客户端/AUI 导入；FTB API 工厂仅在依赖检测后反射加载。本次未修改 `README-zh.md`。

尚未运行验收：两端指令树合并与实际补全、未安装 FTB 的专用服务端加载、外部扩展事件、切换存档后的模块重新装配、登录期间成就触发、绑定/撤销/重置、HUD 连续消息和礼包刷新、物品悬停与口播。编译和静态检查不代表这些场景已经实测通过。

## 2026-10-27 补全面板定位

执行 `compileJava --console=plain` 与 `processResources --console=plain`，主源码编译和资源处理通过；没有运行测试或启动 Minecraft。阅读当前 AUI 1.2.6 依赖源码确认，尺寸计算依赖文档视口上下文，DOM 几何查询会填充布局缓存。简约面板改为水平居中、底边 `6vh` 的 fixed 定位；完整与简约界面的补全节点移到 body 下，独立浮层不再嵌套于输入区。候选重建先隐藏，在渲染阶段按输入框边界设置位置、宽度和可用高度；几何查询与 IME 热区同步显式进入对应文档上下文。静态检查模板节点、共享样式和差异空白通过；补全开合、方向键切换、不同 GUI 缩放及输入法候选仍待游戏内验收。

## 2026-10-27 文本格式作用域

执行 `compileJava --console=plain`，主源码编译通过；没有运行测试或启动 Minecraft。阅读当前原版 `StringDecomposer` 源码确认其格式状态会跨换行保留，简约模式拼接多条任务后会把上一条的颜色和粗体延续到后续编号及文本。共享 `AssistantText` 改为每个显式换行从空样式开始解析，保留空行和 Unicode；宽度引起的自动折行仍保留当前格式。任务标题、章节及目标、奖励名称与进度等字段拼接时补充重置边界，HUD 与完整/简约界面复用同一处理。转义与号保留到渲染边界才归一化，避免重复转换。相关文件的差异空白检查通过；实际颜色和排版尚未作游戏内验收。

## 2026-10-27 完整界面对话消息占位

执行 `compileJava --console=plain`，主源码编译通过；没有运行测试或启动 Minecraft。根据截图中的长任务卡片与后续宿主消息位置，阅读 AUI 布局源码后，将对话滚动容器及消息主体改为普通块布局，以内容高度占位；保留滚动容器作为主界面的 flex 子项分配可用空间。页面切换时的内联 display 同步改为 block，消息及正文间距用外边距保留。实际长卡片、缩放和滚动位置尚未作游戏内验收。

## 2026-10-27 FTB 与号格式码

执行 `compileJava --console=plain`，主源码编译通过；没有运行测试或启动 Minecraft。阅读当前 FTB Quests 与 FTB Library 源码确认：服务端任务标题为原始字符串，FTB 客户端另行解析 `&` 格式；此前界面仅识别 `§`，因此截图中的 `&6`、`&9` 和 `&r` 直接显示。新增共享 `LegacyText` 转换有效与号格式码，再由既有原版解析和 AUI 样式节点显示；TTS 同步使用原版纯文本提取。普通与号、未知码和转义与号保留。通知、任务列表与详情的实际颜色和播报尚未作游戏内验收。

## 2026-10-27 FTB 完整滚动任务列表

执行 `compileJava --console=plain`，主源码编译通过；没有运行测试或启动 Minecraft。静态检查确认进行中任务、可领奖任务和重名候选一次返回全部匹配简表，不再生成“下一页”动作。编号从 1 连续递增，查看与提交共用玩家隔离的完整列表快照，不再限制为前 8 项。通知和完整界面使用共享 CSS 的限高滚动区域，简约模式继续在当前回复区域滚动。

阅读当前 NeoForge 依赖源码确认其支持大包拆分。移除回复的 60000 字符拦截，服务端生成的回复使用 trusted S2C Codec，避免完整列表超出默认 NBT 分配预算；C2S 解码保持原有限制。完整列表造成玩家文件超过容量时，仅淘汰整轮对话及其回放缓存，不删除奖励、订单或操作记录。协议更新为 `7`，客户端和服务端需同步更新。大量任务的滚动、点击与联机传输尚未作游戏内验收。

## 2026-10-27 系统定义数据包表

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有运行测试或启动 Minecraft。核对当前 NeoForge 依赖源码后，以 `DataPackRegistryEvent.NewRegistry` 注册 `system:system`，使用同一 Codec 加载并同步 `SystemDefinition`。静态检查确认内置 `system:default` 位于原生目录，名称与介绍均引用双语翻译键；绑定标题/正文/播报分别使用 1/3/2 个参数，两种语言数量一致，中英文各 449 个语言键且集合一致，`git diff --check` 通过。

阅读代码确认界面和通知从当前连接读取定义，不缓存上一个世界的内容；模块列表只保存 `Identifier`，不解析可选依赖类、不限制执行权限，定义表未进入玩家存档。原生注册表需重开世界或重启服务器读取，奖励与提示词仍保留原有 `/reload`。尚未作联机同步、资源包翻译、自定义名称排版和绑定播报的游戏内验收。

## 2026-10-27 FTB 任务读取与手动提交判定

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；没有运行测试或启动 Minecraft。阅读当前 FTB 依赖源码确认物品消耗配置、勾选、经验、自定义按钮与专用资源输入的提交行为；`FtbQuestReader` 统一章节遍历和读取规则，查询、详情、礼包、通知及播报均复用该入口。分别提供可执行任务、全部可见未完成礼包、当前可领取礼包、待领奖励和手动提交任务视图；通知基线保留完成项，避免重置冒充解锁。

手动提交按整条任务的全部目标执行 `anyMatch`，任意一个需要手动处理的目标即满足，不以当前详情页或目标是否已经完成为限。显式命令提交支持的原生手动目标，保留暂停、前置、顺序及重复任务单轮限制；击杀、观察等自动目标不会被提交，查询和通知不发起提交。中英文各 447 个语言键且集合一致，`git diff --check` 通过。该判定与提交范围取代下方历史记录中的仅消耗物品限制；专用输入、混合任务、部分提交和通知实际行为仍待游戏内验收。

## 2026-10-27 绑定引导顺序与触发范围

执行 `compileJava --console=plain`，编译通过；未运行测试或启动 Minecraft。阅读代码确认只有成功的新绑定会标记一次引导，绑定回包先发送，等通知初始化就绪后依次加入可领取的新手礼包、当前任务概览；空内容跳过。普通登录与重复授予不设置标记，登出、撤销和重置清除待发送标记，重新绑定可再次触发。任务完成和新解锁通知保持原有事件路径，`git diff --check` 通过。该行为取代下方历史记录中的每次登录启动概览；队列实际显示顺序尚未作游戏内验收。

## 2026-10-27 任务播报与显式物品提交

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过，未运行测试。阅读 FTB 依赖源码确认 `ItemTask.isOnlyFromCrafting`、消耗提交、仅限任务界面、任务顺序、暂停及自动领奖后的重复任务重置语义。静态检查确认：任务要求和奖励默认播报、描述默认关闭，客户端三个 lenient 配置字段与统一设置页绑定；常见类型直接格式化，不认识的任务/奖励一次批量整理，按源文本片段、语言、模型地址/名称和提示词缓存，重复内容及启动列表顺序变化可复用。同批次并发合并，失败短期抑制重试，实际模型调用仍经过 ExecutionContext 和 ModelGateway 计量。

任务播报独立载荷只接收任务 ID 与开关，主线程重新验证可见性和描述隐藏条件，不传客户端任意文案给模型；只在当前允许朗读时请求，取消/超时后旧回包不能重新播放。新增 `submit/提交/提交任务` 注册指令只接受明确入口，不进入模型/语义自动选择；与详情共享列表编号和名称定位，原生提交保留前置、暂停、顺序与消耗，一次指令在本轮完成或自动重置时停止。查询和播报路径没有提交调用。

中英文各 445 个语言键且集合一致，11 个通知语音按钮定义的文案齐全，默认 JSON 与新增提示词资源已检查，`git diff --check` 通过。当前未发布版本的网络协议统一为 1，两端需一起更新。没有运行测试、启动 Minecraft、调用模型/TTS 或操作背包；长任务口播、缓存命中与配额、通知切换、特殊任务、重名/过期编号、部分提交、顺序和重复任务仍待游戏内验收。

## 2026-10-27 FTB 启动概览与新手礼包

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未执行测试。阅读当前 FTB 源码确认勾选提交、`withPlayerContext`、队伍领奖记录与暂停语义；静态检查确认首次绑定/每次登录在初始化完成后发送一条普通任务概览及可领取礼包，同连接重复打开界面或任务文件重载不重复启动提示。介绍任务统一从查询、详情及通知过滤；非重复的原生勾选物品任务转为礼包卡片，显式确认后提交任务并领取 FTB 原奖励，不新建独立奖励账本。

礼包引用核对玩家、队伍、任务/奖励对象及完成时间，撤销清理；重复领取读取 FTB 已领取记录，过期可查看刷新，部分领取不报全量成功。物品预览、通知队列、语音按钮及口播使用类型化结果和共享渲染。中英文各 420 个键且集合一致，11 个通知语音按钮定义均有双语文案，`git diff --check` 通过。没有运行测试、启动 Minecraft、创建存档或调用外部 API；首次绑定/重登通知顺序、礼包前置与暂停、队伍/自动奖励、过期与重复点击尚未作游戏内验收。

## 2026-10-27 奖池与签到物品展示统一

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过，未执行测试。静态检查确认奖池列表携带首个 8 项奖品页的类型化预览，概率分母使用完整奖池总权重，预览数量按奖励包计数；详情顶部不重复奖品，原有分页和独立确认保留。物品消耗改用小卡片，免费及系统币金额保留文字，简约模式与通知省去重复的物品文字列表；签到候选按物品卡片显示所属奖励包名称，签到结果、收件箱和商城复用原有物品投影。

中英文各 409 个键且集合一致，`git diff --check` 通过。没有启动 Minecraft、运行测试或调用外部接口；多物品奖励包、超过一页的奖池、不同支付类型及通知/简约模式布局尚未作游戏内验收。

## 2026-10-27 物品尺寸与奖励布局

执行 `compileJava testModClasses --console=plain` 编译通过，未执行测试。阅读 AUI 1.2.6 源码确认独立物品节点默认按 16px 模型绘制；共享 CSS 对原生节点缩放 1.75 倍，外层预留 40px。静态检查确认任务详情只显示“奖励：”标题，下一行排列物品小卡片，领取状态移入卡片，简约模式和通知省去对应的重复文字；非物品及自选奖励保留信息与原操作。`git diff --check` 通过，没有启动 Minecraft，实际尺寸、换行和悬停范围尚未作游戏内验收。

## 2026-10-27 通知与卡片物品预览

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有执行测试。静态检查确认：物品预览通过类型分派提取，卡片、简约模式及 HUD 共用 AUI 原生物品节点；悬停通过原版 `GuiGraphicsExtractor` 提交 tooltip，HUD 仅在按 T 进入聊天且通知完整可见时响应。奖励与支付材料分别分组，空区域隐藏，通知物品容器限制高度并允许交互时滚动。

FTB 普通奖励模板保留组件，数量与随机范围单独传输；详情自选项保持原分页和领奖索引，通知不展开整个自选奖池。核心载荷没有 FTB 类引用，隐藏和被阻止的顶层奖励不进入预览。中英文各 408 个键且集合一致，`git diff --check` 通过。

没有启动 Minecraft 或调用外部 API。待游戏内验收：自定义名称/附魔/耐久、F3+H 与其他模组 tooltip、物品裁剪与不同 GUI 缩放；多物品换行/滚动、商城支付分组、FTB 大数量与随机范围/自选项；连续通知淡入淡出、确认和隐藏后的 tooltip 消失，以及聊天栏输入法与按钮交互。

## 2026-10-27 通知语音操作与按钮向量

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认：语音键在游戏与聊天栏优先选择可见通知，录音及识别期间暂停计时；按钮命中与鼠标共用执行入口，低分、歧义或未命中走主聊天路由，忙碌操作保留提示。通知隐藏、替换、确认和连接变化会取消所属 session，按键重复不会开始录入下一条通知，最终结果不能操作下一条消息。

TF-IDF 核心抽为 `LexicalIndex<T>`，服务端模块路由保留原领域别名，通知向量保留不同动作原词；按钮例句通过客户端资源重载读取，按文案键扩展。FTB 完成通知按玩家生成可领奖励动作，唯一普通奖励使用现有短期引用和领奖工具，多项/自选奖励保留选择流程。中英文各 408 个键且集合一致，8 个按钮例句定义均有双语文案，`git diff --check` 通过。

没有运行测试、启动 Minecraft、打开麦克风或调用收费接口；实际通知动画、键鼠重绑、录音设备、资源重载、识别效果与 FTB 交付尚未作游戏内验收。

## 2026-10-27 两端运行时职责拆分

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认：`AssistantServer` 的玩家会话、请求分派和用量计量分别交由 `AssistantPlayers`、`AssistantRequests`、`AssistantUsage`，命令、网络及 FTB 桥接调用已同步；异步回复的连接代次校验和计量的主线程写入保留。

客户端对话、请求和服务端元数据归入不可变 `AssistantClientState`，所有界面改从状态快照读取；登出或成功撤销时替换为 `EMPTY`，刷新版本号独立递增。同步修正设置页把 ASR 地区 `Set` 当作列表使用的问题，地区切换从排序快照读取。`git diff --check` 通过；没有运行测试、启动 Minecraft 或调用外部 API，绑定、登出、异步回复与界面刷新尚未作游戏内验收。

## 2026-10-27 全局配置直接读取

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态扫描确认：引擎、执行上下文、模型客户端、语义路由和服务端桥接不再持有配置对象字段，不再传递全局配置构造参数或通过 `settings()` 转发；使用 `XxxConfig.INSTANCE.get()` 直接读取。路由缓存不再保存容量和 TTL 的配置副本，写入时按当前限制淘汰。

设置页只保留未提交草稿，配置提交仍使用 `INSTANCE.set()` 的脏标记机制。TTS 等待队列不再保存配置；TTS / ASR 单次请求保留不可变报文与计量维度，模型在配置实例锁内一起读取地址、鉴权和请求体，HTTP 在锁外执行。词法索引与模型并发信号量仍在启动时构建，相关结构参数修改后需重启。`git diff --check` 通过；没有运行测试、启动 Minecraft 或调用外部 API，资源重载和在途请求尚未作运行验收。

## 2026-10-26 抽奖界面布局

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认中英文各 406 个语言键且集合一致，`git diff --check` 通过。

阅读样式和渲染代码确认：抽奖与商城搜索框伸展占满剩余宽度，按钮等高，窄窗口换行；奖池卡片按名称、奖品种类、ID 和单次消耗分层，奖品卡片突出名称、物品数量与中奖概率。卡片正文通过类型注册渲染，简约模式使用同一套紧凑文本格式；原有可信动作、独立报价与确认流程保留。没有运行测试或启动 Minecraft，实际排版、输入法、滚动和不同 GUI 缩放尚未作游戏内验收。

## 2026-10-26 通知首次展开提示音

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。阅读通知状态机确认：提示音在 `HIDDEN` → `EXPANDING` 时提交一次，连续消息的淡出淡入和收缩中途反向展开只提交正文播报，完全收起后下一轮才重新播放提示音；恢复通知、文档重建不补播。提示音与正文仍共用 session，音量维持播报音量的 75%，切换、关闭和隐藏时取消。

没有运行测试或启动 Minecraft，实际连续播放、音量和动画时序尚未作游戏内验收。

## 2026-10-26 玩家存档读取句柄

读取现有 `run-test-client/logs/latest.log` 确认登录时出现 baseline 失败，打开助手后显示存储不可用；原实现没有记录底层异常，日志无法直接确认具体 I/O 错误。本次使用的玩家文件只有合法的 `{"bound":true}`，没有旧订单或缓存卡片字段。阅读源码发现 `FileAgentStore.load()` 直接创建 `FileReader` 后未关闭，读取句柄可能在 Windows 下阻止同一事务随后原子替换原文件。

改用 UTF-8 `Files.newBufferedReader` 和 try-with-resources，确保读完或解析失败时都关闭文件；存储 I/O 和目录枚举失败统一记录操作、路径及原因，并在 `AgentException` 中保留 cause，网络只返回原错误码。未修改或清理任何玩家存档，未增加格式兼容或解析失败后的空数据兜底。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查与 `git diff --check` 通过。没有运行测试或启动 Minecraft，原子替换与反复打开助手尚未作运行验收。

## 2026-10-26 HUD 通知提示音

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过；没有运行测试或启动 Minecraft。使用 FFmpeg 将提供的 `1.mp3` 转为 Ogg Vorbis，FFprobe 确认结果为单声道、44.1 kHz、约 1.78 秒、16,584 字节。静态检查确认中英文各 400 个语言键且集合一致，声音定义与字幕键、OGG 路径可对应，`git diff --check` 通过；发布 JAR 包含 `sounds.json`、OGG、`SystemSounds` 与 `NotificationSound`，大小为 861,806 字节。

阅读本地 Minecraft / NeoForge 依赖源码确认注册事件、原版声音实例和静态/流式启动回调签名。阅读调用链确认：HUD 首次渲染提交当前 session 的提示音，按播报音量的 75% 使用 Voice 分类播放；云端合成并行准备，原版声音实例结束后才启动正文播放。通知关闭、确认、切换、隐藏和声音重载复用 session 取消，声音线程的延迟启动回调也检查取消状态；恢复通知、文档刷新不补播。提示音不依赖云端密钥或自动朗读，不增加 API 用量。没有调用外部语音 API；实际音量、播放顺序、通知连续切换与取消尚未进行游戏内验收。

## 2026-10-26 数据驱动抽奖

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认中英文各 399 个语言键且集合一致，10 份内置及示例商业目录 JSON 可解析，抽奖界面元素 ID 无重复且与 Java 引用一致，抽奖模块语言键齐全，`git diff --check` 通过。

阅读代码确认：奖池从 `system/lottery_pools` 资源加载，使用 Codec 定义奖品、正整数权重及可选消耗；默认池消耗 1 个绿宝石，三个奖品权重为 60/30/10。浏览和预览接入模块注册表、指令与语义路由，单抽只接受独立结构化确认。商城与抽奖共用报价、支付和订单流程；确认绑定玩家、操作类型及目录版本，服务端选中奖励并保存快照，重复确认不会重新收费或抽样。支付前在游戏线程验证整个奖池的物品模板，待支付或结果未知时隐藏未结算奖品，成功后存入收件箱供领取。完整界面有抽奖入口，简约界面复用编号操作，结算口播只读奖品名称。

没有运行测试、启动 Minecraft、创建存档或调用外部 API；奖池重载、概率分布、实际扣款与交付、重复确认、重启恢复、两种界面交互和 TTS 尚未作运行验收。订单存档已改用 `Purchase.order`，没有旧 `Purchase.offer` 兼容逻辑；开发存档若无法解析，使用 `/system reset [玩家]` 后重新授予。

## 2026-10-26 目录 ID 与支付类型

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有运行测试或启动 Minecraft。阅读代码确认：目录奖励和商品 ID、相关请求及签到模型选择使用 `Identifier`，移除 `validId` 正则；`Price.type` 改为 `PaymentType`，通过原版 `StringRepresentable.fromEnum` 编解码，校验及支付分支不再比较字符串。JSON 支付类型仍为 `ITEMS` / `SYSTEM_COIN`；数据包、存档和游戏内交易未作运行验收。

## 2026-10-26 奖励与商城使用 ItemStackTemplate

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 只编译开发源集。阅读依赖 JAR 确认：`ItemStackTemplate.CODEC` 解析物品 ID、1～99 的数量及组件补丁，不访问物品默认组件；`create()` 构建物品堆并通过原版严格校验，失败返回空堆。

阅读代码确认：已移除 `PlayerData.Item` 和重载阶段的 `validateItems`，奖励、商品、价格与结果均直接保存模板；奖励及商品产出列表在 Codec 中统一限制为 1～36 项。发奖时先创建全部物品堆，商城在扣款前检查产出；扣除材料按物品与组件精确匹配并逐槽预留，重复模板不会重复扣同一份材料。未运行测试或启动 Minecraft；数据包重载、组件持久化和游戏内交易未作运行验收。

## 2026-10-26 成就规则使用 Identifier

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有运行测试或启动 Minecraft。阅读代码确认：`AdvancementRule` 的规则 ID 与成就 ID 改为 `Identifier`，使用原版 Codec 解析，移除手写正则；登录完成状态、待处理成就事件和已启用基线缓存使用该类型，查询成就不再重复解析。持久化键、事件任务入口与模块参数按原有文本格式转换，配置 JSON 结构不变；缺失或无效的 ID 不生成默认规则，仍由外层 `rules` 的 lenient 字段回退。游戏内事件触发与配置容错未作运行验收。

## 2026-10-26 移除任意上限与重复校验

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译及打包通过；`testModClasses` 只编译开发源集。发布 JAR 为 805,695 字节。静态检查确认中英文各 376 个语言键且集合一致，`git diff --check` 通过。

阅读代码确认：模块、语义 profile、指令别名、成就规则、例句、提示词和收费表不再受 128/256 等任意数量上限约束；移除对应的资源大小及元数据长度限制。执行策略只检查非负次数、正数预算和超时关系，具体预算由模块或管理员决定；缓存仍按配置容量和 TTL 淘汰。查询输入使用通用字符串与非负分页偏移，不在指令解析器、模块和工具中重复限制查询长度。配置错误通过 Codec 返回，字段继续使用 `lenientOptionalFieldOf`；引擎与平台启动不再重复校验同一份配置。

商城取消包数量、包内条目数量、标签长度和固定 64 数量上限，产出在资源加载时按物品自身的堆叠上限校验；付款材料支持跨堆扣除，重复材料使用 `long` 合并数量。重复 ID、类型身份、权限、玩家隔离、支付状态、实际执行预算、网络长度和分页边界继续保留。未运行测试、启动 Minecraft、创建存档或调用外部 API；大规模目录、配置容错及游戏内交付未作运行验收。

## 2026-10-26 请求分派与网络编码统一

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集构建及打包通过；`testModClasses` 只构建开发源集。静态检查确认：请求和响应通过 `DispatchRegistry` 的 `Codec.dispatch` 编解码，网络 `StreamCodec` 直接携带对象；已删除 `AgentRequest.decode`、`mode/encodedInput` 和拆开的 C2S 字符串字段。通知、绑定变更和导航不再使用特殊请求 ID，用量响应使用独立 `open` 标志。当前未发布版本的网络协议统一为 1。

阅读调用链确认：请求执行、结果投影、卡片展示与口播改用类型处理 Map；模型候选只从可选模块建立 Codec Map，任务查询结果使用命名类型。限流、绑定检查、异步连接代次检查、结果长度限制继续共用入口；打开界面响应关联并完成原请求，导航不占用随后状态刷新的限流间隔。NLS 事件和错误码使用 Map，仍校验 task ID、namespace 与文本长度。

静态检查确认中英文各 376 个语言键且集合一致，Java 包路径及文档本地链接有效，`git diff --check` 通过。发布 JAR 为 811,210 字节，包含新的请求、响应及分派类型，不含开发数据包资源。未运行测试、启动 Minecraft、创建存档或调用外部 API；实际网络往返、类型静态初始化、界面交互及语音行为尚未作运行验收。

## 2026-10-26 配置重载与目录入口收拢

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。静态检查确认：语音页使用 `SpeechConfig.INSTANCE.load()` 重读 `system-speech.json` 并刷新本页草稿；重载按钮不依赖 API Key。TTS 缓存固定使用 `ConfigInstance.getConfigFolder().resolve("tts-cache")`，已移除 `SystemConfig` 和独立音色导入逻辑。

中英文各 376 个语言键且集合一致，Python 工具语法检查和 `git diff --check` 通过。发布 JAR 包含新的重载按钮及双语文案，不含 `SystemConfig`、`SpeechConfig$VoiceProfile` 或开发数据包资源，大小为 784,463 字节。未运行测试、启动 Minecraft 或调用外部 API；配置重载和界面交互尚未作游戏内验收。

## 2026-10-26 PlayerData 与 RecordCodecBuilder

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。阅读代码确认：对话状态及回放缓存归入 `Conversation`，每日模型调用额度归入 `ModelQuota`；顶层 Codec 从 17 项缩减为 13 项，两个内联 MapCodec 使用原有字段名。交付、事件、步骤回执和订单同步改用 `RecordCodecBuilder`，已移除无调用的 `ObjectCodec`。静态检查确认中英文各 377 个语言键且集合一致，`git diff --check` 通过；发布 JAR 包含两个新分组，不含 `ObjectCodec` 或开发数据包资源。

解码时为列表、映射、基线集合和解锁集合创建独立可变容器；清空对话和关闭历史复用 `Conversation.clear()` 推进记忆代次。相关读取、写入、保留策略、在途代次检查和每日额度判断已同步。这里只作静态检查与编译，没有运行测试、启动 Minecraft、创建存档或调用外部 API；存档读写与游戏内行为未作运行验收。

## 2026-10-26 ConfigInstance 与变更标记统一

执行 `compileJava testModClasses jar --console=plain`，最新主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。

静态检查确认八种配置均采用 `AdvancementSettings` 的 `ConfigInstance<T> INSTANCE` 写法，读取统一使用 `get()`，界面模式、TTS、ASR 的已提交修改调用 `set(next)` 并自动 `markDirty()`；旧独立加载、保存入口已移除。中英文各 377 个语言键且集合一致，`git diff --check` 通过。

阅读代码确认：资源重载更新已注册容器，tick 与关闭事件保存待写配置；检查标记与保存共用实例锁，失败恢复标记，重载先保存未落盘的改动。客户端事件放在独立客户端订阅类中。服务端引擎、模型连接和成就事件仍持有启动时快照，需要重启完整应用修改。

本轮未运行测试、未启动 Minecraft、未调用模型或语音 API；默认文件复制、重载和延迟保存的实际运行行为尚未验收。下文为此前版本的历史检查记录。

## 2026-10-26 数据包目录与配置拆分

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。静态检查确认：`AgentSettings` 仅含执行预算、限额、并发和时区；模型连接、语义路由与成就规则分别使用独立配置文件。配置使用 `RecordCodecBuilder`、record getter 与 `lenientOptionalFieldOf`，各配置类管理 `INSTANCE`，统一通过 `ConfigLoader.load(path, codec)` 复制缺失的默认文件并解析。客户端服务不再保存重复的设置对象，收费表也复用同一加载入口。

奖励和商品仅从数据包加载，三个默认签到奖励和一个默认商品均为内置 JSON；没有配置列表兜底。默认资源、示例 JSON 和文档本地链接静态检查通过；发布 JAR 包含八份配置默认文件和四个商品/奖励条目，不含开发源集资源及旧 `AgentSettings.EventRule` 类。中英文语言键未改动。

本轮未运行测试、未启动 Minecraft、未调用模型或语音 API；配置容错、默认文件复制及数据包热重载的实际运行行为尚未验收。

## 2026-10-26 Agent 分包简化

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过。静态检查确认：Agent 根包集中执行与注册职责，领域接口、数据和实现合并到同一包；内置输入输出仍为独立类型。七个 Codec/Schema/JSON 工具移至 `com.iafenvoy.system.util`，包名与目录一致，源码与发布 JAR 均无旧 `api/data/registry/runtime/util/command` Agent 子包引用或类。发布 JAR 为 790,191 字节，低于 10 MB。指令注册修改方法保留包内访问权限。

本轮仅调整目录、引用和文档；没有运行测试、启动 Minecraft 或调用外部 API，编译与静态检查不作为运行时功能验收。

## 2026-10-26 配置字段容错

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过，未运行测试或启动 Minecraft。静态检查确认 config 包内显式字段均使用 `lenientOptionalFieldOf`，可变配置通过 `ObjectCodec.lenientOf` 使用同一机制并在字段层执行原有校验；普通 `ObjectCodec.of` 的存档严格解析未改。收费表跳过不完整或无效的条目，不补造单价。没有对损坏配置进行运行时验收。

## 2026-10-26 Agent 注册与调度重构

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过。仅作编译和静态检查，没有运行测试、启动 Minecraft、调用模型或进行语义准确率验收。

阅读代码及静态扫描确认：Module 自带的 `ModuleRouting` 统一生成指令、语义 profile、模型目录与快捷入口；助手补全与游戏聊天转发共用注册表。交互、事件与维护队列复用 `TaskExecutor`，模块执行和权限校验集中到引擎。Tool 仍是单行为、Module 以 List 组合；旧指令 switch、独立内置 profile 安装器、商城安装包装和重复的模块 dispatch Codec 已移除。语言键仍为中英文各 377 个且集合一致，包名与目录一致，JAR 不含已删除的分派类，大小仍低于 10 MB。

运行时待验收：扩展模块一次注册后在两种输入入口执行与补全；未装 FTB 时无任务入口；低分语义回退；结构化付款来源校验；取消与偏好切换的写回隔离；持久事件重启恢复；队列满和关闭时排队 Future 的完成。以上未宣称实测通过。

## Agent 外包与内置数据类型整理

Agent 代码重新集中到 com.iafenvoy.system.agent 内部；输入与输出拆为 30 个 builtin 独立类型。已删除旧结果迁移器、事件授权来源回退和写回执格式归一，账本必需字段直接由当前 Codec 读取。编译与静态检查不作为运行时功能验收；本次未启动 Minecraft 或执行测试。


## 2026-10-26 分包与 Codec 重构

执行了 compileJava、testModClasses 和 jar，均通过；testModClasses 仅编译开发源集。未启动 Minecraft、未执行测试、未调用付费 API，因此不作为运行时功能验收。

静态检查：源码包名与目录一致；中英文各 377 个语言键且集合相同；发布 JAR 的源码目录映射与当次分包一致，没有开发源集资源，仍小于 10 MB。可选 FTB / SVC 的反射类名已随分包更新。配置、玩家存档、调用链及固定 API 报文已改为 Codec，所有结果直接使用 AgentResult.CODEC，无旧结果迁移。


遵循仓库 AGENTS.md：用户未明确要求时不运行测试、不启动 Minecraft，也不创建临时存档、玩家配置或探针。

## 本次检查

本次轻量路由重做已执行 `gradlew compileJava testModClasses jar --console=plain`，主源码与开发源集编译、模组打包通过。`jar` 的完整产物 10,000,000 字节上限检查也通过。System 自身约 260KB，原有 Minecraft/NeoForge/UI 等安装依赖另计。

静态检查确认 JAR 没有内嵌第三方库、ONNX/DJL/Caffeine 类、native 资源或模型文件；新词表约 4.2KB，路由代码仍统一位于 `com.iafenvoy.system.agent.runtime.routing`。构建配置和版本表同步移除三类运行库依赖，旧 E5 阈值不复用到词法匹配。

双语各 193 个键且键集合相同，资源与配置示例 JSON 可解析，文档本地链接有效。阅读代码确认了不同模块分差、输入覆盖率和陌生特征保留、只读模块及工具层限制、低把握单次回退、精确意图缓存不保存业务结果、当前参数重新解析、玩家独立作用域和清理、MC 语言注入。这些是代码审查结论，尚未作运行验收。

`testModClasses` 只编译开发源集，不运行测试。没有执行 `test`、任何 `run*` 任务、客户端或服务端；没有调用 DeepSeek、训练分类器或运行准确率/性能基准。编译通过、启动成功与验收通过是三种不同结论。默认阈值是工程起点，不是验收过的正确率或节省比例。

## 常用编译命令

```powershell
.\gradlew.bat compileJava testModClasses jar --console=plain
```

FTB 仅为 `compileOnly`，默认开发运行不安装。需要用户明确要求测试且选择 FTB 范围时，再使用 `-PwithFtbQuests` 加入三个 `localRuntime` 依赖。没有旧数据包条目再强制要求开发环境安装 FTB。

## 后续人工验收范围

- OFF 不使用本地索引/意图缓存；SHADOW 不影响路由、候选提示和缓存；无外部模型文件即可使用新配置。
- ENFORCE 的独立语料评估：领域别名、陌生词与低覆盖率、高分小分差、否定/指代/复合表达、写请求与不支持表达、缺失/多个物品 ID；相似度不充当正确率。
- 精确意图缓存的成功写入条件、每玩家/全局上限、TTL/LRU、清空/撤销/掉线与迟到写入；更换配置后索引和缓存重建，结果始终查询当前状态。
- `/ai off` 禁止远程回退但允许已启用的本地只读直达；`/memory off` 不缓存意图；诊断不泄露原文/UUID。
- 明确指令与高把握直达的真实调用次数、本地索引/缓存耗时及 MSPT；本地诊断不能代替完整性能基准。

- 默认未绑定、管理员自身/指定在线玩家 grant、重复 grant、成功落盘后绑定提示、未授权命令与未绑定请求拒绝。
- revoke 删除玩家记录、临时领取/报价引用失效、旧模型/游戏/存储回调不能回写、清理失败时解除旧等待但不提示成功；重新授予不会补发既有成就。
- 两种界面切换及本机偏好、简约模式只显示当前轮、编号选择/分页/兑换确认、模式切换保留当前结果、简约模式不加载历史。
- 明确短语与指令跳过模型路由；否定句、普通自然语言仍遵守单次路由约束；签到理由语言与文案状态准确性。
- 无 FTB 的专用服务端加载；客户端 `/system`、输入、滚动、关闭重开及不同 GUI 缩放。
- 没有密钥时结构化查询可用、AI 签到不入账；真实 DeepSeek 的 JSON 格式、超时与调用次数。
- 查询/事件采用不同策略；同玩家并发请求、其他玩家公平性、主线程调度开销。
- 双语键、缺少引用、自选奖励、FTB 换队/重复任务/任务重载、奖励类型失败。
- FTB 任务完成后 HUD 依次显示完成及解锁项；多前置/任选前置、STARTED 依赖、章节依赖、隐藏任务、FLEXIBLE 模式；同队已绑定在线成员接收，未绑定或其他队伍不接收。
- FTB 新绑定依次显示绑定消息、可领取礼包、当前任务概览，空礼包或空任务跳过；重登、重复授予、打开界面、换队/重载不重复引导，撤销后重新绑定可再次触发，不补播历史完成。介绍任务不读也不绕过依赖，礼包显式领取、过期刷新、队伍/自动奖励与重复点击；锁定切换、进度重置与重复冷却后的状态比较；客户端进度回显不重复通知，静默管理操作不播报；按 T 点击详情/确定后继续队列。
- 相同签到请求、跨日、掉线、收件箱格数不足和物理交付关键窗口崩溃。
- 玩家 A/B 历史隔离、旧连接回包、清空记忆代次、关闭保存/外部模型。
- 新规则基线、重复成就、停用规则、重启恢复、部分成功与事件持久化缺口。
- 商城浏览/分页/确认、材料不足不扣除、Saturn 物品 / 标签及带组件物品匹配、重复 / 重叠 Cost 的部分扣款保护、动态金额冻结、重复确认及跨玩家报价隔离。
- 数据包目录分别覆盖、同路径优先级、禁用条目、无效物品拒绝重载、旧报价失效和签到推理期间重载。
- 扣款前存储额度预留、锁定权益不可领取、支付后崩溃/掉线、未知订单人工核对；Saturn 资源及扩展 Cost、报价期间玩家实体更换、免费奖池。

只有明确指定并实际执行后，才能记录这些场景通过；本轮没有作这些结论。

## 2026-10-27 Saturn Cost 接入

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有执行测试或启动 Minecraft。按依赖 JAR 的 API 和本地源码核对了 Cost、CostPayment、ItemMatcher 与 Resource 的编码和支付行为。商城与抽奖改用 `Cost.LIST_CODEC`；目录通过 NeoForge 提供的 RegistryOps 解析资源引用，默认及示例数据改为 `costs`。旧的 PaymentType、SystemCoinPayment 和手写物品扣款器已删除。

阅读代码确认：展示仅同步求值结果，确认使用服务端绑定账单；原有订单防重、权益锁定和目录版本约束保留。当前 Saturn 实际扣款异常后可能继续并返回成功，System 额外记录每项真实执行的失败 / 异常，阻止部分扣款后发奖。以上为静态审查结论；数据包加载、实际扣款与展示、崩溃恢复尚未作游戏内验收。

静态检查：主资源、开发资源与示例共 37 个 JSON 文件语法及键唯一性通过；双语各 454 个键且集合一致；新目录和示例没有旧 `price` 定义，源码没有旧支付接口引用。修改文档的本地链接有效，相关文件 `git diff --check` 通过，网络协议保持 `1`。本次未修改 `README-zh.md`。

## 2026-10-27 统一设置与自动保存

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。没有执行测试、启动 Minecraft、打开麦克风或调用收费接口；界面点击、输入与保存失败提示尚未作游戏内验收。

静态检查：设置指令与补全共享 `/settings` 注册，旧的语音设置入口已移除；各文本框、开关、地域和语种选择均连接自动保存，本地提交立即调用配置容器保存，服务端偏好保留原有确认流程。语音页移除手动保存与重新加载按钮，不提供 JSON 导入；完整语音参数仍在试听或实际使用时校验。中英文各 451 个语言键、无重复且集合一致，设置模板 ID 与绑定、修改文档的本地链接及相关文件的差异空白检查通过。本次未修改 `README-zh.md`。

## 页面初始化与首次布局

参照 MiXianTu 的 `AuiPages` 和页面宿主，核对实际使用的 ApricityUI 1.2.6 源码后，统一在文档创建前预热本地样式，在监听器绑定、内容填充和首次布局提交完成后恢复页面自动渲染；绘制及补全坐标测量前主动提交待处理布局。HUD 通知也在初始内容填充后提交隐藏布局，再启动原有动画。`compileJava --console=plain` 编译通过。

没有执行测试或启动 Minecraft；静止鼠标首次打开、带补全重开、窗口缩放和连续通知的实际位置与动画尚未作游戏内验收。本次未修改 `README-zh.md`。

## 2026-10-28 FTB 查询固定返回类型

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。没有运行测试、启动 Minecraft 或调用外部 API；任务列表、名称歧义选择和礼包领取尚未进行游戏内验收。

阅读代码确认：`ftb.query` 和新增的 `ftb.find` 直接返回不可变 `List<QuestBriefOutput>`，`ftb.detail` 只返回单个 `QuestOutput`，不存在或不可查看的任务返回业务错误。移除任务查询的多态根接口、列表/详情包装和专用类型注册表；新手礼包仍使用独立的 `StarterGiftOutput`。名称匹配、候选展示与礼包跳转由 `QuestDetailModule` 组合只读工具，最多 3 次本地调用，不增加模型调用，礼包查询复用同一个工具实例。唯一名称匹配不会覆盖原列表编号；编号仍核对玩家、队伍、系统代次、历史代次、有效期及原任务对象。可选 FTB 类仍隔离在兼容层，所有任务快照在游戏线程构建。

静态检查：源码和当前架构文档没有任务查询类型注册表及旧包装类引用，保留 7 张代码注册表，注册声明与事件登记一致；`git diff --check` 通过。没有修改语言文件或 `README-zh.md`，网络协议保持 `1`。

## 2026-10-28 卡片数据接口与个人语音用量

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 仅编译，不执行测试。没有启动 Minecraft、打开麦克风或调用外部 API，界面渲染、资源重载及实际合成/识别尚未作游戏内验收。

阅读代码确认：`AgentResult.Card` 直接持有 `CardData`，22 个数据实现各自提供 MapCodec 与模板键，根 Codec 按注册表分派，无 `TypedPayload` 卡片包装或 `value` 层；模块动作继续使用 `TypedPayload`。客户端独立构建 CardContext，再由模板键选择渲染工厂，公共数据和 Codec 不引用客户端渲染类。默认 22 个渲染绑定均有对应 JSON 布局，资源 ID 不必与数据类型 ID 相同。

TTS / ASR 统计由本机 `LocalSpeechUsage` 按客户端账号 UUID 保存于 `config/system/speech-usage/<UUID>.json`，tracker 捕获所属账号的 Ledger；关闭播放或换服务器后，已发起请求的结果仍记回原账号。语音报告网络包、服务端处理器和语音聚合字段已移除，服务端 SavedData 与玩家明细只保存 AI 用量。管理员全服切换只改变 AI 展示，语音面板始终读取本人本地记录。以上为代码阅读结论，尚未进行运行验收。

静态检查：主资源、开发资源及示例共 60 个 JSON 文件语法与键唯一性通过；双语各 455 个键且集合一致；本次涉及的 11 份架构、语音和索引文档的本地链接有效。网络协议保持 `1`，未修改 `README-zh.md`。

## 2026-10-28 卡片直接读取模板

阅读代码发现原渲染映射的 22 个内置条目均为同名转发，没有自定义工厂调用方。移除 `SystemCardRenderers`，由 `CardPresentation.of(data, context)` 直接使用 `CardTemplates.get(data.template())`；上下文仍独立构建，缺少模板时保留原有回退展示，通知仍保存入队时的模板及上下文快照。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；没有运行测试、启动 Minecraft 或调用外部 API。静态检查确认 22 个 CardData 模板 ID 都有对应 JSON，源码无已删除类的引用，更新的架构文档和索引链接有效，`git diff --check` 通过。实际页面、通知及资源重载尚未作游戏内验收；未修改语言资源、网络协议或 `README-zh.md`。

## 2026-10-28 CardData 自建上下文

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未运行测试、启动 Minecraft 或调用外部 API。实际页面展示、通知交互、物品预览与语言切换尚未进行游戏内验收。

阅读代码确认：原有 18 个类型参数构建器已移入对应 CardData 实现的 `buildContext(builder, formatting)`；其余 4 个协议或动作载荷保留默认空填充。任务及奖励子条目复用自身构建方法，任务列表编号和奖励选项仍读取预填的动作列表。公共 CardContext 保留原有状态、参数、回调三张表及不可变快照；CardFormatting 由调用方提供文字解析函数，共享奖励、价格和物品名称格式化，不加载 GUI 类。ClientCardRegistry 不再保存参数构建处理器，统一在类型填充后补充标题、模式及按钮发送回调；功能模块只保留消息、状态、刷新和通知行为。

静态检查确认：公共卡片数据、上下文和格式化接口没有客户端 GUI、AssistantClient 或客户端模块导入；没有旧 CardContext 包路径或模块参数构建器注册残留。更新的 7 份架构文档及索引的本地链接有效，双语各 455 个键且集合一致，`git diff --check` 通过。未修改布局 JSON、语言键、网络协议或 `README-zh.md`。

## 2026-10-28 客户端卡片行为与展示分离

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未运行测试或启动 Minecraft。完整/简约界面、HUD 通知和任务播报的实际交互尚未作游戏内验收。

阅读代码确认：原 ClientCardRegistry 改为 ClientCardBindings，仅登记与调用消息、客户端状态、静默刷新、任务引用及通知行规则。上下文组装、布局解析、图形展示、纯文本格式化和按钮标签统一由 CardPresentation 提供，完整界面、简约界面与 HUD 直接调用，普通卡片无需登记行为。动作仍在类型参数填充前写入 Builder，通用标题、模式和发送回调仍在填充后补充，模板缺失回退及通知快照保持原实现。

静态检查确认源码没有旧类名或通过行为表调用展示方法的残留，ClientCardBindings 不引用 CardContext、渲染类或 AssistantClient；CardPresentation 不引用行为装配表。状态、消息、刷新及任务引用的调用方仍使用行为绑定。更新的 5 份架构文档及索引的本地链接有效，双语各 455 个键且集合一致，`git diff --check` 通过；未修改语言资源、布局 JSON、网络协议或 `README-zh.md`。


## 2026-10-28 卡片数据流与普通文本约定

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。没有执行测试、启动 Minecraft、创建存档、打开麦克风或调用模型/TTS/ASR API。

阅读代码确认：CardData.buildCard 统一返回 CardBuilder，公共 card 包不引用客户端、AUI、服务端运行时或 FTB 类；客户端 CardPresentation 构建一次完整展示，原 ClientCardBindings、通知标签策略表、旧 Card 外壳和任务播报专用网络包已删除。卡片按钮明确区分本地/服务端动作，服务端按钮经 ModuleRouting.button 转成原类型化工作流输入；单 Tool 对应单行为，多个工作流共享同一 Tool 实例。

消息使用 CardRef(UUID, revision) 与 SHOW/REPLACE/REMOVE，不再按 Java 类型刷新。状态、历史使用独立响应，历史恢复只读且不播报；等待关联请求和页面，失败、超时、无卡片响应都可解除。任务领取后由服务端查询完整任务是否还有可领奖励，不使用当前分页推断；礼包领取静默替换/移除。SpeechContent 随 DTO 下发，服务端后台缓存陌生任务及背景解释，首张卡片不等待 AI，也不追加迟到播报。

普通消息的判定集中在 CardPresentation：只有单个 system:text 节点、没有 repeat、没有任何按钮或额外可见标题/摘要内容时省去卡片外框。文字带按钮、物品、容器、分割线、多节点等仍为卡片。单文本模板带按钮时自动补充操作区，通知 FOOTER 由代码生成。

静态检查：主资源与开发资源共 44 个 JSON 文件语法及键唯一性通过；22 个 CardData 类型使用的 20 个布局均存在；中英文各 455 个键且集合一致；修改文档的本地链接和公共契约依赖检查通过，git diff --check 通过。移除不再使用的 client_state/history/preferences 模板，协议仍为 1，README-zh.md 未修改。

尚未进行游戏内验收：普通文本与带按钮卡片的视觉差异、首次绑定队列、同类型消息独立更新、历史按钮只读、WAIT 失败/超时、资源重载、长任务列表、物品 tooltip 与语音切换。编译和静态检查不代表这些功能已实测通过。

## 2026-10-28 通知确定按钮与语音别名

阅读代码确认原逻辑只在没有业务底部按钮时补充“确定”，已改为始终追加，助手忙碌时仍可关闭通知。确认操作新增“下一个”“下一页”例句并保留“关闭”；实际按钮文案的精确匹配优先于别名，避免覆盖真正的翻页按钮。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；未运行测试或启动 Minecraft，通知按钮点击与实际语音识别尚未进行游戏内验收。静态检查确认别名完整且无重复，中英文各 455 个语言键且集合一致，`git diff --check` 通过。

## 2026-10-28 玩家模块管理指令

核心模块装配 `/system module add/remove <玩家> <模块ID>` 和 `list [玩家]`，节点要求管理员权限，支持控制台与单个在线玩家选择器。静态阅读确认：增删只调用现有 ModuleManager，沿用持久覆盖、会话启停、异步初始化和状态同步；模块补全来自正式服务端注册表，排除必需模块，未安装的可选模块不会出现在候选中。无目标的 list 显示注册目录，有目标时显示已完成激活的玩家模块。重复覆盖、未知模块、必需模块和未绑定玩家有明确反馈。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；中英文各 463 个键且集合一致，`git diff --check` 通过。没有运行测试或启动 Minecraft；权限、在线补全、客户端即时刷新、重登持久化与控制台执行尚未作游戏内验收。未修改 README-zh.md。

## 2026-10-28 动作接口与 MapCodec 注册

阅读代码确认：19 个动作输入均实现 AgentAction，codec() 返回本类注册的 MapCodec；SystemActionTypes、SystemResourceKeys 和 SystemRegistries 的 ACTION_TYPE 直接保存这些 Codec。移除 TypedPayload 及 Type 包装，工作流 inputCodec() 与动作实例核对同一个 Codec；BuiltinModule 从 SchemaMapCodec 获取注册 ID 和模型 Schema。指令、语义提取和按钮解码共享 AgentAction.validate，模型解码仍只接受当前可用目录，不开放事件或仅结构化操作。5 个无参数动作各用独立单例类型，工具内部保留共享 EmptyInput。

成就配置和事件任务使用独立 action 字段保存完整动作，内部按 moduleId 分派并平铺参数，避免与顶层 advancementId / ruleId 冲突；示例已同步，无旧字段迁移。客户端直接发送动作对象，卡片仍通过动作 ID 与字符串参数提交，服务端继续进行工作流权限校验。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。静态检查：19 个注册 ID 与 Codec 类型各自唯一，实现均返回自身 CODEC；主源码无旧包装引用，玩家查询、签到和礼包查询的底层 Tool 仍接收 EmptyInput；57 个资源与示例 JSON 语法及键唯一性通过，双语各 463 个键且集合一致，`git diff --check` 通过。

未运行测试、启动 Minecraft 或调用外部 API。动作网络编解码、成就配置加载、事件恢复、模型路由和卡片按钮的实际交互尚未进行游戏内验收。协议保持 1，README-zh.md 未修改。

## 2026-10-28 模块内部可用性与可选依赖

阅读代码确认：ServerModule 新增默认 true 的 available()，QuestSystemModule 自行检查 FTB Quests；SystemServerModules 始终以普通 DeferredHolder 注册安全定义。ModuleManager 在创建工作流、安装钩子、运行时及访问存储声明前统一筛选，指令装配、存储枚举和模块增删补全也跳过不可用定义；必需模块仅从实际装配集合中收集，手动覆盖及 Saturn action 无法启用缺少依赖的模块。list 保留完整注册目录，玩家列表只显示成功激活的功能。

模块 ID、动作 / 卡片 Codec 和 DataStorage Codec 不随依赖缺席而取消注册，数据包模块引用与保存的数据继续解码，FTB 适配器仍只通过检查后的反射入口加载。现有默认定义中的 FTB 条目保留；未安装依赖时跳过装配，不删除手动覆盖或历史数据。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。未运行测试、启动 Minecraft、创建存档或调用外部 API；FTB 缺席时的实际类加载、数据包加载和移除 / 恢复依赖后的存档读写尚未运行验收。

## 2026-10-28 按玩家绑定数据包系统

阅读代码确认：`/system grant <系统ID> [玩家]` 和自己的 `/system grant <系统ID>` 从当前存档的数据包注册表补全 ID，服务层拒绝不存在的定义。无参数命令显示用法，不进入模型路由；管理员权限、单个在线玩家选择和控制台目标限制保持。AgentEngine 在同一玩家事务内检查已有绑定并写入：同 ID 只返回已绑定，不同 ID 拒绝且不覆盖；绑定变更和维护锁阻止并发授予绕过检查。

玩家数据以不可变 SystemBindings 集合预留多系统结构，当前公开工厂、授予事务与 Codec 最多保存一个 ID，bound() 从集合推导。登录缓存系统 ID，模块求值使用该玩家选定的 SystemDefinition，has_system 的指定 ID 与实际绑定比较。绑定通知使用选中定义，ClientState 同步当前 ID，客户端从同步数据包注册表读取名称与介绍；定义被移除时不会自动改绑默认系统。revoke/reset 仍删除绑定与业务记录，独立用量保留。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过。未运行测试、启动 Minecraft、创建存档或调用外部 API；注册表实际补全、不同系统的多人隔离、并发授予、重登、定义移除及客户端显示尚未游戏内验收。协议保持 1，README-zh.md 未修改。

## 2026-10-28 明确授权的联网搜索模块

已执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过。没有执行测试或启动 Minecraft，没有请求收费 Search/Fetch、模型、TTS 或 ASR API。

静态检查：主资源、开发资源和示例共 59 个 JSON 语法及键唯一性通过；中英文各 486 个语言键，集合和格式化参数数量一致。搜索动作/卡片、客户端节点、页面元素、提示词占位符及默认价格文件对应完整，本地 Markdown 链接与 `git diff --check` 通过，README-zh.md 未修改。

阅读代码确认：搜索只有明确指令、保守的当前消息解析或主动页面操作才能启动；不声明语义档案、快捷动作或自动模型入口。Search/Fetch 的独立工具只读、禁止事件调用；循环受模块预算约束，禁止重复查询和未知 URL 抓取，来源编号来自本次结果。每次付费调用检查生命周期、功能权限与外部服务偏好，网关计量脱离可撤销业务存档；未知结果不会被描述为保证免费。整理失败且会话有效时保留已经取得的来源。

尚未验收：实际 DeepSeek 响应兼容性和时延、游戏内页面与卡片排版、服务器重启后用量恢复、配置重载、权限变化和取消时序，以及供应商实际扣费。编译与静态检查不代表这些行为已经实测通过。

## 2026-10-29 搜索请求的模型路由

阅读代码确认：WebSearchModule 原先声明 explicitOnly，被同时排除在模型能力目录、路由返回值 Codec 和自动来源执行权限之外，未命中固定联网句式的“帮我查一下……”只能走其他能力或澄清。现已恢复普通模型路由，注册描述和数据包 routing.txt 要求模型从当前请求识别资料查询，优先使用本地任务、状态等能力；普通问题、否定联网与历史不作为搜索授权。这是模型意图判断规则，不是确定性授权校验。模块仍没有本地语义档案、快捷推荐或意图缓存，搜索预算、开关、权限和按次计费沿用原实现。模块路由声明在服务端初始化时装配，需重启后应用。

执行 `compileJava testModClasses --console=plain` 编译通过；静态检查确认路由提示词占位符正确、中英文各 486 个语言键且集合一致，`git diff --check` 通过。未运行测试、启动 Minecraft 或调用收费 API；自然语言实际分类准确率、搜索结果及费用尚未运行验收。

## 2026-10-29 explicitOnly 的高确信度标记

阅读代码确认：ModuleRouting 使用 NORMAL、EXPLICIT_ONLY、STRUCTURED_ONLY 三种 Selection；explicitOnly 不再隐藏能力，ModuleRegistry 在 ModelRoute.Capability 目录中通过 Codec 写出 `explicitOnly: true`，路由返回值仍接受该能力。routing.txt 对所有带标记的能力统一要求非常确信当前意图时才选择，不确定则澄清，不需要固定关键词或额外的模型确认调用。

搜索恢复 explicitOnly 声明；注册语义档案时禁止该策略直达，路由器、意图缓存写入与执行入口同步检查，不能靠相似度绕过模型判断。付款、抽奖等 structuredOnly 能力仍不进入模型目录或解码；原先借 explicitOnly 禁用模型的手动任务提交改为 structuredOnly，保留原有指令和按钮入口。

执行 `compileJava testModClasses --console=plain` 编译通过；路由提示词占位符与双语 486 个语言键集合检查通过，`git diff --check` 通过。没有运行测试、启动 Minecraft 或调用收费 API；模型实际遵循高确信度要求的效果尚未运行验收。

## 2026-10-29 精简搜索回答、折叠来源与原版外链

新增客户端通用 CardNode `system:details` 与 `system:link`，各自提供 MapCodec 并通过 SystemCardNodeTypes 注册。静态阅读确认：折叠区支持 children 的递归节点、when/unless/repeat 与占位符，默认 open=false；展开只修改当前 DOM，不发送业务或模型请求。搜索模板改用折叠来源区，标题点击走 AssistantPageScreen 中对原版 Screen.clickUrlAction 的调用，复用 Util.parseAndValidateUntrustedUri、chatLinks/chatLinksPrompt 及确认后返回当前界面的逻辑。新节点在 HUD 中检查可交互状态；链接原值替换不加入格式码，语音仍只读取业务回答。

web_search.txt 要求提炼相关事实、合并重复内容、先给结论和简短要点，保留必要数字与限制，不附加网页原文、导航、广告、检索过程和重复来源列表；不增加压缩模型调用，也不改变 explicitOnly 路由语义。

执行 `compileJava testModClasses --console=plain` 编译通过。静态检查：21 份卡片 JSON 无重复键、全部节点 ID 对应 12 个注册类型，提示词占位符正确，中英文各 487 个语言键且集合一致，`git diff --check` 通过。未运行测试、启动 Minecraft 或调用收费 API；折叠布局、URL 确认交互与模型实际回答长度尚未游戏内验收。

## 2026-10-29 折叠来源布局与搜索整理收尾

根据截图和源码检查，调整折叠 / 链接按钮为 flex 容器、独立 block 文字和固定箭头占位；折叠切换立即清理子树布局缓存并提交重排。来源列表用普通块纵向排列并添加分隔线，摘要通过 AUI 支持的 line-clamp 最多展示三行，完整来源仍可点击。已读取当前 ApricityUI 1.2.6 sources JAR，核对布局失效方法、行数限制及节点文字绘制机制；未启动渲染验证，截图中的具体绘制路径尚未运行复现。

阅读代码确认：原搜索循环在重复工具选择、可恢复异常后直接退出，且默认单次模型期限仅 10 秒；这些路径可能返回未整理来源。现有证据时预留最后一次整理和时间窗口，重复操作或可恢复的探索失败转为只解码 Answer；硬预算、授权检查、每日额度和取消语义保持生效。默认模型 / 工具次数仍为 6 / 5，总期限改为 90 秒，单次模型 20 秒，已有自定义策略继续优先。来源标题与摘要合并连续空白，提示词要求用法类问题给步骤和例子。最终无法整理时仅记录错误码、次数和剩余时间，便于后续区分超时、无效响应与预算失败；此前静默兜底缺少原因日志，不能据此断定截图这一次的具体失败原因。

执行 `compileJava testModClasses --console=plain` 编译通过；21 份卡片 JSON 无重复键且节点 ID 均已注册，中英文各 487 个语言键集合一致，搜索提示词占位符检查通过，`git diff --check` 通过。没有运行测试、启动游戏或调用收费 API；实际展开后的排版、供应商响应时间及最终整理成功率尚未运行验收。

## 2026-10-28 系统定义的模块 tag

已读取当前 Saturn 依赖 JAR 的 RegistryCodecs 签名及对应源码，模块引用复用 keyOrTag / matchesKeys，支持单个模块 ID、#tag 及带 condition 的对象形式。权限求值时读取注册表当前 tag 成员，继续保留重复引用任一条件成立、手动覆盖、必需模块及可选依赖检查。客户端仍同步实际模块 ID，不依赖 tag 展开。

执行 `compileJava testModClasses --console=plain` 编译通过；新增文档中的两个 JSON 示例、文档本地链接、双语语言键集合及 `git diff --check` 检查通过。未运行测试、启动 Minecraft、创建临时数据包或调用收费 API；tag 实际加载、资源重载和条件切换尚未游戏内验收。README-zh.md 未修改。

## 2026-10-28 搜索模块 ID 与开发默认资源

搜索功能模块在两端统一注册为 system:web_search，工作流、动作与卡片继续使用同名 ID，各自属于不同注册表；命令 /search 和页面内部标识不变。默认系统及 #system:default 模块 tag 移到 src/test-mod/resources/data/system，主源集不再提供这两份数据文件。发布环境须由服务端数据包定义可绑定系统，Java DEFAULT 仅作客户端展示备用。

执行 `compileJava testModClasses --console=plain` 和 `jar --console=plain` 均通过。直接读取新发布 JAR，确认默认系统 JSON 和默认模块 tag 均不在包中，搜索实现、提示词与卡片资源仍包含；开发资源输出包含两份默认数据。静态检查确认旧模块 ID 无残留，双语键集合一致，git diff --check 通过。未启动游戏、执行测试或调用收费 API；README-zh.md 未修改。

## 2026-10-29 助手原生命令树与独立缓存

阅读当前 Minecraft / NeoForge sources JAR 确认：客户端配置完成先发布 TagsUpdatedEvent.ClientPacketReceived，之后登录与命令包处理中的 mergeServerCommands 才触发 RegisterClientCommandsEvent。服务端 ReloadableServerResources 构造 Commands 则早于 updateComponentsAndStaticRegistryTags 发布 ServerDataLoad；客户端注册可复用缓存，服务端不能依赖相同顺序。Saturn RegistryCache 的 JAR 字节码确认标签更新重建和客户端登出清理。

AgentCommand、CommandRegistry 与客户端命令声明已接入 Brigadier；ClientCommandCache 缓存声明，游戏客户端注册复制缓存节点。助手异步合并原生服务端建议，任务和选择按钮的候选由对应模块实时提供；补全前显式过滤 requires，模块变更及 revoke/reset 后同步游戏命令树。普通文本语法未完整匹配时继续语义/模型路由，未知斜杠指令和明确语法错误不调用模型，参数构造及 Codec 失败仍直接拒绝。英文 literal 忽略大小写，选择1、选择 1、select 1 和裸数字保留。

执行 `compileJava testModClasses --console=plain` 编译通过；中英文各 490 个语言键且集合相同，更新文档的本地链接存在，`git diff --check` 通过。没有执行测试、启动 Minecraft、创建临时世界/数据包或请求模型服务。游戏内 Tab/IME、任务候选、模块切换、重载与重连仍需人工验收。


## 2026-10-30 1.21.1 修改同步至 26.1.2 master

先从干净的 1.21.1 工作区切换 master，再执行 git pull --ff-only，远端更新从 2c459f9 快进至 b6afc9d；随后同步 b601fd7..03ca46a 的功能差异，不带入版本降级、嵌入式 Saturn 或 1.21 专属兼容类。完整行为清单、目标 API 适配和人工验收项见 [跨版本同步记录](ftb-quests-porting.md)。

阅读目标 FTB Quests 26.1.2.8、Library 26.1.2.9、ApricityUI 26.1-1.2.6 的源码，以及实际 Minecraft 26.1.2 JAR 签名，确认服务端实例非 Optional、NeoForge 进度事件、Json5Object 奖励序列化、void claim 与自选原生标记顺序、原版 ItemStackTemplate、ComponentSerialization、Commands.hasPermission 和延迟 GUI 提取机制。图标的几何 / 文字 / 物品在 AUI 当前输出目标与变换中重放，tooltip 使用目标原版 API；这属于源码核对和编译验证，并非渲染验收。

实际执行 `gradlew compileJava testModClasses jar --console=plain` 成功：主代码与测试源集编译通过，产物 `build/libs/system-1.0-alpha.3.jar` 为 1,542,493 字节，小于 10 MB。直接检查 JAR，确认数据库、数据库优先问答、目标图标绘制类、卡片和提示词已包含；没有嵌入 Saturn、旧 WebSearchModule / WebSearchInput 或默认 web_search 配置 / tag 授权。已有依赖和版本配置未更改。

静态检查：双语各 513 个键且集合一致；23 份卡片 JSON 无重复键并可解析，节点 ID 均在 13 个节点类型中注册；动作与卡片数据分别为 21 / 26 个类型。冲突标记与旧版 API 引用检查、重复 import 检查和 git diff HEAD --check 通过。保留 master 的通知替换与手动切换逻辑；根目录两份 README 未改。临时补丁已删除，改动未提交、未推送、未暂存。

没有运行测试、启动 Minecraft、创建存档 / 数据包或请求收费 API。功能实际验收尚未进行，尤其是目标渲染流程、第三方 FTB 图标、持久化 / 玩家隔离、并发重载和本地优先联网授权；不能将编译通过视作运行通过。

## 2026-10-30 任务通知交互窗口

阅读并修改代码：通知任务列表由 actions 节点每页截取 10 项，上 / 下页和页码位于正文，与宿主底部确定按钮独立。翻页只更新通知持有的 CardPageState 和节点 DOM，不发送请求、不替换通知、不推进队列或重启播报；共用 CSS 保留原宽度与限高视口。QuestList 查看动作改为 PUSH，详情回复仍按原来源原位展示，保留本地返回栈；返回恢复原页且不重复播报列表，确定关闭整个窗口。卡片更新保留页码，移除会清理相应返回项；返回淡出期间的迟到回复不覆盖待返回内容，关闭来源后的迟到回复仍不能抢占其他通知。

服务端普通新解锁任务只有一项时直接构造 QuestOutput 详情并使用 unlocked 播报；多项时继续合并一条 QuestUnlockedOutput。FTB 完成事件循环仍逐个构建、发送独立完成通知，未合并任务完成项。上述结论来自源码阅读。

实际执行 `gradlew compileJava testModClasses --console=plain` 成功。静态检查通过：中英文各 517 键且集合完全相同，23 份卡片 JSON 可解析且无重复键，内置任务列表页大小为 10；新源文件和 `git diff --check` 无空白错误。没有运行测试、启动 Minecraft 或请求收费 API。尚未游戏内验收：边界页与大列表、详情返回和页码保持、动画期间迟到回包、忙碌时本地操作、语音返回、资源重载和临时隐藏后的恢复。

## 2026-10-30 26.1.2 分页与 ASR 补充移植

切换 master 后先执行 `git pull --ff-only origin master`，远端无新提交，基线为 `b97aa1c`。核对 AUI 26.1 / 1.2.6 的 Node / Element 源码后同步分页容器提前初始化；ASR 同步停止与入队互斥、EOF 复核、终态和计时代次、90 秒会话 / 15 秒收尾超时、取消及后台错误处理，并将正常结束的 EOF 实际移到设备关闭前。26.1.2 原生输入法、SavedDataType、依赖及网络协议未变。

实际执行 `gradlew compileJava testModClasses jar --console=plain` 成功；这里的 testModClasses 只编译开发源集，没有运行测试或游戏。静态 / JAR 检查通过：双语各 517 键且集合相同、23 份卡片 JSON 可解析，三个修改类均为 Java 25 字节码。产物 `build/libs/system-1.0-beta.1.jar` 为 1,548,823 字节，没有带入 1.21.1 的内嵌 Saturn、旧输入法 helper 或 IMBlocker Mixin。

没有请求收费 API、创建存档 / 临时配置或执行运行时探针。尚未实际验收：通知翻页点击及返回页码、Java Sound / SVC 录音结束、设备延迟关闭、取消 / 超时后的迟到回调、识别填入与自动发送。完整范围及来源审计见 [跨版本同步记录](ftb-quests-porting.md#2026-10-30-本轮补充移植)。

## 2026-11-03 滚动范围查询重复布局修正

阅读实际 ApricityUI 26.1 / 1.2.6 依赖源码确认：LayoutCommit.commit 已更新滚动元素的 scrollHeight，而原 AssistantScreen.scrollLimit 会额外调用 commitScrollMetricsFromLayout，重新查询聊天内容的子元素位置和自然尺寸；该调用及 setScrollTop 内部的 Box 查询均未开启 GeometryQueryScope。移除重复测量，读取范围与恢复位置分别使用几何查询作用域；AssistantPages 的手动布局提交也开启同样的样式 / 测量缓存。DOM 修改前后的查询分开，避免共享失效的矩形缓存。

实际执行 `gradlew compileJava testModClasses --console=plain` 成功；testModClasses 仅检查开发源集编译。静态检查确认 System 不再主动调用 commitScrollMetricsFromLayout，两处 scrollLimit 及滚动恢复均位于 GeometryQueryScope 内。未启动 Minecraft、运行测试、插入运行时探针或请求外部 API，未取得本次卡死时的线程堆栈；长聊天、嵌套卡片、滚动保持和实际卡死是否消除尚待游戏内验收。

## 2026-10-30 宿主信息双栏卡片（26.1.2）

核对实际 Minecraft 26.1.2 InventoryScreen、GuiEntityRenderer、EntityRenderDispatcher 的源码及 AUI 26.1 / 1.2.6 MinecraftElement、Mask、物品后端与深度区间。新增 system:player 节点和客户端 PlayerModelElement，服务端快照携带 UUID；player.json 用现有 row / column 组合模型和信息，在线列表折叠，统一 CSS 控制尺寸和窄屏换行。客户端每次按 UUID 解析玩家，不缓存实体，不修改真实实体，不新增依赖或服务端客户端类引用。

实际执行 `gradlew compileJava testModClasses jar --console=plain` 成功。静态检查通过：中英文各 519 键且集合相同，23 份卡片 JSON 无重复键、节点均在 14 个注册类型内、translate 键存在；JAR 包含新增节点 / 元素和更新后的玩家模板，产物 `build/libs/system-1.0-beta.1.jar` 为 1,555,739 字节，`git diff --check` 通过。

未运行测试、启动 Minecraft、创建临时世界或请求收费 API；本次只有编译、源码和资源检查。仍需游戏内确认完整 / 简约 / HUD 的缩放和裁剪、外观 / 装备层、模型与文字淡入淡出、窄屏换行、重生后重新解析玩家、模型不可用时的占位，以及缺少 FTB / SVC 的专用服务端加载。仅修改当前 master / 26.1.2，尚未移植到 1.21.1。

## 2026-10-31 完整模式分页、定位与语音计费同步

1.21.1 全部工作区改动已提交为 `c6fc689`，随后切换 master 并执行 `git pull --ff-only`，远端无更新。以 master `1ef5d80` 为基线，同步完整模式任务列表每页 10 项及按卡片 UUID 保存页码、共享 CSS 固定定位、模块页面节点缺失保护、语音价格表静态初始化和数字金额 Codec、语音账本异常隔离，以及识别完整堆栈日志。逐项核对时保留目标已有的玩家独立渲染状态、原生输入法、自然 EOF 发布顺序、版本和依赖。

实际执行 `gradlew compileJava testModClasses --console=plain` 成功，主代码和开发源集重新编译通过。静态检查通过：中英文各 519 键且集合完全相同，任务卡片页大小为 10，PRICE 在外层 Codec / INSTANCE 之前初始化，保留目标 NonNull 注解；源码无旧分页字段，全部 renderCard 调用已同步，`git diff --check` 无空白错误。

没有运行测试、启动 Minecraft、创建存档或请求收费 API；未修改两份 README，未推送远端。仍需游戏内验收分页与缩放后的页码、界面定位、自定义模板缺页，以及成功识别后的填入 / 自动发送和本地费用。当前进程若已经发生价格类初始化失败，更新后需重启客户端，F3+T 不能恢复该 JVM 类状态。

## 2026-10-31 语音记账阻塞修正

源码审计确认合成请求与解码仍在 `system-speech`，但首次创建本地账本会在调用线程读盘，且用量页读取快照会等待统计计算 / 写盘锁。LocalSpeechUsage 改为独立单线程串行处理初始加载、费用计算、归并与保存，界面只读 volatile 不可变快照；取消的语音任务不会取消账本队列，JVM 退出由独立 hook 做有时限的排空。

实际执行 `gradlew compileJava testModClasses --console=plain` 成功，主代码编译通过，开发源集编译任务为最新。只读抓取用户已启动的 1.21.1 进程时，渲染 / 服务端线程均在正常等待，没有取得卡顿时的堆栈；因此只能确认已移除上述阻塞路径，不能认定它就是用户这次卡顿的唯一根因。没有启动游戏、运行测试、创建存档或请求语音接口；并发记录、后台加载后的页面刷新、关闭时排空和实际卡顿改善仍待验收。当前仅修改 master，1.21.1 尚需同步。

## 2026-11-06 常驻卡片 HUD

本轮只执行 `gradlew compileJava processResources --console=plain`，结果为 `BUILD SUCCESSFUL`。同时用脚本检查 27 份卡片 JSON 无重复键、节点类型均已登记，中英文语言键集合一致（各 581 个），并刷新 `tools/card_editor.html` 的模板 / 语言 / CSS 快照。没有启动 Minecraft、创建存档或进行游戏内 HUD、图钉、FTB 任务刷新验收；位置、滚动、重登恢复及任务完成后的显示仍需客户端实测。

## 2026-11-06 页面样式 / 布局卡顿审计

按实际 ApricityUI 1.2.9 sources JAR 检查六类页面和共用节点，取消事件内整页布局提交、每帧重复提交和空闲 HUD class 重写，增加只读几何入口及消息 / 卡片增量协调；保留原生滚动缓动、通知动画及资源重载。详细原因与边界见[页面布局审计](ui-layout-audit.md)。

实际执行 `gradlew compileJava processResources --console=plain` 成功。静态检查确认六份 HTML 无重复 ID、全部引用共享 CSS；27 份卡片 JSON 无重复键且节点属于已注册的 23 个类型；双语语言键集合相同（各 581 个）。源码扫描没有旧 AssistantPages.commit、直接 Box / Size / Rect 测量或原生滚动范围探测调用；离线编辑器可执行脚本的 `node --check` 和 `git diff --check` 通过。

用户已关闭卡死的游戏，现有日志没有对应线程堆栈。本轮未启动游戏、执行运行时测试或添加探针；不能将编译 / 静态检查等同于卡顿已消除。任务翻页、长对话滚动与入场动画、输入法定位、连续通知、HUD 刷新及资源重载仍需游戏内验收。

## 2026-11-06 打开页面卡顿后续修正

用户反馈此前审计后每次打开仍卡数秒。读取新的 `run-test-client/logs/aui.log`：页面创建 175ms，首次布局 8786ms / 45 个元素；该首次布局记录为跨帧墙钟耗时，没有阻塞期间的线程堆栈。按实际 AUI 1.2.9 源码移除提取阶段的重复 prepareRender、初始化重复视口失效及无效的 manuallyRendered 切换；把非空草稿的光标范围设置延后到原生布局完成，空草稿保留默认光标。完整页面固定外壳改用定位边界，body 改块布局，减少嵌套 flex 自然测量。具体依据见[页面布局审计](ui-layout-audit.md)。

实际执行 `gradlew compileJava processResources --console=plain`，结果 `BUILD SUCCESSFUL`。没有启动 Minecraft、运行功能测试或添加探针，不能据此承诺实际页面打开耗时；仍需后续运行日志和交互验收。

## 2026-11-06 HUD 全屏背景修正

阅读实际 AUI 1.2.9 的 HTML.normalizeDocumentMarkup 确认：存在 head/body 时会重新拼接无属性的 html 标签，原通知与常驻卡片的 html class 被丢弃，以它为前提的透明 CSS 未命中，留下共享 body 的半透明全屏背景。两种 HUD 改为在 body 声明 system_hud；AssistantThemes 在实际根节点补同一标记，统一清空根背景色、背景图、阴影、背景滤镜及装饰伪元素，保留面板背景和聊天栏内交互。

实际执行 `gradlew compileJava processResources --console=plain` 成功，静态检查两份 HUD 的 body 标记与元素 ID、刷新编辑器 CSS 快照并通过 `git diff --check`。未启动 Minecraft 或运行功能测试，主题切换、资源重载和实际透明显示仍未游戏内验收。

## 2026-11-06 常驻卡片左右出入动画

阅读 Saturn 实际依赖的锚点 API 与 ApricityUI 1.2.9 的 Transition / Document 源码后，常驻卡片增加稳定包装层，左侧锚点左进左出、右侧锚点右进右出，居中锚点按屏幕半边决定。新增等待原生布局就绪，退出保留到 Transition 结束，进度 / 模板更新只替换内部内容；背包、F1、HUD 编辑器和退出世界继续立即卸载。仅修改 transform / opacity，使用像素位移与几何缓存作用域，不增加手动 prepareRender 或整页几何提交。

实际执行 `gradlew compileJava processResources --console=plain`，结果为 `BUILD SUCCESSFUL`。未启动 Minecraft、运行功能测试或添加探针。左右锚点、居中位置、最后一张退出、快速取消再固定、连续进度更新、滚动容器裁剪和资源重载后的实际效果尚待游戏内验收。

## 2026-11-06 切换界面保留常驻卡片退出动画

用户实测入场正常，但切换界面会瞬间消失。源码确认 tick、ScreenEvent.Opening 和鼠标前置处理均把非聊天 Screen 当作立即卸载条件。现将文档保留条件与卡片显示条件分开：新界面打开时立即禁用交互、清空显示目标并启动既有退出过渡，AUI persistent overlay 继续绘制，所有卡片退出后才卸载。退出途中返回游戏会恢复同一包装层并反向进入；其他界面下不新建卡片，F1 和退出世界仍立即清理。按 AUI 1.2.9 的 Base.drawPersistentScreenDocuments / Client.drawScreen 源码核对跨界面绘制路径，未增加额外渲染或几何提交。

本轮执行 `gradlew compileJava processResources --console=plain` 和 `git diff --check`；编译与静态检查通过。未启动 Minecraft 或运行功能测试。背包、暂停菜单、AUI 页面和 HUD 编辑器中的实际退出效果、快速返回时反向过渡及退出期间鼠标透传仍待游戏内验收。

## 2026-11-06 常驻卡片展示配置

ClientConfig 新增 `cardAnimations=true`、`cardsOnOtherScreens=false`，使用 lenientOptionalFieldOf 逐字段回退；默认文件与“设置 → 常规”开关同步。修改通过 ConfigInstance.set 标脏并立即保存，切换主题 / 界面模式保留新字段。关闭动画后 active 卡片直接切到最终样式，移除 animated class 取消已有过渡，inactive 卡片立即删除；开启其他页面显示后继续刷新快照，页面内保持被动，HUD 编辑器、F1 和登出保留各自边界。

执行 `gradlew compileJava processResources --console=plain` 成功，`git diff --check` 通过。静态检查默认 JSON、设置页 ID 唯一性及翻译引用，中英文各 585 个键且集合一致；同步编辑器的语言快照。未启动 Minecraft 或运行功能测试。仍需游戏内验收两个开关的四种组合、动画中途关闭、背包控件透传及重启后的设置保持。

## 2026-11-06 设置页打开卡死排查

用户确认刚打开设置页就卡死。读取最新 run-test-client 日志：21:03:05 页面创建 147ms，21:03:06 首次布局 35ms / 79 个元素，之后没有对应阻塞段。只读检查进程时游戏已退出，无法补取卡住时的渲染线程堆栈。核对 AUI 1.2.9 Transition.build，确认 transform 过渡会在样式计算时调用 Size.of；设置开关伪元素与多层自动高度 flex 组合存在提前测量路径。设置内容、标签页、分组和开关按钮改为块流，轨道与主题切换器固定局部定位，滑块改用 left 过渡；控件刷新跳过相同的 class / aria-selected / display / disabled，保留两个新配置和即时保存。详见[页面布局审计](ui-layout-audit.md#设置页打开卡顿的后续修正)。

执行 `gradlew compileJava processResources --console=plain` 成功，`git diff --check` 通过；静态检查设置页 ID、翻译引用、块布局与滑块过渡规则，中英文各 585 键且集合相同，同步编辑器 CSS 快照。未启动 Minecraft、运行功能测试或添加探针，未创建存档或改玩家配置。实际卡死是否消除尚未验收，仍需核对首次打开、三种标签、长表单滚动、开关动画、主题切换与不同缩放。

## 2026-11-06 卡片节点与字符串按钮重构

参考 KOOK 官方卡片文档的 button.click / value，按钮支持 link、return-val 和本地 local。CardActionRequest 只传来源版本及字符串；服务端先核对绑定、来源和限流，在游戏线程发布 CardButtonEvent，未处理事件按 CardEventValue 解码进入既有模块权限与业务校验。保留通知的代码定义底部按钮、确定、返回、PUSH / REPLACE 和语音入口；正文按钮独立处理禁用、忙碌与 HUD 交互，修正忙碌更新覆盖原有禁用状态的问题。

节点从 23 种整理为 18 种，按功能分包，以能力接口分离文本投影、纯消息资格、标题、children 与集合；grid / flex / groups 组合替代业务列表节点，共用 CardPagination。CardUiState 独立持有页码、分组、折叠，不再写入 Context。全部 27 份模板、图钉资源和 HTML 编辑器同步迁移；任务 pin 通过客户端构建事件注入，覆盖普通列表、多任务解锁列表、详情及常驻任务。延迟 CardText 只在实际节点读取时解析，避免生成未展示页的物品名称与奖池 tooltip。

实际执行 Gradle compileJava / processResources，结果 BUILD SUCCESSFUL；IDE 增量构建报告成功。静态核对 27 份模板、342 个节点的注册 ID、必填字段和每个占位符的循环深度；游戏与编辑器的 18 种类型一致，内嵌模板 / CSS / 翻译快照与源文件一致，中英文各 585 键且集合相同。Node.js --check 检查编辑器脚本语法，git diff --check 通过。

没有启动 Minecraft、运行测试、添加探针或创建存档。尚未游戏内验收：实际布局与页高、分组翻页后按钮索引、任务图标和 tooltip、图钉同步、通知进入详情再返回、历史按钮禁用、URL 确认、扩展事件字符串，以及缺少 FTB / 专用服务端的运行时类加载。旧节点别名与旧按钮协议不保留，客户端、服务端及自定义资源包需同步更新；网络版本号维持 1。

## 2026-11-06 物品图标偏移与宿主信息留白

核对实际 AUI 1.2.9 sources JAR：原生物品按元素中心绘制。单物品节点改用中心缩放，与居中的预留框保持一致；带名称的物品行保留图标单元尺寸，防止被文字挤窄。通用 card_flex 的基础 gap 提前到语义样式之前，并取消强制 align-items:center，避免覆盖行列布局；宿主模板直接使用 player_information 承载信息面板，移除多余的居中容器。同步 HTML 编辑器的模板与 CSS 快照。

执行 `gradlew compileJava processResources --console=plain` 成功；27 份模板 JSON、编辑器快照、共享 flex 样式顺序、双语键集合（各 585 个）、编辑器脚本语法及 `git diff --check` 通过。未启动 Minecraft 或运行功能测试，实际物品对齐、窄屏换行和宿主信息布局尚需游戏内确认。

## 2026-11-06 单页网格收缩

GridNode 根据筛选后的完整索引数决定预留行数：结果不超过一页容量时取向上取整的条目数 / 列数，多页时所有页均使用 max_row，末页不缩短。仍固定每格高度、保留置灰的单页翻页按钮；商店、抽奖、任务等共用此行为，不测量 DOM 或滚动范围。HTML 编辑器采用相同规则，相关布局文档同步更新。

执行 `gradlew compileJava processResources --console=plain` 成功；静态确认判断依据是分页前的完整筛选结果，编辑器资源快照一致，编辑器脚本语法和 `git diff --check` 通过。未启动 Minecraft 或运行功能测试；实际单页收缩、多页末页高度及分组切换仍需游戏内验收。

## 任务进度条动画

共享轨道从 3px 加粗为 6px。ProgressNode 使用原生 CardProgressElement 绘制填充，按 350ms 缓出插值缩放矩阵；CardUiState 只保存数字与单调时间，不持有 DOM。常驻卡片更新正文时保留 UI 状态，重复目标不重播，中途改目标从当前显示值继续。动画只请求重绘，读取已提交矩形，不逐帧写样式、修改布局宽度或调用 Size.of。核对实际 AUI 1.2.9 sources JAR 的原生元素绘制及矩阵 API；编辑器同步预览动画与 CSS 快照。

执行 `gradlew compileJava processResources --console=plain` 成功。未启动 Minecraft 或运行功能测试；游戏内首次显示、连续进度更新、完成 / 回退、主题与 GUI 缩放、移除及重新显示常驻卡片仍待验收。

## 2026-11-07 服务端与 FTB 运行时生命周期

AssistantServer 使用随 ServerStartedEvent / ServerStoppingEvent 创建和关闭的单个 INSTANCE，get(server) 读取一次 volatile 引用并核对服务器身份。FTB 查询、知识索引和通知跟踪由安装范围的共享 FtbQuestRuntime 持有，事件监听随实例注册和注销；ModuleManager 对共享运行时只 validate / tick / close 一次，安装失败也关闭已创建实例。多个 FTB 配置的玩家 Session 使用引用计数，最后一个关闭时才清理玩家查询和奖励引用。

实际执行 `gradlew compileJava processResources --console=plain`，BUILD SUCCESSFUL；`git diff --check` 通过。静态搜索未发现 MinecraftServer 键 Map、旧通知静态调用或服务器持有器 / Mixin 引用，Mixin 配置无改动。未启动 Minecraft、运行测试或调用收费 API；连续切换存档、多个 FTB 定义启停、通知事件去重及缺少 FTB 时的运行时加载仍待游戏内验收。改动仅在 master，未同步 1.21.1。

## 2026-11-07 ModuleContext 类型化服务 Map

ModuleContext 保留 AgentEngine 和模块注册表为构造器固定字段，通过 engine() / modules() 读取；其余七项内置服务及扩展服务改为 ContextType<T> 键、Map<ContextType<?>, Object> 与泛型 put/get。模块管理、内置模块及可选 FTB 适配器统一迁移调用；同次安装的服务 Map 共享，配置 id、工具缓存及 sharedRuntime 原有生命周期保留。ContextType 按对象身份区分，同名键不混用；内部转换集中在 get，业务调用不需要强制转换。

新增 ModuleContextEvent，由 ModuleManager 在服务端线程发布到游戏事件总线，时机为内置服务注入之后、配置分发及工具 / 工作流装配之前。监听器取得根上下文并同步 put 扩展服务，各配置继承同一 Map；固定字段不经过 put。事件位于管理器初始化 try 内，失败时仍清理已创建的共享运行时。原 AssistantRegistrationEvent 的装配后入口保留。

实际执行 `gradlew compileJava processResources testModClasses --console=plain`，BUILD SUCCESSFUL；静态搜索无 ContextTypes.ENGINE / MODULES 或无参构造残留，事件发布点位于所有安装循环之前，`git diff --check` 通过。testModClasses 只编译开发源集，未运行测试、启动 Minecraft 或新增探针，未修改配置或存档。游戏内扩展监听器、模块装配与可选依赖加载未作运行验证。
