# 客户端 JSON 卡片

数据流为：服务端生成业务快照 → `CardUpdate` 携带 `CardData` → 客户端调用 `buildCard()` → `CardBuildEvent` 扩展 Builder → `build()` 冻结为 `CardView` → JSON 布局 → 明确的本地或服务端按钮动作。完整模式、简约模式和 HUD 共用构建、版本和交互入口。架构说明见[卡片与消息数据流](card-flow-design.md)，注册入口见[注册表速查](registries.md)。

编辑布局可直接打开 [tools/card_editor.html](../tools/card_editor.html)。它提供离线节点树、属性／JSON 同步编辑、示例 Context 和浏览器近似预览，操作与范围见 [Card JSON 离线编辑器](card-editor.md)。

## 数据与渲染绑定

公共契约位于 `com.iafenvoy.system.data.card`：`CardData` 提供 `codec()` 和 `buildCard()`，不加载客户端、AUI、服务端运行时或可选模组类。业务 DTO 仍在原来的输出包；具体客户端处理位于 `data.card.client` 和 `screen.card`。

`SystemCardDataTypes` 向两端共享的 `system:card_data_type` 注册每个实现自己的 MapCodec。根 `CardData.CODEC` 按 `type` 分派，数据直接平铺，没有 TypePayload 包装。数据 ID 与模板 ID 可以不同，不需要额外的渲染映射或客户端行为注册。

```java
@Override
public CardBuilder buildCard() {
    return CardBuilder.template(Identifier.parse("example:reward"))
            .summary(this.title)
            .put("result_items", this.items)
            .speech(this.speech)
            .serverButton("claim", "system.action.claim",
                    Identifier.parse("example:claim"), Map.of("reference", this.reference));
}
```

`buildCard()` 只读本 DTO 的字段，不查权限、背包、任务状态或模块表，不发包、请求模型或播放声音。服务端提前准备业务引用、下一页游标、报价及可操作状态。`CardBuilder.build()` 得到不可变 `CardView`：模板、标题翻译键、summary、Context、按钮列表和 SpeechContent。Component 保留翻译键及样式；实际翻译、物品堆创建和名称解析只发生在客户端。

`ClientCards` 保存连接内按 ID 的最新数据、版本和活动状态。`CardPresentation.of(update, messageKey, mode, actionOffset)` 取最新快照，解析 Component，保留按节点读取时才解析的 CardText，补充显示模式、按钮标签及受控 callback，然后读取 `assets/<namespace>/cards/<path>.json`。缺少模板时回退到 summary 和已有按钮，不展示 DTO 的原始 JSON。

知识问答使用 `KnowledgeAnswerOutput` / `system:knowledge_answer`，正文显示回答、本地来源默认折叠，包含标题、分类和命中段落；只播报回答或原文提示，不提供网页链接或任务操作。联网回退继续使用独立的 WebSearchOutput / web_search 模板；两种卡片都只展示服务端准备的结果。

## 构建事件：给现有卡片补充参数

[`CardBuildEvent`](../src/main/java/com/iafenvoy/system/event/client/CardBuildEvent.java) 在客户端 `NeoForge.EVENT_BUS` 同步发布，不走 mod event bus，不可取消。统一入口位于 `ClientCards`：先调用原 `CardData.buildCard()` 填好内置内容，再发布事件，最后 `CardBuilder.build()` 复制出不可变快照。公共 CardData / CardBuilder 不负责发布事件，专用服务端不会走这个客户端入口。

| 访问器 | 内容 |
| --- | --- |
| `ref()` | 当前卡片 UUID 和 revision，可区分消息与版本 |
| `data()` | 服务端传来的类型化 CardData，按实现类筛选需要扩展的卡片 |
| `builder()` | 已有内置参数的可变 CardBuilder，使用 put / enable / list 添加展示参数 |

例如，其他模组在客户端监听并扩展宿主信息卡片：

```java
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.event.client.CardBuildEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(Dist.CLIENT)
public final class ExampleCardEvents {
    @SubscribeEvent
    public static void build(CardBuildEvent event) {
        if (!(event.data() instanceof PlayerSnapshotOutput player)) return;
        event.builder()
                .put("example_host", Component.translatable("example.card.host", player.name()))
                .put("example_level", player.experienceLevel())
                .enable("example_extra", true);
    }
}
```

`example.card.host` 的语言键由扩展模组提供。也可以在这里读取该模组已同步到客户端的数据，写入自己前缀下的参数；Component / CardText 会继续经过客户端格式处理（CardText 按节点需要延迟解析）。资源包覆盖 `assets/system/cards/player.json`，在需要的位置加入节点：

```json
{
  "type": "system:text",
  "when": "example_extra",
  "text": "{{example_host}} · Lv.{{example_level}}"
}
```

事件覆盖 SHOW、携带新数据的 REPLACE、历史恢复和未缓存卡片的按需构建；读取已有 CardView、过期 / 重复更新、REMOVE 不触发。完整、简约和通知共享同一份扩展快照，资源重载只重读 JSON，不会重新采集参数。按需构建可能重复触发，因此监听器应只补充展示内容，不把它当成发奖等业务事件；仅在回调期间修改 Builder，不保留引用。新参数不自动持久化或上传服务端，服务端业务和权限仍通过原有请求处理。

## 普通消息约定

仅当模板正文恰好是一个 `system:text` 节点、没有 repeat、没有任何 CardButton、没有额外可见标题或独立摘要内容时，渲染成普通 `message_text`，不生成卡片外框。文字可以换行或带格式；无需单独登记普通文本渲染器。

