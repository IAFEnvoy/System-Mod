# 卡片节点分类与职责设计

状态：已按本稿完成节点分类与模板迁移，2026-11-06。按钮随后按 KOOK 的 click/value 约定实施，以 [cards.md](cards.md#按钮与动作) 的现行格式为准；下文保留重构前的问题与方案对照。 本稿来自对重构前 23 种节点、27 份内置模板、CardRenderer / CardPresentation、物品渲染及 FTB 图标适配代码的阅读；仅编译和静态核对，没有启动游戏或测量重构后的性能。

目标是把布局、集合展示、文字处理和交互各自统一，让模板能组合功能。分类用于代码组织和编辑器导航，不为每个分类建立新注册表，也不要求全部节点继承一棵复杂的基类树。

现行格式仍以 [cards.md](cards.md) 为准；两端数据流沿用 [card-flow-design.md](card-flow-design.md)。

## 1. 当前问题与已经共用的部分

| 当前实现 | 阅读结论 | 整理方向 |
| --- | --- | --- |
| TextNode / TitleNode / ContextNode | 已共用大部分文字实现；TitleNode 仍重复声明文字 Codec 字段，ContextNode 主要只改变 CSS | 共享文字字段与格式化；保留标题语义，删除仅改变默认样式的节点 |
| ContainerNode / RowNode / ColumnNode | 已共用子节点遍历，row / column 主要只改变 CSS | 保留普通容器，把横纵布局合为 flex |
| CardGrid / CardPagination | 已统一容量、固定行区域、当前页创建和翻页；尚不能作为通用 JSON 网格使用 | 提供独立 grid 节点，继续复用一套分页实现 |
| OfferGridNode / PrizeGridNode | 均在 Java 中构建物品、名称、格子，还分别绑定商品或奖品 DTO | 格子内容交给 JSON，数据转换留给 CardData.buildCard |
| GroupedActionsNode | 同时负责分组、左栏、页码、格子、动作绑定及简约模式分支 | 分组交给 groups，网格交给 grid，操作交给 button |
| ItemsNode / AssistantItems | 物品绘制已经共用原版元素，但还夹带分组标题、说明、横排和另一套表格布局 | 留一个单物品节点，集合与标题使用通用布局和文字节点 |
| IconsNode / QuestIconNode | 都通过 FTB 客户端适配绘制图标，分别负责列表和单任务；解析可选工厂的逻辑重复 | 合为单图标适配节点，列表通过通用集合展开 |
| ItemSourcesNode | 同时处理来源分类、分类按钮、分页和配方槽位 | 分类与分页通用化，只保留单条配方的专用展示 |
| ButtonNode / ActionsNode / QuestPinNode | 点击入口、交互状态和按钮 DOM 有重复；图钉节点还自行监听 tick、扫描文档更新状态 | 统一按钮和动作绑定，图钉状态由原有缓存维护 |
| CardTemplate.textOnly / CardPresentation | 通过具体 TextNode 类判定纯文本，通过 TitleNode 类判定标题；总展示逻辑知道具体元素 | 通过能力接口提供文字投影、纯文本资格和卡片标题 |

已经合理的公共逻辑继续保留，不再套一层功能相同的 Manager / Registry / Renderer。

## 2. 节点分类与目标清单

建议整理为下列 **18 种节点**。减少数量不是硬性指标；保留有实际渲染语义的类型，避免为业务目录或默认 CSS 单独造类型。

下表省略 `system:` 前缀，全部是目标设计，不能直接用于当前版本。

| 分类 | 节点 | 单一职责 |
| --- | --- | --- |
| 文字 | text | Minecraft / FTB 格式文本、翻译、参数；可以作为纯文本消息 |
| 文字 | markdown | Markdown 解析与受控展示，不改变 FTB 文本处理 |
| 文字 | title | 标题层级，以及提供可提升到卡片顶部的标题 |
| 图像与模型 | image | 资源包内的图片；不自行下载网络图片 |
| 图像与模型 | item | 一个物品预览、数量和原版 tooltip |
| 图像与模型 | player | 一个玩家模型的皮肤、装备、朝向、缩放等 |
| 图像与模型 | ftb_icon | 一个 FTB 任务、目标类型或奖励类型的原生图标及 tooltip |
| 布局 | flex | direction、wrap 和子元素排列，可选集合数据源 |
| 布局 | grid | 格子布局，可选集合数据源及本地分页 |
| 容器 | container | 普通流式容器，提供真实的背景、边框或内容分区 |
| 容器 | groups | 分组选项、选中项和对子区域的条目筛选 |
| 容器 | details | 本地展开 / 收起子内容 |
| 交互 | button | 文字或图标按钮、受信任 callback、选中与禁用状态 |
| 交互 | link | URL 展示与原版打开链接流程 |
| 装饰与状态 | divider | 分割线 |
| 装饰与状态 | progress | 数字进度的显示，不判断任务业务状态 |
| 专用展示 | player_information | 展示 Saturn 已处理的信息，仅以 importance 筛选 |
| 专用展示 | recipe | 展示一条配方快照，保留材料位置、候选项和产物 |

`image` 与 `item` 不合并：原版物品数量、组件、tooltip 和原生绘制不应降格为普通纹理。`ftb_icon` 与 `image` 也不强行合并，FTB 图标可能不是一张静态纹理。

`player_information` 保留独立节点及现有 importance 约定，不把 Saturn 的求值和格式化再实现一遍。`recipe` 的有序槽位、空槽与候选材料也有实际语义，不拆成大量业务相关的 JSON 条件。

## 3. 按能力细分接口

### 3.1 根接口只保留所有节点都需要的行为

沿用 CardNode 的 Codec dispatch、默认样式和渲染入口。文本投影从根接口移到有该能力的接口，不再要求模型、进度条等元素提供空 text 实现。

以下仅表示拟议签名，不是本次代码修改：

```java
interface CardNode {
    MapCodec<? extends CardNode> codec();
    String css();
    @Nullable Element render(CardRenderer.Render view);
}

interface TextProjectingNode extends CardNode {
    String text(CardRenderer.Text view);
}

interface PlainMessageNode extends TextProjectingNode {
}

interface HeadingNode extends TextProjectingNode {
    Optional<String> cardHeading(CardRenderer.Text view);
}

interface ChildrenNode extends CardNode {
    List<CardTemplate.Node> children();
    // Share child traversal through a default method; do not create another context.
}

interface CollectionNode extends ChildrenNode {
    String source();
    String filter();
    // Share original-index selection; each layout still creates its own element structure.
}
```

接口按实际能力组合，分类本身不成为标记接口。例如 groups 同时拥有子节点、集合处理和文本投影，但不需要额外的 ContainerKind 枚举或 isContainer()。

只为确有共享行为的部分提供 default 方法，例如子节点遍历、集合原始索引解析。不要为了实现接口给节点增加无意义字段，也不为每个方法配一个新的包装对象。

| 元素 | 能力组合 |
| --- | --- |
| text | PlainMessageNode，因而也具备 TextProjectingNode |
| title | HeadingNode，因而也具备 TextProjectingNode；不继承 PlainMessageNode |
| markdown / link / player_information / recipe | TextProjectingNode，各自定义怎样得到导出文字 |
| container / details | ChildrenNode + TextProjectingNode；共用子节点文字遍历 |
| flex / grid / groups | CollectionNode + TextProjectingNode；共用原始索引和子模板遍历 |
| button | ChildrenNode + TextProjectingNode；只投影标签，不触发 callback |
| image / item / ftb_icon | 可提供替代文字、物品名或图标标题的文本投影，不取得纯文本消息资格 |
| player / divider / progress | 仅实现需要的展示契约，不因根接口要求而提供空 text 方法 |

ChildrenNode 的共用文字遍历保留 text_separator；row 原先的同行导出和 column 的分行导出不会因为合为 flex 而丢失。集合节点复用索引遍历，但仍自行决定文本投影的顺序和分组标签。

### 3.2 纯文本消息资格与文本导出分开

`PlainMessageNode` 表示该元素可以直接作为一条普通文本消息。初始只有 TextNode 实现它；Markdown、标题、按钮和容器即使能导出文本，也不自动取得该资格。

宿主只判断通用条件：

1. 模板正文恰好一个顶层节点，且没有 repeat。
2. 该节点实现 PlainMessageNode。
3. CardView 没有任何按钮，包括 FOOTER 按钮。
4. 没有需要独立展示的标题或不同于正文的摘要。

满足后调用接口的 text()，直接生成消息文字；否则渲染卡片。仍然保留“有按钮就是卡片”的规则，不因节点藏在容器里或暂时隐藏而猜测为纯文本。

因此删除 CardTemplate 对 `getClass() == TextNode.class` 的判断。自定义节点若确实满足纯文本契约，可以直接实现接口，无须修改总渲染器。

**TitleNode 不再继承实现 PlainMessageNode 的 TextNode**，否则会意外继承纯文本资格。两者用一个共享的文字值对象复用字段、Codec 和格式化，而不是依赖继承取得展示语义。

### 3.3 标题由节点提供，提升位置由宿主决定

TitleNode 实现 HeadingNode：在自身 level 为 1 且文本非空时返回卡片标题，其他情况返回 empty。具体文字解析、层级判断留在节点内。

CardPresentation 只在顶层可见、非 repeat 的节点中寻找第一个提供标题的 HeadingNode，并把它提升到正文之前。嵌套标题仍在原位置展示；总展示逻辑不再引用 TitleNode 的具体类或 level 常量。

宿主仍负责选择一个标题以及避免重复绘制，这是卡片级行为，不下放给每个节点。

### 3.4 通用渲染器收缩到遍历与分派

CardRenderer 只负责共用的 style / when / unless / repeat、占位符和循环索引，以及调用节点接口。文字导出只判断 TextProjectingNode；模型等无文本能力的节点自然跳过。

各类元素负责自己的行为：文字负责格式化，容器负责渲染子节点，布局负责排列，分组负责筛选，按钮负责通过共享绑定方法绑定操作。总渲染器里不增加按商品、任务、奖品或节点 ID 切分的大 switch。

保留现有 CardRenderer.Render / Text 作为调用参数；不再引入多层 RenderRequest / RenderContext / NodeContext。

## 4. 通用字段与组合规则

CardTemplate.Node 继续是唯一的共用字段封装，不把以下字段复制到每个节点的 Codec：

| 字段 | 设计 |
| --- | --- |
| type | 沿用现有正式注册表及 MapCodec dispatch |
| style | 沿用单字符串或字符串列表；属于 CSS 类名，不是任意内联样式代码 |
| when / unless | 读取状态表；不根据任意对象类型猜测真假 |
| repeat | 普通节点的轻量重复，不自动创建额外容器 |
| id | 新增可选的模板内稳定标识，用于页码、分组、折叠等本地状态 |

text / title 共享一个文字值对象，包含当前 text / translate / args / inline 字段，Codec 字段仍平铺，不引入 `content: {value: {text: ...}}`。Markdown 只共用占位符等基础工具，不被迫经过 Minecraft 颜色格式化。

样式差异通过 style 表达，例如原 context 改为 `text + card_context`。不保留只换默认 CSS 的 Java 类型和注册项。

不新增 TooltipNode、PinNode、商品格子节点或按钮容器节点。tooltip 是对应图像 / 按钮的属性；图钉是 button 的一种样式；格子内容由子节点组成。

## 5. 一个平铺上下文，集合使用原始索引

保留 CardContext 的 states、parameters、callbacks 三张表和 get(id, Class) 类型检查，整张卡片共用一份。不创建子上下文，不反射读取业务对象字段，不把所有 DTO 转成 Map 或裸 JSON。

CardData.buildCard() 通过现有 Builder.list 约定写入集合数量和按索引展开的参数。例如：

```text
offers = 2
offers.0.label = Component
offers.0.previews = 1
offers.0.previews.0.item = ItemPreviewOutput
offers.1.label = Component
offers.1.previews = 2
```

`source: "{{offers}}"` 指向这一集合的数量，不再同时传一份 List<Offer> 给通用网格解析。叶子节点仍可直接读取有类型的 ItemPreviewOutput、配方、图标引用等值；“平铺”不等于把每个对象拆成字符串。

按钮 callback 仍由客户端针对可信 CardButton 或本地逻辑生成，键可以是 `offers.0.open`。普通服务端快照不能传 Runnable，模板也不能构造任意可执行动作。

CollectionNode 共用以下规则：

- 先根据 source 得到原始索引，再应用可选 filter、上层分组筛选，最后分页。
- filter 是逐条状态绑定；子节点的 when 只控制子节点，不隐式删除整个格子。
- 筛选和分页不重新编号，`{{offers.*.label}}`、对应 callback 和业务 ID 始终使用同一个原始索引。
- 每渲染一条记录只向现有循环索引追加一个索引；嵌套集合的多个 `*` 按外层到内层解析。
- 上层传递的筛选范围仅属于当前渲染分支，以 source 标识和原始索引表示，不修改 CardContext 或兄弟节点的数据。
- 同一节点不同时设置 source 和 repeat，避免两套重复机制对同一条目重复展开；嵌套集合在子节点声明。

build() 时复制状态表和参数 / callback 表，保持已有对象的不可变快照语义。页码、折叠和缓存待确认状态不通过修改这份快照生效。

## 6. 布局、分页和分组

### 6.1 flex 和 container

container 仅提供普通流式分区；flex 用 direction 表示 row / column，wrap 控制换行。两者共用 ChildrenNode 的遍历，分别设置实际需要的布局。

flex 可以没有 source，此时按顺序渲染 children 一次；有 source 时以 children 作为每条数据的模板。集合模式的可选 page_size 用于较长的按钮 / 文字列表，并复用 CardPagination。

只需要条件或 repeat 时直接使用节点通用字段，不自动套 container。确有背景、裁剪或布局作用的 DOM 保留，不能为减少节点数量破坏布局。

### 6.2 grid

grid 使用同一套集合规则；没有 source 时 children 是静态格子，有 source 时 children 是每格内容。

| 字段 | 含义 |
| --- | --- |
| source / filter | 可选集合及逐条筛选 |
| columns | 完整模式列数，沿用默认 6 |
| compact_columns | 紧凑模式列数上限，默认 3；实际不超过 columns |
| max_row | 一页最多行数，沿用默认 3 |
| cell | 格子高度，沿用默认 104px |
| children | 静态格子或每条记录的内容模板 |

集合模式的容量由实际列数 × max_row 得到。筛选后只有一页时按实际行数收缩高度；两页及以上固定预留 max_row 行，末页也不收缩，防止翻页跳动。分页栏始终保留，按钮按实际页码置灰；空集合不生成网格。静态模式不生成分页栏，也不强行补满三行。

只创建当前页的 DOM、图标和 tooltip。保留当前 minmax(0, 1fr) 的列轨道以及按行高计算区域的方式，不为了“自动分页”先测量所有条目的高度。超出格子的长文本按共享 CSS 截断 / tooltip 展示。

grid 只创建一个必要的格子外壳，不知道报价、概率、领奖或任务完成规则，也不自动把整个格子绑定到某个 result_actions 下标。

### 6.3 groups

groups 根据 source、group、group_label，以及可选 section / section_label 建立分组选项，children 定义右侧或下方内容。orientation 决定左侧栏或顶部页签。

任务列表不提供固定组选项，只从本次查询结果生成章节和章节组，保持第一次出现的顺序，不补空章节，不读取完整的客户端任务目录。

需要固定分类时可提供 options（分类 ID、标签），例如物品来源的配方 / 掉落 / 标签；没有结果的固定分类置灰。这个字段只描述显示选项，节点内不写死三种来源业务。

分组只把选中组的原始索引传给子区域。相同 source 的 grid / flex 消费该筛选范围；它们仍负责自己的布局和分页。切组与翻页都只重建对应区域，不替换消息，不进入通知队列，不发网络请求。

### 6.4 本地状态只维护一份

把现有 CardPageState 扩展或改名为 CardUiState，集中保存页码、选中分组和折叠状态，不再让每个元素自行保留一套不一致的 Map。

状态由卡片宿主持有，并通过 Render 参数传入，不再作为 `local_pages` 混在业务参数表里。状态 key 使用卡片身份、节点 id（未指定则用模板路径）及循环项 / 分组标识，避免同一卡片中两个网格因共用 source 而串页。

切组可以恢复该组上次页码；条目减少时夹到有效页；通知 PUSH / 返回保留原视图状态；卡片销毁、连接退出时清理。数据更新保留仍然有效的状态，但不能保留过期按钮 callback。

CardPagination 保留为共享实现，不再另注册一个 pagination 节点来包住 grid。按钮列表和网格可以共用分页逻辑，但各自只切片一次。

## 7. 展示与交互细节

### 7.1 图像、物品和 FTB 图标

image 读取本地资源 Identifier 或上下文中的资源引用，提供尺寸 / 适配显示参数；首版不引入远程图片下载、图片缓存或额外网络接口。具体 AUI 图片 API 在实施时核对实际依赖源码。

item 读取单个 ItemPreviewOutput，保留 ItemStackTemplate 的组件、数量文字和原版 tooltip。额外说明按 Component 追加到 tooltip，不再把标签、说明分组和另一套网格塞进单物品节点。物品名称要显示在外面时由 text 单独布局。

ftb_icon 读取单个有类型的 FTB 图标引用：来源区分 quest / task / reward，携带对应任务 ID 或类型 Identifier、标题、数量和 tooltip。它是展示 DTO，不携带 FTB Icon 实例，不提供“读取全部任务”的能力。

现有 create / createQuest 的差异收敛到一个 FTB 客户端适配入口。任务图标仍尊重 Quest.getIcon() 的实际配置，不能误用目标类型图标替代。未安装 FTB 时不加载适配类，缺失图标按约定提供文本或空图标回退；公共模板和必须加载的数据不依赖可选类。

图像 / 原版物品 / FTB 图标可以共用尺寸、数量与 tooltip 的小型辅助方法，但不为所有媒体建立第二套节点注册表。

### 7.2 button 统一交互（原方案，现已改为 click/value）

button 支持 text 或 children、可选本地资源 icon，以及 callback、selected、disabled 的状态绑定。没有文字的图标按钮必须提供可读标签。多个按钮使用 flex / grid，不再依赖专用 actions 节点。

统一绑定入口复用 CardController、活动版本检查、忙碌限制和 view.interactive()；不能因为换用通用 button 丢失 ActionsNode 现有校验。本节原方案只引用已有 callback；实施时按后续要求改为 click/value：可发送服务端字符串，由服务端事件或内置解析器重新验证权限后执行。

把每格主操作和 pin 做成同一个格子中的同级按钮，不在一个原生 button 里面嵌套另一个 button。图钉点击阻止冒泡，避免同时打开任务。

pin 的已固定 / 待确认状态继续由 DisplayCardCache 管理；客户端展示绑定向对应按钮发布增量状态更新。删除 QuestPinNode 自己的 tick 与全 Document 扫描，不另造一套 pin 存储。状态同步不以重建整张卡片或整个历史列表实现。

link 保留独立节点，因为它有原版 URL 偏好与确认流程。details 和分组 / 分页控件虽然可以共用按钮创建工具，但不伪造一条业务 CardButton 来完成纯本地 UI 操作。

### 7.3 通知宿主保留的职责

正文可以使用上述节点；通知的 FOOTER 操作、返回、默认确定和语音别名仍由宿主结合可信按钮定义管理，不由 JSON 随意生成。

通知及常驻卡片的外壳、Saturn HUD 位置、宽度和高度上限仍由各自宿主管理，不因 grid 或 groups 的加入而自行撑宽或覆盖全屏。

普通 button 需要在通知正文使用时，执行和当前 BODY 动作相同的授权与交互检查，不沿用当前 ButtonNode 只允许 full / simple 的限制。不同模式哪些业务按钮可出现，由统一展示入口投影；FOOTER 不重复出现在正文集合。

默认确定始终存在，忙碌时仍可关闭；下一个 / 下一页 / 关闭的现有识别别名不丢失。分页控件不混入业务按钮队列，也不因 TTS 结束而自动切下一条通知。

业务操作继续使用既有 CLOSE / WAIT / REPLACE / PUSH；需要立即显示的详情替换当前槽位或进入可返回视图，不能重新排到通知队列尾部。翻页、切组和折叠不触发这些业务导航行为。

### 7.4 recipe 和 player_information

recipe 只接收一条已准备的配方 ItemSource 快照。保留有序材料、3×3 填空规则、重复材料、候选项 tooltip、工作站和产物；不承担“配方 / 掉落 / 标签”的顶层分类和分页，不查询服务端 RecipeManager。

player_information 保持只接受 importance，并展示 Saturn 已采集、求值和格式化的行，严格显示 importance 大于阈值的内容。图形展示和文本导出读取同一份数据，不重复计算 Saturn 信息。

两者实现 TextProjectingNode 以供文字导出。TTS 仍使用业务显式提供的 SpeechContent，不把整个节点树、分页栏或 tooltip 自动读出来。

## 8. 模板示例（拟议格式）

### 8.1 商店网格

CardData 先准备 offers 的数量、label、previews 和可信 open callback。最多展示几个预览由展示数据或子模板决定，不在通用 grid 中硬编码。

```json
{
  "type": "system:grid",
  "id": "shop_offers",
  "source": "{{offers}}",
  "columns": 6,
  "compact_columns": 3,
  "max_row": 3,
  "cell": 104,
  "children": [
    {
      "type": "system:button",
      "style": "offer_cell_content",
      "click": "return-val",
      "value": "{{actions.*.value}}",
      "children": [
        {
          "type": "system:flex",
          "direction": "row",
          "source": "{{offers.*.previews}}",
          "children": [
            {
              "type": "system:item",
              "item": "{{offers.*.previews.*.item}}"
            }
          ]
        },
        {
          "type": "system:text",
          "style": "offer_cell_label",
          "text": "{{offers.*.label}}"
        }
      ]
    }
  ]
}
```

奖品网格使用同一个 grid，但 children 不带购买按钮；概率由 tooltip / text 展示。页面可以改变格子结构，无须修改 OfferGridNode 或 PrizeGridNode 的 Java DOM 构建代码。

### 8.2 任务分组与图钉

```json
{
  "type": "system:groups",
  "id": "quest_chapters",
  "source": "{{quests}}",
  "group": "{{quests.*.chapter_id}}",
  "group_label": "{{quests.*.chapter}}",
  "section": "{{quests.*.chapter_group_id}}",
  "section_label": "{{quests.*.chapter_group}}",
  "orientation": "vertical",
  "children": [
    {
      "type": "system:grid",
      "id": "quest_results",
      "source": "{{quests}}",
      "columns": 6,
      "max_row": 3,
      "cell": 104,
      "children": [
        {
          "type": "system:button",
          "style": "quest_cell_content",
          "click": "return-val",
          "value": "{{actions.*.value}}",
          "children": [
            { "type": "system:ftb_icon", "icon": "{{quests.*.icon}}" },
            { "type": "system:text", "text": "{{quests.*.title}}" }
          ]
        },
        {
          "type": "system:button",
          "style": "quest_card_pin",
          "icon": "system:textures/gui/pin.png",
          "label": "{{quests.*.pin_label}}",
          "click": "local",
          "value": "{{quests.*.pin}}",
          "selected": "{{quests.*.pinned}}",
          "disabled": "{{quests.*.pin_pending}}"
        },
        { "type": "system:progress", "value": "{{quests.*.progress}}" }
      ]
    }
  ]
}
```

这里没有新的分层参数表：groups 传递筛选索引，grid 追加原始循环索引，所有叶子仍读同一份 CardContext。图钉资源路径仅为设计示例，本次不新增图片资源。

## 9. 当前 23 种节点的对应关系

| 当前节点 | 目标 |
| --- | --- |
| text | 保留；实现 PlainMessageNode，文字字段与 title 共用 |
| markdown | 保留；实现 TextProjectingNode，不取得纯文本消息资格 |
| title | 保留；实现 HeadingNode，删除对 TextNode 的语义继承 |
| context | text + card_context 样式 |
| container | 保留；实现 ChildrenNode，不承担其他布局职责 |
| row | flex，direction=row |
| column | flex，direction=column |
| details | 保留；ChildrenNode + TextProjectingNode，本地状态统一维护 |
| link | 保留；统一交互检查和 URL 打开逻辑 |
| divider | 保留 |
| items | flex / grid / repeat + 单个 item；标题和说明由 text / tooltip 表达 |
| item_sources | groups + flex / grid + recipe / text；业务 CardData 与查询接口继续存在 |
| offer_grid | grid + item / text / button |
| prize_grid | grid + item / text / tooltip |
| player | 保留，不强行提供文本投影 |
| player_information | 保留，仍然完全使用 Saturn 结果 |
| icons | flex / repeat + 单个 ftb_icon |
| quest_icon | 单个 ftb_icon，引用种类为 quest |
| quest_pin | button + 图钉样式、选中状态和受信任 callback |
| progress | 保留 |
| actions | flex + button，必要时使用共用分页；文字标签模板放在按钮 children |
| grouped_actions | groups + grid + button / ftb_icon / progress |
| button | 保留并统一文字、图标、内容和状态绑定 |

新增 image、item、ftb_icon、flex、grid、groups、recipe；被替代类型不再保留平行实现。无需为了旧开发模板维护两套渲染路线。

## 10. 实施范围和顺序

1. 先补能力接口和共用文字字段，实现节点自己的文字投影、纯文本资格与标题输出；删除总展示逻辑中的具体类判断。
2. 提取现有物品 / FTB 图标的单元素能力，补 image；保持原版绘制、tooltip 与可选类隔离。
3. 将已有 CardGrid / CardPagination 接为通用 grid / flex 集合能力，再拆 groups；状态统一在原有页码状态对象中维护。
4. 统一 button 和可信动作绑定，将 pin 的展示更新接回 DisplayCardCache 的版本维护；保持通知按钮与导航行为。
5. 调整各 CardData 的纯展示参数，迁移全部 27 份内置模板；把业务对象列表解析从通用节点中删掉。
6. 拆 ItemSourcesNode，保留单配方节点；删除旧节点注册与代码，不能只留一层转发到新实现。
7. 同步 JSON 编辑器的节点分类、字段、示例上下文和预览，以及 cards / registries / 布局文档。分类只影响导航，不改变命名空间和正式注册表数量。

代码可按 `node/text`、`node/media`、`node/layout`、`node/container`、`node/interaction`、`node/integration` 组织；divider / progress 可同放展示包。能力接口留在 card 包。每个实现独立文件；不为对应目录再造一个注册中心或转发层。

服务端仍决定业务快照、权限、查询范围、支付与发奖；客户端负责展示和已有本地交互。公共 DTO / buildCard 不引用 AUI、Minecraft 客户端实例或 FTB 客户端类，正式节点注册沿用现有装载方式，静态初始化不能访问客户端运行时。

## 11. 后续验收重点

本节是实施后的检查清单，本次没有执行：

- 自定义 PlainMessageNode 可以作为纯文本显示，不需要改 CardTemplate / CardPresentation；同一内容附带按钮后仍是卡片。
- TitleNode 不继承纯文本资格；一级顶层标题只显示一次，嵌套标题不被提升，Markdown 仍保留自己的布局。
- 非 TextProjectingNode 不参与文字导出；容器 / 集合导出使用自然顺序的全部内容，不由当前分页、折叠或选中分组决定是否遗漏。TTS 仍独立。
- 两个同源网格不串页；切组、最后一页、条目减少和通知返回均保持合理状态，过滤后的按钮对应原始记录。
- 通知保留默认确定、语音别名、BODY / FOOTER 区分和忙碌限制；没有 TTS 完成后自动切换。
- 主按钮与图钉互不误触；任务固定在重登录后恢复，pending 与已固定状态能跨现有卡片更新。
- 物品仍有原版 tooltip；FTB 任务自定义图标不被类型图标替代；配方空槽、重复输入和候选材料不丢失。
- 玩家信息只按 importance 筛选；原有模型参数继续有效。
- 只挂载当前页；切页 / 切组不重建整个聊天历史、不手动提交整页几何，不在布局回调中测量 scrollLimit 或重复 prepareRender。
- 未安装 FTB、专用服务端、资源重载及缺失模板不触发可选客户端类加载；新增资源与数据不会把 FTB 变为必装依赖。

按照 AGENTS.md，实际实施时可以先做编译和静态检查；游戏运行与功能验收需按用户当次要求执行，不能把编译成功写成功能已验证。
