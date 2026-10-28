# 客户端 JSON 卡片

资源位于 `assets/<namespace>/cards/<path>.json`，按卡片数据类型 ID 选择；例如 `system:lottery_pool` 对应 `assets/system/cards/lottery_pool.json`。内置 22 种卡片均有默认文件，支持客户端资源包覆盖和 F3+T 重载。节点统一实现根接口 `CardNode`，由 `CardNode.CODEC` 通过 `dispatchMap("type", ...)` 分派。`CardNodeTypes` 只保存 ID 到 MapCodec 的映射，序列化、默认样式、AUI 渲染和纯文本投影都由节点自己实现。

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

功能模块每个输出只注册一次：

```java
module.cards().register(ExampleOutput.class, (builder, value) -> builder
        .put("title", value.title())
        .put("result_items", value.items()));
```

构建流程：类型化输出 → 填充 Builder → 构建不可变 Context → 模板解析/渲染。解析器不创建额外 Context；显示模式也存于同一份状态表。布局、分组标题、说明及按钮外观保留在 JSON，功能模块提供数据和操作。

## 布局与占位符

参数、状态及回调 ID 统一用小写下划线，文字占位符只支持 `{{id}}`。集合和 callback 字段可写 `{{id}}` 或直接写 ID；不兼容之前未发布的美元花括号格式或大写参数名。

```json
{
  "style": ["result_card"],
  "show_title": false,
  "body": [
    { "type": "header", "text": "{{title}}" },
    { "type": "items", "items": "{{result_items}}", "label": "{{field:reward}}" },
    { "type": "button", "text": "领取奖励", "callback": "claim", "when": "can_claim" }
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

所有节点都有 `type`，可选 `style/when/unless/repeat`。`style` 同样支持字符串/数组；`when` 的状态非 0 时显示，`unless` 的状态非 0 时隐藏，未知状态为 0。节点条件可直接写状态 ID 或 `{{id}}`。条件只读状态表，不猜测参数值类型。

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
  "type": "items",
  "repeat": "prizes",
  "items": "{{prizes.*.result_items}}",
  "description": [
    { "type": "text", "text": "{{prizes.*.label}}" }
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
{ "type": "button", "text": "{{claim_label}}", "callback": "claim" }
```

button 的 text、callback 必填。callback 只查询已注册的 Runnable；缺失时禁用按钮，不执行任何字符串指令。完整模式可点击，简约模式只显示标签，纯文本和通知模式不生成该按钮。

原有 actions 节点默认读取 `{{result_actions}}`，其按钮也通过同一张 callbacks 表执行。注册表给每个动作填 `actions.0.label/number` 等参数，并登记 `actions.0.click`；任务模块通过 Builder.forActions 给同一份表补充任务标题等标签字段。根 actions 的 container_style、button_style、numbered、label 继续控制外观：

```json
{
  "body": [{ "type": "actions" }],
  "actions": {
    "container_style": "quest_list",
    "button_style": ["card_action", "quest_row"],
    "numbered": false,
    "label": [
      { "type": "text", "text": "{{actions.*.action_title}}", "when": "actions.*.action_title" },
      { "type": "text", "text": "{{actions.*.label}}", "unless": "actions.*.action_title" }
    ]
  }
}
```

label 仅作纯文本投影。缺省使用动作原标签；简约模式按真实动作顺序显示编号，由输入执行选择。可信回调仍经过原有服务端权限、任务和交易校验。

**通知的确认、关闭、详情、领取按钮和任务选择行继续由代码生成。通知模式忽略全部 actions/button 节点。** HUD 主体可使用卡片，JSON 不接管其交互策略。语音主文字仍由独立 speech 注册提供。

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

随后在客户端模块的 `register(context)` 中登记 Codec：

```java
import com.iafenvoy.system.screen.card.CardNodeTypes;

CardNodeTypes.register("example:badge", BadgeNode.CODEC);
```

注册阶段完成后冻结；重复 ID、重复使用同一个 Codec 实例、未知类型或未注册的编码节点都会拒绝。节点的 MapCodec 不应包含共用字段；递归 children/description 使用 `Codec.lazyInitialized(() -> CardTemplate.Node.CODEC)`，避免静态初始化引用环。扩展不应绕过通知的代码按钮约定。

资源重载依次尝试高优先级资源包、低优先级资源包、上次有效模板；未知 type 或结构错误使整张模板回退，结构字段不作 lenient 吞错。删除资源会移除模板。完整/简约界面刷新已有内容；通知保留入队时模板和 Context，回调实例的身份不影响通知内容去重。

资源只位于客户端 assets，不进入服务端 data、存档或模型请求；无新增依赖，网络协议仍为 1。