**有按钮仍然是卡片**，包括使用 `system:text` 模板的绑定提示。物品、容器、分割线、多个节点或其他布局元素也保留卡片。单文本模板携带按钮时，宿主自动补上动作区，按钮不会丢失。

```json
{
  "show_title": false,
  "body": [{"type": "system:text", "text": "{{summary}}"}]
}
```

内置 `TextCardData` 使用上述 `system:text` 模板。判断集中在 `CardPresentation.plainText()`，各界面不再自行猜测消息类型。HUD 的统一确定按钮属于通知宿主控制，不改变数据本身的布局约定。

## 一个卡片，一份 Context

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| states | `Object2DoubleMap<String>` | 0 为关闭，非 0 为开启；也可存循环次数 |
| parameters | `Map<String, Object>` | 平铺的文字、数值、布尔、物品及其他展示参数 |
| callbacks | `Map<String, Runnable>` | 客户端控制器绑定的可信操作 |

整张卡片共用一份 Context，没有子 Context。`CardContext.Builder.build()` 复制三表为不可变快照，put 同时复制 List 和 Component；其他对象由调用者保证稳定。`get(id, Class<T>)` 只在类型匹配时返回，否则为 null。

`CardBuilder.put/enable/list` 代理 Context 的同名能力。put 自动设置同名状态：布尔为 1/0，数值为自身，字符串按空白判定，集合取大小；enable 可覆盖。list 将数量写入根键，并通过 prefix 回调填充带下标的平铺参数。`reward/price` 保存延迟解析的 CardText，不在公共代码里创建物品堆。callback 仅由客户端绑定，DTO 和 JSON 不提供 Runnable。

## 主文案、播报与更新

summary 提供模板缺失时的回退和通知主句，模板正文已有相同文字时避免重复。`SpeechContent` 分 main、requirements、rewards、descriptions，客户端按个人开关组合。未设置 speech 时沿用 summary，显式 `SpeechContent.EMPTY` 表示不播报；TTS 不从模板、按钮或 Java 类型推断业务。

`CardUpdate(ref, mode, data)` 与内容分离，ref 为 UUID + revision：

| mode | 行为 |
| --- | --- |
| SHOW | 新增消息；按响应来源进入聊天或通知队列 |
| REPLACE | 完整替换指定 ID；不新增历史、不重新入队或播报 |
| REMOVE | 结束该 ID 的活动展示和语音；历史保留最后只读快照 |

较旧或重复版本被忽略；关闭的通知收到 REPLACE 不再弹出。请求完成不依赖卡片数量，零卡片回复仍解除等待。状态和历史分别走 StateResponse、HistoryResponse；历史恢复不播报、不发包、不恢复旧按钮的执行权限。

## 布局与占位符

参数、状态及回调 ID 统一用小写下划线，文字占位符只支持 `{{id}}`。集合和 callback 字段可写 `{{id}}` 或直接写 ID；不兼容之前未发布的美元花括号格式或大写参数名。

```json
{
  "style": ["result_card"],
  "show_title": false,
  "body": [
    { "type": "system:title", "level": 2, "text": "{{title}}" },
    { "type": "system:flex", "source": "{{result_items}}", "children": [
      { "type": "system:item", "item": "{{result_items.*}}" }
    ] },
    { "type": "system:button", "text": "领取奖励", "click": "return-val", "value": "{{claim_value}}", "when": "can_claim" }
  ]
}
```

JSON 控制顺序、嵌套、样式和可见性。通常用 flex / grid 组合 button；单文本模板携带按钮时宿主补充动作区。

| 根字段 | 默认值 | 含义 |
| --- | --- | --- |
| `style` | `result_card` | CSS 类名；接受字符串或字符串数组，数组用空格拼接 |
| `show_title` | true | `show_card_title` 状态开启时自动展示通用标题 |
| `hide_summary` | false | 不在正文上方显示摘要行；标题已由模板给出、或摘要与正文重复时使用，聊天消息与语音仍保留摘要 |
| `body` | 必填 | 节点列表 |
| `actions` | 默认编号和标签 | `numbered` 控制简约模式编号；`label` 控制语音 / 数字选择的动作标签。样式和按钮结构写在正文节点上 |

所有节点都有完整命名空间的 `type`（例如 `system:text`），可选 `id/style/when/unless/repeat`。下表省略内置节点的 `system:` 前缀。`style` 同样支持字符串/数组；`when` 的状态非 0 时显示，`unless` 的状态非 0 时隐藏，未知状态为 0。节点条件可直接写状态 ID 或 `{{id}}`。条件只读状态表，不猜测参数值类型。

| 分类 | type | 功能 |
| --- | --- | --- |
| 文本 | `text` / `markdown` / `title` | FTB 格式文字、受控 Markdown、可提升的标题 |
| 媒体 | `image` / `item` / `player` / `ftb_icon` | 单张本地图片、单个物品、玩家模型、单个 FTB 图标 |
| 布局 | `flex` / `grid` | 弹性布局、只创建当前页的固定网格 |
| 容器 | `container` / `groups` / `details` | 普通流、分组选择、折叠 |
| 交互 | `button` / `link` | URL、本地回调或服务端字符串；文字链接 |
| 展示 | `divider` / `progress` | 分割线、进度条 |
| 集成 | `player_information` / `recipe` | Saturn 信息行、单个有序配方 |

共 18 种节点；原来的批量物品、图标、货架、任务网格、pin、actions 均由这些节点组合，不再保留重复实现或旧节点别名。次要文字使用 `text` 配合 `style: "card_context"`。

