# 客户端 JSON 卡片

数据流为：服务端生成业务快照 → `CardUpdate` 携带 `CardData` → 客户端调用 `buildCard().build()` → `CardView` → JSON 布局 → 明确的本地或服务端按钮动作。完整模式、简约模式和 HUD 共用构建、版本和交互入口。架构说明见[卡片与消息数据流](card-flow-design.md)，注册入口见[注册表速查](registries.md)。

## 数据与渲染绑定

公共契约位于 `com.iafenvoy.system.card`：`CardData` 提供 `codec()` 和 `buildCard()`，不加载客户端、AUI、服务端运行时或可选模组类。业务 DTO 仍在原来的输出包；具体客户端处理位于 `card.client` 和 `screen.card`。

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

`ClientCards` 保存连接内按 ID 的最新数据、版本和活动状态。`CardPresentation.of(update, messageKey, mode, actionOffset)` 取最新快照，解析 Component 和 CardText，补充显示模式、按钮标签及受控 callback，然后读取 `assets/<namespace>/cards/<path>.json`。缺少模板时回退到 summary 和已有按钮，不展示 DTO 的原始 JSON。

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
    { "type": "system:header", "text": "{{title}}" },
    { "type": "system:items", "items": "{{result_items}}", "label": "{{field:reward}}" },
    { "type": "system:button", "text": "领取奖励", "callback": "claim", "when": "can_claim" }
  ]
}
```

JSON 控制顺序、嵌套、样式和可见性。通常用 actions 节点安排按钮；单文本模板携带按钮时宿主补充动作区。

| 根字段 | 默认值 | 含义 |
| --- | --- | --- |
| `style` | `result_card` | CSS 类名；接受字符串或字符串数组，数组用空格拼接 |
| `show_title` | true | `show_card_title` 状态开启时自动展示通用标题 |
| `body` | 必填 | 节点列表 |
| `actions` | 默认样式 | 可信动作列表的容器、按钮、编号及标签配置 |

所有节点都有完整命名空间的 `type`（例如 `system:text`），可选 `style/when/unless/repeat`。下表省略内置节点的 `system:` 前缀。`style` 同样支持字符串/数组；`when` 的状态非 0 时显示，`unless` 的状态非 0 时隐藏，未知状态为 0。节点条件可直接写状态 ID 或 `{{id}}`。条件只读状态表，不猜测参数值类型。

| type | 默认 CSS | 功能 |
| --- | --- | --- |
| `text` | `card_body` | 正文 |
| `header` | `card_title` | 标题 |
| `context` | `card_context` | 次要文字 |
| `container` | `card_container` | 普通流布局 |
| `row` | `card_row` | 横向排列并允许换行 |
| `column` | `card_column` | 纵向排列 |
| `details` | `card_details` | 默认折叠的通用内容区，点击标题展开 / 收起 |
| `link` | `card_link` | 调用 Minecraft 原版打开 URL 的流程 |
| `divider` | `card_divider` | 分割线 |
| `items` | `item_groups` | 原版物品图标、数量及 tooltip |
| `actions` | `card_actions` | 已有可信动作列表 |
| `button` | `card_action` | 通过 callback ID 绑定 Java 操作 |

`text/header/context` 共享 `text/translate/args/inline`：优先使用 translate，先替换 args 中占位符再翻译，inline 默认 false。容器的 children 必填，text_separator 可选，仅影响纯文本连接。

### 折叠区与超链接

```json
{
  "type": "system:details",
  "summary": "{{tr:system.ui.search_sources}} ({{sources}})",
  "when": "sources",
  "children": [
    {
      "type": "system:column",
      "repeat": "sources",
      "children": [
        { "type": "system:link", "text": "[{{sources.*.id}}] {{sources.*.title}}", "url": "{{sources.*.url}}" },
        { "type": "system:context", "text": "{{sources.*.snippet}}", "when": "sources.*.snippet" }
      ]
    }
  ]
}
```

`details` 的 summary、children 必填，`open` 默认 false。children 使用完整节点语法，可嵌套其他折叠区、循环、物品和本地 / 服务端按钮；没有可见子节点时整个折叠区省略。展开状态仅属于当前渲染实例，重建或重开页面后按 open 初始化，不写业务数据、不请求模型或搜索。完整、简约模式可点击标题，HUD 需先按聊天键；纯文本导出保留标题和全部内容，TTS 仍只取独立的 SpeechContent。

`link` 的 url 必填，text 缺省时使用 URL。两者支持平铺占位符与循环下标，URL 以原值替换，不注入 Minecraft 格式重置符。点击复用 `Screen.clickUrlAction`，由原版校验外部 URL、读取 chatLinks/chatLinksPrompt，必要时显示原版确认界面，结束后返回当时的界面；非法链接不执行。链接不作为 AUI 导航或脚本执行，不触发服务端动作。完整、简约模式均可点击，HUD 需进入聊天栏；纯文本格式为标题与 URL。

折叠标题与链接采用 flex 按钮和独立的 block 文字子元素，避免按钮直接文字与行内片段重复绘制；标题的箭头单独占位。展开 / 收起时立即清理内容子树的布局缓存并提交重排。搜索模板用 search_source 块纵向排列来源，摘要通过 line-clamp 显示最多三行，完整内容可点击链接查看；这些显示限制不裁剪模型取得的证据。

| 占位符 | 含义 |
| --- | --- |
| `{{title}}` | 字符串、数值或布尔参数 |
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
  "type": "system:items",
  "repeat": "prizes",
  "items": "{{prizes.*.result_items}}",
  "description": [
    { "type": "system:text", "text": "{{prizes.*.label}}" }
  ]
}
```

