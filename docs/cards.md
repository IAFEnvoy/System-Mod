# 客户端 JSON 卡片

资源位于 `assets/<namespace>/cards/<path>.json`，`CardData.template()` 直接返回布局资源 ID，不需要再登记渲染映射。内置 22 种卡片均有默认文件，支持客户端资源包覆盖和 F3+T 重载。节点统一实现根接口 `CardNode`，由 `CardNode.CODEC` 通过正式注册表 `SystemRegistries.CARD_NODE_TYPE.byNameCodec().dispatchMap("type", ...)` 分派。内置条目声明于 `registry.SystemCardNodeTypes`，序列化、默认样式、AUI 渲染和纯文本投影都由节点自己实现。注册键与扩展方式见[注册表速查](registries.md)。

## 数据与渲染绑定

`AgentResult.Card.data` 直接持有 `CardData`，不再套 `TypedPayload`。各实现提供自己的 `MapCodec`、`TEMPLATE` 常量、`codec()` / `template()` 方法和 `buildContext(builder, formatting)` 参数填充。`SystemCardDataTypes` 只登记这些 MapCodec，注册表为两端共用的 `system:card_data_type`。根 `CardData.CODEC` 按 `type` 分派，字段直接平铺在同一个对象中，例如 `{"type":"system:quest","id":"...","title":"..."}`。业务结果仍由服务端生成；公共数据不引用客户端类或加载布局资源。模块动作仍使用 `TypedPayload`。

`CardContext` 与 `CardFormatting` 位于公共 `agent` 包。类型自己的 `buildContext(...)` 负责字段、列表及动作展示参数，不再向 `ClientCardBindings` 注册参数构建器。`CardFormatting` 通过调用方提供的 `text(Component)` 解析文字，复用奖励、价格和物品名称格式化；客户端传入 `Component::getString`，按本地语言生成文本。公共实现不引用 `AssistantView`、GUI 或客户端单例。

`CardPresentation` 统一负责客户端展示，不依赖行为登记表。`CardPresentation.context(...)` 先将卡片动作写入 Builder，再调用 `data.buildContext(builder, formatting)`；任务列表编号及奖励选项仍可通过 `builder.forActions(...)` 读取动作数据。随后客户端统一补充标题、显示模式、按钮标签及发送请求的回调，最终构建不可变快照。`CardPresentation.of(data, context)` 接收这个快照，直接调用 `CardTemplates.get(data.template())` 读取当前布局，没有额外的模板别名或渲染工厂表。数据 Codec 的注册 ID 与布局资源 ID 可以不同。

```java
// In ExampleOutput, template() returns this resource ID:
public static final Identifier TEMPLATE = Identifier.parse("example:compact_reward");

@Override
public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
    builder.put("title", this.title());
}
```

以上渲染使用 `assets/example/cards/compact_reward.json`，与数据类型 ID 无关。多个数据实现可返回同一个资源 ID 共用布局；更换外观可以覆盖 JSON，无需修改数据 Codec 或业务处理。每次创建展示对象时读取最新模板，已入队的通知保留创建时的布局及上下文快照。图形卡片、纯文本投影和按钮标签共用同一条模板解析路径；缺少模板时使用通用回退展示，回退文字只加入上下文副本，不修改调用方传入的上下文。

## 一个卡片，一份 Context

`CardContext` 只有三个字段：

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `states` | `Object2DoubleMap<String>` | 启用状态；0 为关闭，非 0 为开启，也可保存循环次数 |
| `parameters` | `Map<String, Object>` | 所有文字、数值、布尔值、物品和动作参数 |
| `callbacks` | `Map<String, Runnable>` | 由 Java 注册的按钮回调 |

整张卡片共用一个不可变 Context，没有子 Context、作用域覆盖或单独的物品/动作参数表。Builder 负责填充并在 `build()` 时复制三张表；`put` 收到 List 时也会复制列表。任意业务对象仍由调用方保证其使用期间稳定，不对 Object 作反射或序列化复制。

```java
CardContext context = CardContext.builder()
        .put("title", "奖励已就绪")
        .put("result_items", previews)
        .enable("can_claim", true)
        .enable("full", true)
        .callback("claim", this::claim)
        .build();

String title = context.get("title", String.class);
Number count = context.get("title", Number.class); // null: the stored type does not match
```

`public <T> T get(String id, Class<T> clazz)` 先取参数，只有 `clazz.isInstance(value)` 时返回 `clazz.cast(value)`，缺失或类型不符返回 null。集合取出时先检查 List 类型，再检查每个元素类型。

Builder 的 `put` 保留实际 Java 类型，同时设置同名状态：布尔值为 1/0，数值为自身值，字符串按是否为空白设置 1/0，集合取大小，其余对象为 1。`enable(id, boolean/double)` 可单独覆盖启用状态。`callback` 只登记操作，不把回调混入参数表。