`text/title` 通过独立的 TextContent 共享 `text/translate/args/inline`：优先使用 translate，先替换 args 中占位符再翻译，inline 默认 false。容器的 children 必填，text_separator 可选，仅影响纯文本连接。

`title` 另有 `level`（1～4，默认 1），对应 h1～h4 的字号层级。正文**顶层**第一个可见、非空且没有 repeat 的 1 级标题会被提升为卡片标题：渲染器把它排在摘要之前，正文里不再重复绘制，即使标题键与结果消息键相同也会显示；放在容器或 repeat 里的 1 级标题、以及 2～4 级标题都在原位渲染。标题节点不参与纯文本折叠判定，带标题的模板始终保留卡片外观。

标题与正文使用同一份已格式化的 Context：先解析 Component、保留延迟 CardText，并补充 summary、card_title、显示模式和按钮参数，再选择标题。因此 `{{label}}`、`{{title}}`、`{{reward_label}}` 中的组件颜色、翻译和模式条件都能生效，不会因原始对象类型而留下占位符。隐藏、空白及 repeat 标题不占用卡片标题位置。

被提升的标题同时抑制卡片正文里的自动摘要行，避免“标题 + 同一句话”重复；摘要仍保留在卡片数据里，聊天消息与语音播报照常使用。需要保留的业务提示应在模板中明确加入 `{"type":"system:text","text":"{{summary}}"}`，可附加 when 条件；内置兑换确认、奖励回执、任务事件／提交、候选操作及新手礼包都采用这一方式。物品来源保留摘要中的物品名与数量，避免只剩注册 ID。

### 内置模板的标题约定

27 份内置模板中，26 份结构化卡片均使用 `system:title`；纯文本 `text.json` 保持无标题。24 份使用顶层一级标题，奖池和奖品两份保留现有标题与数量／概率同行的二级标题布局。

| 卡片 | 标题来源 |
| --- | --- |
| shop_catalog / lottery_catalog / quest_list | 系统商城、系统抽奖、任务列表；不显示冗长结果提示作为标题 |
| shop_offer / shop_order | 商品或订单名称；兑换确认保留独立提示、价格与按钮 |
| entitlement / purchase / reward | 奖励名称；回执、原因、物品和领取状态放正文 |
| quest / quest_submit | 带原有样式的任务名称；详情继续保留完成标记，提交结果另行显示 |
| quest_notice | `notice_title` 按事件翻译为“任务完成”或“新任务解锁”；原通知主句及待领奖提示保留在正文 |
| player / items / item_sources / sign_in_status | 宿主信息、物品查询、物品来源、每日签到；宿主双栏内部不再重复标题 |
| knowledge_answer / web_search / quest_guide | 知识查询、联网搜索、本地说明书；来源条目仍使用二级标题 |
| claim / delivery_state / selection / starter_gift / quest_startup | 任务领奖、交付状态、可选操作、新手礼包、可推进任务；正文保留具体状态／提示／数量 |
| lottery_pool / lottery_prize | 原奖池／奖品名称；保留同行数量／概率，不再额外补通用标题 |

这些卡片标题属于内容区，HUD 左上角的固定“系统”标题不受影响；TTS 继续取 SpeechContent，与标题排版无关。资源包覆盖后需同步更新对应模板，离线编辑器的内置快照也应一起刷新。

### Markdown 正文

```json
{
  "type": "system:markdown",
  "text": "{{answer}}",
  "when": "answer"
}
```

`text` 默认为 `{{summary}}`，支持平铺占位符、循环下标和通用 style / when / unless / repeat。节点从原始字符串读取内容，不注入 Minecraft 格式重置符，也不把 `&a` 等字符转换为颜色代码。`system:text` 的行为不变，仍负责 FTBQ 标题、介绍、来源原文与 § / & 格式文本。单个 Markdown 节点保留自己的布局，不触发只适用于单个 `system:text` 的普通消息折叠规则。

底层复用 AUI 1.2.9 的 Txtmark 扩展解析器，不增加第三方依赖；支持标题、段落、粗体 / 斜体、嵌套有序 / 无序列表、引用、行内代码、围栏代码块、分割线与链接。代码保留换行并允许长行换行；没有代码语法高亮，也不提供 GitHub 风格表格或任务复选框扩展。样式集中于共享 assistant.css 的 card_markdown / markdown_*，完整模式、简约模式和通知使用同一个渲染入口。

AUI 的 `renderSafe` 不是完整 HTML 过滤器，因此 `AssistantMarkdown` 使用 JDK 的被动 HTML 读取器，只重建预设 div / span / br 等展示元素及纯文本节点。模型给出的属性、脚本、样式、自定义 AUI 标签和事件不会进入文档，图片只显示 alt 文字、不下载外部资源。链接只保留合法的 HTTP / HTTPS 地址，点击经过 `AssistantPageScreen.openLink` 和原版确认流程；HUD 仍需要进入交互状态。解析失败记录日志并退回源文本。

本地知识问答和联网回答已改用此节点，两个 CardData 把原始 `answer` 独立放入 Context，避免 summary 的 Minecraft 文字格式化改变代码 / URL。没有模型答案时的内置提示与折叠来源仍用 text 与 card_context 样式。模板设置 hide_summary，避免重复绘制未经排版的答案；问答 / 搜索提示词允许 text 字段使用简短 Markdown，仍要求输出 JSON。没有改变网络 Codec、历史数据或 SpeechContent，TTS 继续使用业务显式提供的播报文本。Markdown 的纯文本投影按块换行、移除排版标记，不把 HTML 带入文字导出。