每个 `*` 按从外到内的顺序替换下标。例如嵌套循环的 `rewards.*.choices.*.choice_title` 读取 `rewards.0.choices.1.choice_title`。repeat 本身先使用外层下标取次数，再进入当前循环。没有继承、局部变量或参数覆盖，点号只是 Map 键的一部分。

## 物品

items 字段引用参数表中的 `List<ItemPreviewOutput>`，缺失、类型不符和空列表不显示。条目保留 ItemStackTemplate 及数量字符串，数量可为范围。

可选字段：label 默认空，使用占位符设置分组标题；description 默认 []，使用相同节点语法生成物品说明；compact 缺省时通知为紧凑模式；show_labels 默认 true。物品绘制复用 `AssistantItems` 和 AUI 原生 Item，悬停使用原版 tooltip。

商城提供 offer_items、price_items；奖励提供 result_items。奖池奖品、签到候选和任务奖励各自用平铺的带下标键，分组与概率/状态说明均由 JSON 决定。物品节点不输出纯文本，文字导出使用 text_items 状态及对应文字参数。

## 按钮与动作

```json
{ "type": "system:button", "text": "{{claim_label}}", "callback": "claim" }
```

button 的 text、callback 必填。callback 只查询已注册的 Runnable；缺失时禁用按钮，不执行任何字符串指令。完整模式可点击，简约模式只显示标签，纯文本和通知模式不生成该按钮。

原有 actions 节点默认读取 `{{result_actions}}`，其按钮也通过同一张 callbacks 表执行。客户端给每个按钮填 `actions.0.label/number` 等参数，并登记 `actions.0.click`；CardData 构建时用 CardBuilder.actionParameter 给刚添加的按钮补充任务标题等字段。根 actions 的 container_style、button_style、numbered、label 继续控制外观：

```json
{
  "body": [{ "type": "system:actions" }],
  "actions": {
    "container_style": "quest_list",
    "button_style": ["card_action", "quest_row"],
    "numbered": false,
    "label": [
      { "type": "system:text", "text": "{{actions.*.action_title}}", "when": "actions.*.action_title" },
      { "type": "system:text", "text": "{{actions.*.label}}", "unless": "actions.*.action_title" }
    ]
  }
}
```