`ClientCardBindings` 只登记客户端消息、状态更新、刷新、任务播报引用与通知行样式，不再包含上下文构建或渲染入口。功能模块只在需要这些行为时注册对应输出；普通卡片提供 CardData 实现和 JSON 模板即可展示。例如：

```java
module.cards().register(ExampleOutput.class)
        .message("example.result.ready", (_, value) -> new ClientMessage(
                "奖励「" + value.title() + "」已准备就绪。"));
```

图形展示入口为 `CardPresentation.of(card, messageKey, mode, actionOffset)`；`format(card, textItems)` 和 `label(card, action)` 使用同一条模板及上下文构建路径，分别生成纯文本和动作标签。传入已建好的上下文时仍可用 `of(data, context)`。

构建流程：客户端预填动作 → `CardData.buildContext(...)` 填参数 → 客户端补充通用展示状态和回调 → 构建不可变 Context → 模板解析/渲染。解析器不创建额外 Context；显示模式也存于同一份状态表。布局、分组标题、说明及按钮外观保留在 JSON，功能模块提供消息和交互行为。18 种普通展示数据分别覆盖构建方法；`EmptyCardData`、`ClientState`、`History`、`QuestClaimOutput` 沿用默认空填充，仍取得通用标题与动作上下文。

`register` 返回的 `Registration<T>` 可继续链式声明 `message/quests/notificationRows/state/refresh`，不必为同一种输出重复传 Class。`message(key, (card, value) -> ...)` 直接取得对应卡片及 DTO，统一返回 `ClientMessage`：单参数构造让显示与播报共用文字；双参数构造可分别提供文字；`ClientMessage.speech(text)` 只覆盖播报。null 字段使用消息键与正文的默认文本，空字符串表示不显示或不播报。没有卡片的消息可用 `module.message(key, result -> ...)`；只需为某种输出声明消息时，可用 `module.message(key, Output.class, (card, value) -> ...)`，无需登记类型行为。通知和语音共用消息处理器，参数布局仍由模板独立控制。

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

JSON 控制顺序、嵌套、样式和可见性，框架不在正文末尾自动添加物品或按钮。

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
| `divider` | `card_divider` | 分割线 |
| `items` | `item_groups` | 原版物品图标、数量及 tooltip |
| `actions` | `card_actions` | 已有可信动作列表 |
| `button` | `card_action` | 通过 callback ID 绑定 Java 操作 |

`text/header/context` 共享 `text/translate/args/inline`：优先使用 translate，先替换 args 中占位符再翻译，inline 默认 false。容器的 children 必填，text_separator 可选，仅影响纯文本连接。

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

原有 actions 节点默认读取 `{{result_actions}}`，其按钮也通过同一张 callbacks 表执行。注册表给每个动作填 `actions.0.label/number` 等参数，并登记 `actions.0.click`；任务模块通过 Builder.forActions 给同一份表补充任务标题等标签字段。根 actions 的 container_style、button_style、numbered、label 继续控制外观：

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

**通知的确认、关闭、详情、领取按钮和任务选择行继续由代码生成。通知模式忽略全部 actions/button 节点。** HUD 主体可使用卡片，JSON 不接管其交互策略。语音主文字来自同一个消息处理器返回的 ClientMessage.speech，不从卡片布局提取；任务要求、奖励与背景描述仍按原配置另行追加播报。

## 通用参数与模块字段

通用参数为 card_title/card_type/result_actions、`actions.<下标>.label/number`；状态为 show_card_title/full/simple/notification/text_items。回调为 `actions.<下标>.click`。纯文本模式的 full/simple/notification 均为 0。

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
| preferences/delivery_state | history_saved/external_model 或 state |
| quest/quest_submit | title，tasks.<下标>.task_title/task_progress/task_required；详情另有 chapter/description/progress/completed/can_complete |
| quest | rewards.<下标>.reward_title/reward_state/result_items，choices 使用 rewards.<下标>.choices.<下标>.choice_number/choice_title/result_items |
| quest_notice | id/title/chapter/description/requirements/other_rewards/manual_submission/result_items |
| quest_startup | total/remaining，quests.<下标>.title/progress |
| starter_gift | gifts.<下标>.result_items/error |

price 是非物品费用文字，price_text 包含物品文字。empty/claim/client_state/history 默认没有额外正文。

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

资源重载依次尝试高优先级资源包、低优先级资源包、上次有效模板；未知 type 或结构错误使整张模板回退，结构字段不作 lenient 吞错。删除资源会移除模板。完整/简约界面刷新已有内容；通知保留入队时模板和 Context，回调实例的身份不影响通知内容去重。

资源只位于客户端 assets，不进入服务端 data、存档或模型请求；无新增依赖，网络协议仍为 1。