### 宿主信息与玩家模型

内置 `player.json` 用 flex 的 row / column 方向 排出左侧模型、右侧信息：右侧由独立的 `system:player_information` 节点显示 Saturn 玩家信息，不再单独重复名称 / 生命 / 等级 / 维度，也不显示在线玩家列表。模型区域默认**无边框**：节点 `style` 写成 `["player_preview", "player_preview_bare"]`，把 `player_preview_bare` 从 `style` 去掉即可恢复边框。布局、预览宽高仍在共享 `assistant.css`，窄容器允许换行，不改变 HUD 的宿主宽度或限高。

```json
{
  "type": "system:player",
  "player": "{{player_id}}",
  "follow_mouse": true,
  "yaw": 15,
  "pitch": 0,
  "scale": 1,
  "offset_x": 0,
  "offset_y": 0
}
```

| 参数 | 节点默认值 | 含义 |
| --- | --- | --- |
| `player` | `{{player_id}}` | 参数表中 UUID 对象的绑定键，支持现有循环下标；绑定缺失或类型不符时不生成节点 |
| `follow_mouse` | false | 光标可见时跟随鼠标，水平 / 垂直偏转沿用原版背包预览的计算方式；内置 player.json 显式开启 |
| `yaw` | 15 | 关闭跟随或光标锁定时的固定水平朝向，单位为度；0 为正面，180 为背面，90 / -90 为两侧 |
| `pitch` | 0 | 关闭跟随或光标锁定时的固定俯仰角，单位为度 |
| `scale` | 1 | 自动适配宽高后的缩放倍率，必须大于 0；0.8 缩小，1.2 放大，不改变区域尺寸 |
| `offset_x` | 0 | 模型中心水平偏移，单位为文档 CSS 像素；正数向右 |
| `offset_y` | 0 | 模型中心垂直偏移，单位为文档 CSS 像素；正数向下 |

以上配置字段使用 lenientOptionalFieldOf，缺失或类型错误时采用默认值，非正数或非有限的 scale 同样回退为 1。节点没有额外文字投影，不影响纯文本导出或 TTS。区域尺寸仍由 CSS 的 player_preview 样式控制，缩放和偏移只调整框内模型，超出区域的部分继续裁剪。节点自身的类名可被 `Node.style` 覆盖：内置模板用 `["player_preview", "player_preview_bare"]` 保留尺寸与背景、去掉预览边框；要在自定义模板里恢复边框，只写 `player_preview` 即可。

跟随在客户端绘制时读取实时光标，先通过 AUI 的文档视口与 CSS transform 逆变换转换坐标，再与偏移后的模型中心比较，适配 GUI 缩放、滚动和容器变换。游戏锁定鼠标时使用 yaw / pitch，不强制释放光标，不拦截按键或点击。固定朝向模式保持头部与身体的水平朝向一致，跟随模式则保留原版预览的头部偏转。

`PlayerQueryTool` 在服务端读取当前宿主 UUID 和数值，`PlayerSnapshotOutput` 的 Codec 携带 playerId，buildCard 写入 player_id；公共数据不持有 Entity、皮肤或客户端类。客户端 `PlayerModelElement` 每次按 UUID 解析当前世界玩家，复用原版 InventoryScreen / GuiEntityRenderer 的独立渲染状态与实体提交链路，沿用 MC 皮肤、装备和模型层。数值属于服务端查询时的快照，模型使用当前客户端状态；不按名字找人、不把离线卡片替换成本机玩家，也不自行请求皮肤接口。

玩家信息节点只接受一个业务配置字段：

```json
{
  "type": "system:player_information",
  "importance": 40
}
```

`importance` 默认 0，缺失或类型错误时回退为默认值。仅显示 Saturn 行定义中 `importance` **严格大于**此值的行，等于阈值也不显示；内置模板设为 40。通用的 `style/when/unless/repeat` 仍由节点宿主处理，没有字段 / 分组白名单或额外行数、字符数裁剪。数据包新增的 Saturn 信息行自动参与显示；调整行的重要度、可见条件或格式应修改 Saturn 数据定义。

`PlayerInformationView` 在服务端游戏线程通过 `PlayerViews.parse(player)` + `PlayerInformation.collectAll(...)` 采集信息，enabled、visible、missing、顺序和内容格式均由 Saturn 决定。`PlayerInformationOutput` 仅保存每行的重要度与 `InformationText.lines(row)` 生成的 Component 列表，避免把原生 InformationDocument 的注册表 Holder 引用带入无注册表上下文的 JSON 聊天历史；不重新实现 Saturn 的求值或文本格式化。Component 的翻译键和样式保留到客户端处理。`PlayerSnapshotOutput.buildCard()` 把列表放进固定参数 `player_information`，节点统一处理卡片渲染与纯文本投影，过滤后为空时显示 `system.ui.player_information_empty`。采集整体失败只记录日志并返回空列表，不影响玩家查询本身。

模型在 AUI 当前输出目标、变换与裁剪中绘制，深度使用其物品保留区间，避免 CSS 尺寸改变后覆盖其他文档；不调用会切换投影 / 输出目标的整套 GuiRenderer。只调整临时 render state，移除姓名、阴影、火焰和轮廓，不修改真实实体的朝向或姿态。节点不保留实体引用或单独 GPU 资源，玩家暂不在客户端世界中时显示本地化占位，重新可用后恢复。

### 任务与奖励类型图标

