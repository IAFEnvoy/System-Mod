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

知识问答使用 `KnowledgeAnswerOutput` / `system:knowledge_answer`，正文显示回答、本地来源默认折叠，包含标题、分类和命中段落；只播报回答或原文提示，不提供网页链接或任务操作。联网回退继续使用独立的 WebSearchOutput / web_search 模板；两种卡片都只展示服务端准备的结果。

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
| `icons` | `quest_icon_group` | FTB 任务 / 奖励的原生类型图标、数量 / 进度及文字 tooltip |
| `actions` | `card_actions` | 已有可信动作列表 |
| `button` | `card_action` | 通过 callback ID 绑定 Java 操作 |

`text/header/context` 共享 `text/translate/args/inline`：优先使用 translate，先替换 args 中占位符再翻译，inline 默认 false。容器的 children 必填，text_separator 可选，仅影响纯文本连接。

### 任务与奖励类型图标

```json
{
  "type": "system:icons",
  "icons": "{{reward_icons}}",
  "label": "{{field:reward}}"
}
```

`icons` 绑定 `List<QuestIconOutput>`，label 可选，均支持平铺占位符及循环。每项包含 task / reward 分类、FTB 原生类型 ID、本地化标题、数量或进度、tooltip 的 Component 列表；不序列化 FTB Icon，不发送客户端对象或脚本。原生物品目标 / 奖励继续使用 `items` 节点和原版物品 tooltip，非物品类型使用截图中 FTB 创建菜单的类型图标；不按目标实体或配置图标替换类型图标。

`IconsNode` 在首次实际渲染时检查 FTB 安装情况，反射加载隔离的 `compat.ftb.client.FtbQuestIconElement`；通过 TaskTypes / RewardTypes 的类型表与 getIcon 取得 FTB 原始图标，使用其 renderer 提取贴图、atlas、组合、动画及第三方已注册类型的绘制状态，不复制资源或手写 ID 到贴图的映射。26.1.2 的 FtbQuestIconRenderer 在 AUI 的活动目标和投影中重放提取的几何、文字与物品，不调用完整 GuiRenderer 来改写渲染目标；版本适配记录见 [跨版本同步](ftb-quests-porting.md)。缺少依赖 / 未知类型时显示文字。专服不会执行图标解析或加载这个客户端适配器。

图标元素在 AUI BODY 阶段绘制，沿用当前 pose、滚动裁剪、变换及 opacity 合成；与现有物品 tooltip 共用屏幕绘制后的检测入口，通过原版 GuiGraphicsExtractor.setTooltipForNextFrame 显示标题、类型、具体要求 / 奖励、进度 / 领取状态。HUD 需先按聊天键进入交互状态。完整模式、简约模式、任务通知、自选奖励和新手礼包共用这些数据和模板，TTS 继续使用独立 SpeechContent；纯文本导出保留具体内容。

礼包、任务详情和任务通知使用 `reward_section` 外层统一显示一次“奖励”标题，内层 `reward_grid` / `reward_entry` 将物品与非物品图标横向排列，宽度不足时换行。子节点不再设置重复的奖励 label，物品使用 compact 并隐藏分组标签；自选项保留各自编号和名称。礼包整体的 `items` / `icons` 由服务端汇总，模板绑定顶层 `result_items` / `reward_icons`：经验值、经验等级各自相加，同物品且组件相同的数量 / 随机区间相加，原生 `only_one` 条件物品保留独立。`gifts` 仅保留任务 ID、领取引用和错误；失败文字在汇总预览下方逐项显示。礼包与 `QuestNotice` 的 `has_rewards` 均由物品及类型图标是否为空决定，无奖励时不显示空标题。

普通任务每批仅解锁一项时直接发送 `QuestOutput` 完整详情，播报使用新任务主句及个人设置允许的要求、奖励和背景。多项时使用 `QuestUnlockedOutput`，复用 QuestList 的按 ID 查看按钮与 `system:quest_list` 模板；`unlock_notice` 隐藏主动查询的编号提示和行编号，摘要与播报仅提示本批数量。通知内每页 10 项，点开详情后可返回原列表和原页码。完成任务仍每项各发一条独立通知。

`QuestGuideOutput` 使用 `system:quest_guide` 类型及同名模板，包含查询词和已授权的来源条目。每条提供标题、章节、匹配段落及原文；`has_more` 控制本地 details 展开，不发送任务提交或领奖动作。播报只提示命中数量，正文不自动发给模型。来源收录和权限边界见 [本地说明书检索](quest-guides.md)。

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