label 仅作纯文本投影。缺省使用动作原标签；简约模式按真实动作顺序显示编号，由输入执行选择。可信回调仍经过原有服务端权限、任务和交易校验。

CardButton 显式描述 Local/Server 动作、BODY/FOOTER 位置以及 CLOSE/WAIT 行为。actions 节点在通知正文只绘制 BODY 按钮，FOOTER 由通知宿主生成；宿主始终在业务按钮之后补充本地确定按钮，忙碌时仍可关闭通知。大任务列表直接放正文滚动区，不截断成少量底部按钮。

点击、数字和语音选择都进入 CardController。Local 通过处理器 Map 执行；Server 发 CardActionRequest(source, action)，仅含来源 CardRef、动作 Identifier 和字符串参数。工作流在 ModuleRouting.button 中解码，继续经过原有权限、预算、引用、Cost 与发奖流程，不经过模型路由。labelKey 只决定标签和语音候选，不决定动作。

CLOSE 停止当前语音/录音并关闭通知；WAIT 保留通知并暂停关闭计时，服务端按钮在等待期间禁用。失败、超时和零卡片回复也解除等待。语音优先匹配当前版本的实际按钮，失败或歧义才进入主聊天。

## 通用参数与模块字段

通用参数为 summary/card_title/result_actions、`actions.<下标>.label/number`；状态为 show_card_title/full/simple/notification/text_items。回调为 `actions.<下标>.click`。纯文本模式的 full/simple/notification 均为 0。

| 模板 | 参数 |
| --- | --- |
| player | name/health/max_health/experience_level/dimension/language，online_players.<下标>.player_name |
| items | items.<下标>.item_name/item_id/max_stack，result_items |
| sign_in_status | date/signed_in/history_saved/external_model，candidates.<下标>.reward_label/reward_items/result_items |
| reward/entitlement/purchase | reward_label/reward_items/result_items；回执另有 entitlement_id/state，购买另有 order_id |
| shop_offer | label/id/items/price/price_text/payment_available，offer_items/price_items |
| shop_order | order_id/label/state/error |
| lottery_pool | label/id/prize_count/preview_count/price/price_text/free/payment_available/price_items；prizes.<下标>.reward_label/chance/result_items |
| lottery_prize | reward_label/reward_items/chance/result_items，概率数值不含百分号 |
| delivery_state | state |
| quest/quest_submit | title，tasks.<下标>.task_title/task_progress/task_required；详情另有 chapter/description/progress/completed/can_complete |
| quest | rewards.<下标>.reward_title/reward_state/result_items，choices 使用 rewards.<下标>.choices.<下标>.choice_number/choice_title/result_items |
| quest_notice | id/title/chapter/description/requirements/other_rewards/manual_submission/result_items |
| quest_startup | total/remaining，quests.<下标>.title/progress |
| starter_gift | gifts.<下标>.result_items/error |

price 是非物品费用文字，price_text 包含物品文字。text 使用 summary，selection 使用已声明的动作。状态和历史没有卡片模板。

## 类型扩展与重载

节点实现位于 `screen/card/node`，各自独立文件。根接口 `CardNode` 定义：

- `codec()`：返回当前节点注册的稳定 MapCodec 实例。
- `css()`：默认样式，仍可由 JSON 的 style 覆盖。
- `render(view)`：生成 AUI 节点，返回 null 隐藏。
- `text(view)`：纯文本投影，默认空字符串。

`CardTemplate.Node` 只包装共用的 style/when/unless/repeat，不保存泛型类型包装或渲染函数。渲染器遍历后直接调用节点方法。TextNode 的 HeaderNode/ContextNode 子类复用文字实现，ContainerNode 的 RowNode/ColumnNode 子类复用容器实现；每种节点仍有自己的 MapCodec 和默认样式。

新增节点使用独立 Java 文件，例如：

```java
public record BadgeNode(String value) implements CardNode {
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