`ftb_icon.icon` 绑定单个 `FtbIconData`，不接受图标列表。`FtbIconData.quest(id, title)` 读取 Quest 自身配置的图标；`FtbIconData.type(preview)` 使用服务端提供的 QuestIconOutput 类型、数量和 tooltip。任务列表的单个图标参数为 `actions.<下标>.action_icon`，任务 / 奖励列表用 flex.source + 单 ftb_icon 组合。

原生图标适配器仅在安装 FTB 的物理客户端反射加载；共享 DTO 不引用可选类。类型不符时隐藏，适配器不可用时使用标题回退。物品仍走 item 节点和原版 tooltip，图标与数量继续沿用 FTB 的原生展示。

任务详情与提交结果的要求区使用 `flex.source=tasks` 横向排列目标，每个 `quest_requirement` 把物品 / 类型图标与对应进度放在一起，空间不足时按目标换行；不逐项目标占一整行，奖励区仍独立展示。

```json
{"type":"system:flex","source":"{{task_icons}}","children":[
  {"type":"system:ftb_icon","icon":"{{task_icons.*}}"}
]}
```

### 折叠区与超链接

```json
{
  "type": "system:details",
  "summary": "{{tr:system.ui.search_sources}} ({{sources}})",
  "when": "sources",
  "children": [
    {
      "type": "system:flex",
      "direction": "column",
      "repeat": "sources",
      "children": [
        { "type": "system:link", "text": "[{{sources.*.id}}] {{sources.*.title}}", "url": "{{sources.*.url}}" },
        { "type": "system:text", "style": "card_context", "text": "{{sources.*.snippet}}", "when": "sources.*.snippet" }
      ]
    }
  ]
}
```

`details` 的 summary、children 必填，`open` 默认 false。children 使用完整节点语法，可嵌套其他折叠区、循环、物品和本地 / 服务端按钮；没有可见子节点时整个折叠区省略。展开状态仅属于当前渲染实例，重建或重开页面后按 open 初始化，不写业务数据、不请求模型或搜索。完整、简约模式可点击标题，HUD 需先按聊天键；纯文本导出保留标题和全部内容，TTS 仍只取独立的 SpeechContent。

`link` 的 url 必填，text 缺省时使用 URL。两者支持平铺占位符与循环下标，URL 以原值替换，不注入 Minecraft 格式重置符。点击复用 `Screen.clickUrlAction`，由原版校验外部 URL、读取 chatLinks/chatLinksPrompt，必要时显示原版确认界面，结束后返回当时的界面；非法链接不执行。链接不作为 AUI 导航或脚本执行，不触发服务端动作。完整、简约模式均可点击，HUD 需进入聊天栏；纯文本格式为标题与 URL。

折叠标题与链接采用 flex 按钮和独立的 block 文字子元素，避免按钮直接文字与行内片段重复绘制；标题的箭头单独占位。展开 / 收起只使内容子树的布局失效，交给 AUI 的下一次渲染处理，不在点击事件中同步提交整页布局；回调持有提前初始化的原生节点，避免插入时替换标签后引用失效。搜索模板用 search_source 块纵向排列来源，摘要通过 line-clamp 显示最多三行，完整内容可点击链接查看；这些显示限制不裁剪模型取得的证据。

| 占位符 | 含义 |
| --- | --- |
| `{{title}}` | 字符串、数值、布尔或 `Component` 参数；组件在客户端按当前语言格式化为带格式码的字符串 |
| `{{tr:system.ui.lottery_cost}}` | 翻译完整语言键 |
| `{{field:reward}}` | 翻译 `system.field.reward` |
| `{{bool:signed_in}}` | 根据状态翻译“是/否” |
| `{{state:state}}` | 字符串参数转小写后翻译 `system.state.<值>` |

未知或不能作为文字的参数保留原占位符。替换只执行一遍，字段值中的花括号、美元符和反斜杠不会再求值；动态文字不作为 HTML/脚本执行，原有格式码仍独立收束。

## 平铺的循环参数

Builder 的 list 只是写键的便捷方法，不保存子 Context：

```java
builder.list("prizes", pool.prizes(), (prefix, prize) -> builder
        .put(prefix + "label", prize.reward().label())
        .put(prefix + "result_items", previews(prize)));
```

它将数量写入 prizes，将条目参数写入 `prizes.0.label`、`prizes.0.result_items` 等平铺键。repeat 从状态表读取次数，渲染器只携带循环下标：

```json
{
  "type": "system:flex", "source": "{{prizes}}", "direction": "column",
  "children": [{
    "type": "system:flex", "source": "{{prizes.*.result_items}}",
    "children": [{"type":"system:item","item":"{{prizes.*.result_items.*}}",
      "tooltip":[{"type":"system:text","text":"{{prizes.*.label}}"}]}]
  }]
}
```

每个 `*` 按从外到内的顺序替换下标。例如嵌套循环的 `rewards.*.choices.*.choice_title` 读取 `rewards.0.choices.1.choice_title`。repeat 本身先使用外层下标取次数，再进入当前循环。没有继承、局部变量或参数覆盖，点号只是 Map 键的一部分。

## 物品

`item` 只接受一个 ItemPreviewOutput；`size` 默认 32 像素，`tooltip` 是文字投影节点列表，追加到原版物品 tooltip。物品堆只在客户端按需创建；物品名称、价格及奖池 tooltip 通过 CardText 延迟到实际显示节点或文本导出读取时解析，未翻到的页不提前创建 tooltip 物品堆；列表、说明、名称和标签由 text / flex / grid 组合。put 一个物品 List 会同时写入 `key.0` 等平铺键，物品的 `key.0.label` 是延迟解析的名称和数量。