商城提供 offer_items、price_items；奖励提供 result_items。奖池奖品、签到候选和任务奖励各自用平铺的带下标键，分组与概率/状态说明均由 JSON 决定。物品节点不输出纯文本，文字导出使用 text_items 状态及对应文字参数。FTB 物品任务同时提供 `task_items`，详情卡片会把目标物品作为原版图标渲染并保留进度文字；无法确定具体物品的任务仍只显示标题。

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

`actions` 节点可设置 `notification_page_size`（默认 0，不分页）；内置任务列表设置为 10，仅通知模式生效。上页、页码、下页在节点内部生成，与宿主底部确定及业务按钮分开。翻页仅修改当前通知的 `CardPageState` 并重绘该节点，不发包、不替换通知、不推进队列或重启播报；完整 / 简约界面仍展示完整列表。页码按动作数据绑定键保存，跟随通知窗口存活，不序列化至服务端或聊天历史。

CardButton 显式描述 Local/Server 动作、BODY/FOOTER 位置以及 CLOSE/WAIT/REPLACE/PUSH 行为。actions 节点在通知正文只绘制 BODY 按钮，FOOTER 由通知宿主生成；宿主始终在业务按钮之后补充本地确定按钮，忙碌时仍可关闭通知。列表内容使用原有固定宽度和限高滚动视口，分页不扩大通知面板。

点击、数字和语音选择都进入 CardController。Local 从 ClientModuleCaches.BINDINGS 的处理器 Map 分派；Map 和登记、分派、清理逻辑直接位于 ClientModuleBindingsCache，由模块通过 localAction 声明，随 Saturn RegistryCache 重建和退出清理。Controller 不单独保存永久动作表。Server 发 CardActionRequest(source, action)，仅含来源 CardRef、动作 Identifier 和字符串参数。工作流在 ModuleRouting.button 中解码，继续经过原有权限、预算、引用、Cost 与发奖流程，不经过模型路由。labelKey 只决定标签和语音候选，不决定动作。

CLOSE 停止当前语音/录音并关闭通知；WAIT 保留通知并等待回复。REPLACE 同样等待，但把直接回复的新 SHOW 卡片放到当前通知的位置，旧内容淡出后新内容淡入，不收起面板或重复播放提示音；其余排队通知保持顺序。CardBuilder.serverButton 默认使用 REPLACE。任务列表查看按钮使用 PUSH：收到可显示的回复后保留来源视图，详情正文上方提供本地“返回列表”，返回时恢复原页码且不重复播报列表。失败的零卡片回复保留原列表；业务 REMOVE 会清除对应返回视图。底部确定结束整个当前窗口并清理返回栈，再取下一条通知。通知没有自动关闭计时，播报结束也不会切换到下一条。

仅从 HUD 激活的 REPLACE / PUSH 按钮会在客户端 pending 中记录来源 CardRef，并按 requestId 关联回复；完整/简约页面中的同一按钮仍按页面流程处理。原通知已确认、关闭或返回上层时，迟到回复不重新弹出，也不替换其他通知。独立推送继续入队；业务 CardUpdate.REPLACE/REMOVE 仍沿用静默更新语义。等待期间禁用服务端按钮，失败、超时和零卡片回复也解除等待；本地翻页、返回和确定独立于服务端忙碌状态。语音优先匹配当前通知的业务按钮、返回及确定，失败或歧义才进入主聊天。

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
| quest/quest_submit | title，tasks.<下标>.task_title/task_progress/task_required/task_items；详情另有 chapter/description/progress/completed；submission 枚举由 CardData 构建提交或勾选按钮 |
| quest | rewards.<下标>.reward_title/reward_state/result_items，choices 使用 rewards.<下标>.choices.<下标>.choice_number/choice_title/result_items |
| quest_notice | id/title/chapter/description/requirements/other_rewards/manual_submission/result_items |
| quest_startup | total/remaining，quests.<下标>.title/progress |
| starter_gift | gifts.<下标>.result_items/error |

任务详情的 `title` 在任务名称前追加绿色 ✓（已完成）或红色 ✗（未完成），只给符号着色，保留原任务名称的样式。summary 与标题使用同一份组件，避免自动摘要重复显示任务名；模板不再单独显示“已完成”一行，语音仍读取原有 SpeechContent。

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