`image` 的 source 是本地 Identifier，例如 `system:icons/pin.svg` 对应 `assets/apricityui/apricity/system/icons/pin.svg`，由 AUI 异步读取；width / height 默认 32。该节点不下载网页图片，文字导出使用 alt。player 节点继续复用原版模型渲染。

`recipe` 绑定单个 ItemSource，只负责工作站、材料、结果和说明；保留有序槽位、重复材料、空格、3×3 补齐和候选材料 tooltip。物品来源卡使用 groups 的固定分类选项，以及带各自 filter 的 grid 组成配方 / 掉落 / 标签视图。

## 按钮与动作

按钮约定参考 [KOOK 卡片消息的 click/value](https://developer.kookapp.cn/doc/cardmessage)，采用同样的链接 / 字符串回传概念；布局、DTO 和 MC 交互仍由本模组实现。

```json
{"type":"system:button","text":"打开文档","click":"link","value":"https://example.com"}
```

```json
{"type":"system:button","text":"查看任务","click":"return-val",
 "value":"system:quest_detail?quest=0123456789abcdef","behavior":"push"}
```

```json
{"type":"system:button","text":"本地操作","click":"local","value":"{{example_callback}}"}
```

| click | value | 执行位置 |
| --- | --- | --- |
| `link` | HTTP / HTTPS URL | 客户端复用 Minecraft 链接偏好与确认界面 |
| `return-val` | 不透明字符串 | CardActionRequest 只发送 source(ref + revision) 和 value，服务端解析 |
| `local` | CardContext.callbacks 的键名 | 执行代码提供的 Runnable；JSON 不提供脚本 |

按钮可用 text 或 children 构建主体；icon 可引用本地图片；label 提供 tooltip / 无障碍文字。selected / disabled 读取状态绑定。客户端的可选 binding 只接受代码提供的 CardButtonBinding，供 pin 等交互维护实时选中、pending 和可用状态，通用 ButtonNode 不认识任务或缓存。

内置字符串用 `CardEventValue.encode(Identifier, Map<String,String>)` 生成，例如 `system:quest_detail?quest=...`；键和值采用 UTF-8 URL 编码，参数按键排序。没有参数时只有动作 ID。`CardBuilder.serverButton` 的 Identifier + Map 重载只负责生成该字符串；也可直接传入字符串。客户端不解析服务端行为或请求大模型。

服务端先检查玩家连接、绑定、限流及来源版本，再在游戏主线程发布 `CardButtonEvent`。其他模组读取 player/source/value，识别自己的格式后调用 `handle(AgentResult)` 或 `handle(CompletionStage<AgentResult>)`；已处理事件不再进入内置解析器。单个事件只能有一个处理结果。事件监听器负责自己的权限和业务校验，不在主线程等待网络或磁盘。

```java
@SubscribeEvent
public static void button(CardButtonEvent event) {
    if (event.result() != null || !event.value().equals("example:help")) return;
    event.handle(AgentResult.ok("example.help"));
}
```

未处理字符串进入 CardEventValue → ModuleRouting.button → AgentEngine.submit，继续复用启用模块、所有权、费用、任务状态及幂等检查。客户端可修改字符串，服务端始终重新检查业务数据；卡片来源只是关联与版本，不授予执行权限。

`CardButton` 保留 BODY / FOOTER 和 CLOSE / WAIT / REPLACE / PUSH。通知的确定、返回和底部按钮由宿主代码管理；正文 button 不替换这些控件。REPLACE 回复替换当前通知，PUSH 同时保留来源和 CardUiState 以便返回；翻页、分组和折叠只更新当前窗口，不发包或插入通知。纯文本卡有按钮时仍保留卡片。简约模式保留带编号的文字选择。

### 共用分页网格

`flex` 支持 direction（row / column）、wrap、可选 source/filter/page_size；`container` 只包裹静态 children。`grid` 的 source 为空时按静态 children 排版，有 source 时按原始索引重复 children；不要同时用 repeat 和 source 遍历同一集合。

```json
{
  "type":"system:grid", "source":"{{offers}}", "columns":6,
  "compact_columns":3, "max_row":3, "cell":104,
  "children":[{
    "type":"system:button", "style":"offer_cell_content",
    "click":"{{actions.*.click_type}}", "value":"{{actions.*.value}}",
    "children":[{"type":"system:text","text":"{{offers.*.label}}"}]
  }]
}
```

动态 grid 每页容量为当前列数 × max_row；full 取 columns，其他模式取 columns 与 compact_columns 的较小值。筛选后结果只有一页时，按实际需要的行数收缩高度；两页及以上始终保留 max_row 行，包括不足一页的末页，避免翻页跳动。上 / 下页按钮始终保留，单页时置灰；空结果不生成网格。只创建当前页 DOM 和物品预览，CardPagination 是唯一分页组件。

`groups` 只负责筛选索引，不认识货架或任务。source/filter/group/group_label 控制分组；section/section_label 控制左侧分节；orientation 可为 vertical 或 horizontal。默认只显示结果实际存在的分组；显式 options:[{id,label}] 可固定分类，空分类置灰。选中组只把原始下标集合传给相同 source 的后代布局，整张卡仍共用一份 Context，事件参数和循环下标不重新编号。

CardUiState 在每个展示实例保存页码、选中组和折叠状态，按卡片及节点路径定位；可设置节点 id 保持局部状态稳定。通知返回会保留来源状态，聊天与通知各自独立。状态不混进 CardContext 的业务参数，不上传服务端。分页和分组点击只替换局部子树并请求 AUI 在下一帧重新布局，不同步读取 scrollLimit 或强制计算整页样式。

`progress` 读取 0～100 的数值，轨道默认高 6px，颜色仍由主题控制。首次绘制从零展开，更新时从当前显示值平滑过渡到新值（350ms），相同目标不重播。CardUiState 按数值绑定保存动画状态，常驻卡片替换正文时复用此状态；原生填充元素只缩放绘制矩阵，动画期间不修改 CSS 宽度或查询布局，结束后停止主动重绘。

任务 pin 使用 QuestCardBindings 在 CardBuildEvent 中注入本地 callback 和 CardButtonBinding，普通 button 显示图钉图片；服务端固定记录仍由原来的专用显示卡载荷与 DataStorage 管理。任务完成通知、单任务详情和多任务列表的排队策略保持原有行为。

## 常驻卡片 HUD

常驻卡片使用独立的 `AssistantCardDisplay`、`assistant-cards.html` 和 Saturn HUD 项 `system.cards`，默认位于屏幕左侧；不进入聊天历史或通知队列，不播放提示音或 TTS，也没有自动关闭时间。正文仍由 `CardData.buildCard()`、`CardBuildEvent`、`CardPresentation` 和客户端 JSON 模板构建，共用 `assistant.css` 和主题。`CardContext.Mode.HUD` 对应 `hud` 状态；支持折叠、链接、图钉等客户端节点，当前任务跟踪模板不提供提交、领奖等业务动作。

固定记录由核心模块的 `DisplayCardsDataStorage` 保存，存储声明为 `CoreSystemModule.DISPLAY_CARDS`，Saturn Codec 类型为 `system:display_cards`。去重且保持插入顺序的记录只含 `source` 和 `id`，不保存过期任务快照或客户端对象。更新通过 `AgentStore` 玩家事务执行，实际变化调用 `markDirty()`；沿用 `<存档>/system-agent/<玩家UUID>.json` 的 moduleData，因此按服务器 / 存档及玩家隔离，重新登录、重启仍保留。撤销 / reset 删除玩家业务数据时一起删除；普通登出只清空客户端镜像。

服务端 `AssistantCardDisplays` 负责数据源与会话，由 `ModuleManager` 驱动登录、初始化、tick 和关闭。模块通过 `context.get(ContextTypes.DISPLAYS).register(context.id(), sourceId, reader)` 注册来源，reader 在服务端线程接收当前玩家及业务 ID，返回 `Optional<CardData>`。每秒重读当前数据，只在快照变化或确认固定操作时发送 `DisplayCardsS2CPayload`；C2S 只接受 key 和固定状态，不接受客户端提供的卡片正文。session / revision 防止过期快照覆盖，异步写回还会复核连接与玩家运行代次。

FTB 来源为隔离适配器 `FtbQuestDisplay`：每次核对当前队伍、任务可见性及详情解锁条件，再生成 `QuestPinOutput`。`assets/system/cards/quest_pin.json` 显示标题、章节、前三项目标与底部进度；`status` 为彩色勾 / 叉，`title` 只包含任务标题，二者使用不换行的 flex 横排，状态不收缩，长标题仅在右侧文字列换行；额外目标只显示数量。完成后继续保留到手动取消 pin，不自动提交或领奖。任务不存在、变为隐藏或对应模块停用时，保留引用但不发送卡片正文；恢复可读后重新出现。

左侧宽度由共享 CSS 控制为 `min(360px, 38vw)`，高度随内容增长，没有高度上限或内部滚动区，全部卡片按顺序向下排列。Saturn 定位使用内容的实际高度；超出屏幕的内容由视口裁剪。按聊天键后可查看 tooltip 及取消固定，正常游戏不抢占鼠标；默认打开背包等其他 Screen 时立即禁用交互，退出动画完成后卸载 HUD，返回后恢复。“设置 → 常规”可关闭常驻卡片动画，或启用在其他游戏内界面继续展示：仅放行 `Screen.isInGameUi()` 为 `true` 的背包、箱子等页面，设置和暂停菜单仍隐藏，不依据暂停状态判断；正常游戏与聊天栏另行放行。其他界面内保持被动，不拦截原版控件。F1 和退出世界仍立即卸载，HUD 编辑器始终使用预览。位置和显隐使用 Saturn 自带编辑器，详见[HUD 位置编辑](ui-localization.md#常驻卡片-hud)。常驻展示不注册通知 CardRef，不执行通知的确定、返回或排队逻辑。

每张卡片使用独立包装层执行横向滑动和淡入淡出：Saturn 左侧锚点左进左出，右侧锚点右进右出；上下居中锚点按 HUD 所在屏幕半边选择方向。位移根据已保存的位置与屏幕边缘计算，并转换为文档像素。新增卡片等待原生首次布局完成再入场，取消固定、切换界面或数据暂时不可读时先退出、动画结束再移除；最后一张也保留到退出完成。退出途中返回游戏或恢复同一张卡片会反向进入，任务进度及模板更新只替换内部内容，不重播入场。切换界面只保留退出所需的被动绘制，不接受鼠标操作；编辑器自身预览不受影响。

## 通用参数与模块字段

通用参数为 summary/card_title/result_actions、`actions.<下标>.label/number/click_type/value/behavior/visible/disabled`；状态为 show_card_title/full/simple/notification/text_items。回调为 `actions.<下标>.click`。纯文本模式的 full/simple/notification 均为 0。

| 模板 | 参数 |
| --- | --- |
| player | player_id（UUID）、name/health/max_health/experience_level/dimension，player_information |
| items | items.<下标>.item_name/item_id/max_stack，result_items |
| sign_in_status | date/signed_in/history_saved/external_model，candidates.<下标>.reward_label/reward_items/result_items |
| reward/entitlement/purchase | reward_label/reward_items/result_items；回执另有 entitlement_id/state，购买另有 order_id；状态 delivered 表示已交付，按钮区显示不可点的“已领取” |
| shop_offer | label/id/items/price/price_text/payment_available，offer_items/price_items |
| shop_order | order_id/label/state/error |
| lottery_catalog | revision/query/truncated；pools.<下标>.label/prizeCount/prizes.<下标>.label/chance/items，单元格由 pool.<下标> 服务端按钮驱动 |
| lottery_pool | label/id/prize_count/preview_count/price/price_text/free/payment_available/price_items/prizes；状态 priced 表示有单次消耗；prizes.<下标>.reward_label/chance/result_items |
| lottery_prize | reward_label/reward_items/chance/result_items，概率数值不含百分号 |
| delivery_state | state |
| quest/quest_submit | title，tasks.<下标>.task_title/task_progress/task_required/task_items；详情另有 chapter/description/progress/completed；submission 枚举由 CardData 构建提交或勾选按钮 |
| quest | rewards.<下标>.reward_title/reward_state/result_items，choices 使用 rewards.<下标>.choices.<下标>.choice_number/choice_title/result_items |
| quest_notice | id/title/chapter/description/requirements/other_rewards/manual_submission/result_items |
| quest_startup | total/remaining，quests.<下标>.title/progress |
| starter_gift | gifts.<下标>.result_items/error |

任务详情的 `title` 在任务名称前追加绿色 ✓（已完成）或红色 ✗（未完成），只给符号着色，保留原任务名称的样式。summary 与标题使用同一份组件，避免自动摘要重复显示任务名；模板不再单独显示“已完成”一行，语音仍读取原有 SpeechContent。

price 是非物品费用文字，price_text 包含物品文字。text 使用 summary，selection 使用已声明的动作。状态和历史没有卡片模板。

交付（`system.action.deliver`）成功后不再发送“已交付”状态卡：服务端用 silent 回复重建同一张回执并原地替换来源卡，`delivered` 状态把“领取到背包”换成不可点的“已领取”。`reward` / `entitlement` / `purchase` 三张卡都适用，重建所需的 kind、订单号与奖池随按钮参数下发。

## 类型扩展与重载

节点实现位于 `screen/card/node`，各自独立文件。根接口 `CardNode` 定义：

- `codec()`：返回当前节点注册的稳定 MapCodec 实例。
- `css()`：默认样式，仍可由 JSON 的 style 覆盖。
- `render(view)`：生成 AUI 节点，返回 null 隐藏。


`CardTemplate.Node` 只包装共用的 id/style/when/unless/repeat。节点实现分别位于 node.text、media、layout、container、interaction、display、integration，不内联。能力接口按需要实现：TextProjectingNode 负责文字导出，PlainMessageNode 表示普通消息资格，HeadingNode 返回可提升标题，ChildrenNode 统一 children，CollectionNode 统一集合原始下标。TitleNode 与 TextNode 只共享 TextContent，不互相继承。

CardRenderer 只负责条件、循环、路径、占位符和按接口分派；不再对具体 TextNode / TitleNode 或业务节点做判断。文本模式由可投影节点导出所有页，卡片级的无按钮、独立标题和摘要规则仍集中在 CardPresentation。

新增节点使用独立 Java 文件，例如：

```java
public record BadgeNode(String value) implements TextProjectingNode {
    public static final MapCodec<BadgeNode> CODEC = Codec.STRING.fieldOf("text")
            .xmap(BadgeNode::new, BadgeNode::value);

    @Override
    public MapCodec<BadgeNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_context";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        return view.element("span", CardRenderer.value(this.value, view.context(), view.indices()));
    }

    @Override
    public String text(CardRenderer.Text view) {
        return CardRenderer.value(this.value, view.context(), view.indices());
    }
}
```

随后在客户端入口通过 DeferredRegister 登记 Codec，并将其绑定到 mod event bus：

```java
public static final DeferredRegister<MapCodec<? extends CardNode>> NODES =
        DeferredRegister.create(SystemResourceKeys.CARD_NODE_TYPE, "example");
public static final DeferredHolder<MapCodec<? extends CardNode>, MapCodec<BadgeNode>> BADGE =
        NODES.register("badge", () -> BadgeNode.CODEC);

// In a client-only mod constructor:
NODES.register(bus);
```

JSON 使用 `"type":"example:badge"`。注册与冻结由 NeoForge 模组加载流程负责，不能在 `ClientModule.register(context)` 或 F3+T 重载时新增节点。重复 ID、重复使用同一个 Codec 实例、未知类型或未注册的编码节点都会拒绝。节点的 MapCodec 不应包含共用字段；递归 children/description 使用 `Codec.lazyInitialized(() -> CardTemplate.Node.CODEC)`，避免静态初始化引用环。扩展不应绕过通知的代码按钮约定。

资源重载依次尝试高优先级资源包、低优先级资源包、上次有效模板；未知 type 或结构错误使整张模板回退，结构字段不作 lenient 吞错。删除资源会移除模板。完整/简约界面刷新已有内容；通知在实际绘制时读取当前模板，队列保存卡片数据；已显示通知不因资源重载重新入队或播报。消息按卡片 ID 去重。

资源只位于客户端 assets，不进入服务端 data、存档或模型请求；无新增依赖，网络协议仍为 1。
