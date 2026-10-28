# 功能模块化

功能装配入口在 `com.iafenvoy.system.module`。绑定、用量、玩家、物品、签到、收件箱、商城、抽奖、成就和 FTB 任务分别管理自己的贡献；Agent 执行代码继续位于 `com.iafenvoy.system.agent`。

## 两层模块

| 类型 | 生命周期与职责 |
| --- | --- |
| `SystemModule` | 无服务器状态的功能定义，声明工作流、游戏指令和安装钩子 |
| `ModuleContext` | 每台服务器独立创建，提供引擎、游戏桥接、交易服务、共享工具和玩家生命周期注册 |
| `AgentModule<I>` | 一段有独立预算的工作流，声明指令别名、语义样例、参数提取、工具 List 和执行逻辑 |
| `ClientModule` | 仅客户端加载，声明本地指令、页面绑定、卡片展示、通知和口播规则 |

例如商城功能包含浏览、报价和确认购买三个工作流；抽奖功能也分别包含浏览、预览和确认抽取。一个功能可以组合多个工作流，每个工作流继续使用自己的执行策略，每个 Tool 只处理一个行为。

Agent 内置工作流和工具按 `agent/builtin/<功能>` 归类。`builtin/input`、`builtin/output` 保留独立输入输出类型；`BuiltinTools` 和 `ResultTypes` 仍是两端共享的类型与 Codec 契约，不负责安装功能或分派业务。

## 服务端装配与指令

`SystemModules` 先注册内置功能，发布 `SystemModuleRegistrationEvent`，随后冻结注册表。功能 ID 重复会报错。定义只创建一次，不能把某台服务器的引擎、玩家或工具存到定义的静态字段。

每台服务器启动时调用各功能的 `workflows(context)`，统一注册返回的工作流，再调用 `installed(context)`。所有功能安装后仍发布原有的 `AssistantRegistrationEvent`，允许只需要添加工作流的扩展沿用原入口；同一工作流只能注册一次。

共享 Tool 通过上下文取得，保证两个功能使用同一个实例：

```java
var preview = context.tool(BuiltinTools.COMMERCE_PREVIEW,
        () -> new CommercePreviewTool(context.commerce()).tool());
return List.of(new ShopModule(preview), new ShopQuoteModule(preview),
        new ShopPurchaseModule(new ShopPurchaseTool(context.commerce()).tool()));
```

`SystemCommand` 只创建 `/system` 根节点、收集 `SystemModule.commands(root)`，并转发通用的 Agent 指令参数。`grant/revoke/reset` 在绑定模块，`usage` 在用量模块，打开助手及 `ask/routing/simple/full` 在核心模块。权限继续声明在对应 Brigadier 节点上。Agent 文本指令与语义样例仍由工作流的 `routing()` 一次声明，帮助、补全、模型目录复用同一注册表。

添加功能的注册方式如下；`ExampleSystemModule` 是扩展自行实现的功能定义：

```java
@EventBusSubscriber
public final class ExampleRegistration {
    @SubscribeEvent
    public static void register(SystemModuleRegistrationEvent event) {
        event.registry().register(new ExampleSystemModule());
    }
}
```

## 玩家生命周期

在功能的 `installed(context)` 中调用 `context.players(playerModule)`，注册每台服务器独立的 `PlayerModule`：

1. `validate()` 在全部工作流注册后检查跨工作流引用。
2. `grantBaselines(player)` 在主线程收集首次绑定的基线；不同功能的基线键不能重复。
3. `login(player)` 在主线程捕获游戏状态，返回该玩家本次连接的 `Session`。
4. `Session.initialize(data)` 在玩家文件事务内更新数据，不能访问实时游戏对象。
5. 事务成功且连接、初始化代次仍有效时，在主线程调用 `Session.ready()`。
6. 重登、退出、撤销、重置或初始化失败时关闭会话；旧异步回调不能激活新会话。

成就模块通过该入口管理基线、登录期间的事件缓冲、规则授权和派发。收件箱模块负责恢复交付及付款的不确定状态。`AssistantPlayers` 保留绑定事务、连接检查、限流和通用会话调度，不再解释成就规则或订单状态。

## 客户端贡献

`ClientModules` 发布 `ClientModuleRegistrationEvent` 后冻结客户端注册表。订阅类必须限定为 `Dist.CLIENT`，公共功能定义不要引用 GUI、AUI 或客户端模块类：

```java
@EventBusSubscriber(Dist.CLIENT)
public final class ExampleClientRegistration {
    @SubscribeEvent
    public static void register(ClientModuleRegistrationEvent event) {
        event.registry().register(new ExampleClientModule());
    }
}
```

`ClientModule.register(context)` 可贡献以下内容：

| 入口 | 用途 |
| --- | --- |
| `command(action, aliases...)` / `pattern(handler, suggestions...)` | 助手内本地控制，执行和补全共用声明 |
| `workspace(page, navigation, bind)` | 页面、导航和控件绑定；空页面/导航可用于工具栏 |
| `cards().register` | 每个输出只填一次 CardContext.Builder，统一提供平铺状态、对象参数与按钮回调；build 创建不可变副本 |
| `cards().notificationRows` | 通知中的代码生成选择行及样式，不由模板定义按钮 |
| `cards().summary` / `speech(key, handler)` | 通知开头提示与独立主文字口播；主体详情由卡片展示 |
| `cards().state` | 从类型化卡片更新客户端状态快照 |
| `cards().refresh` | 静默结果替换同类型卡片、更新或移除通知 |
| `registry().notificationAction` | 动作是否提供详情、等待刷新时保留通知、先打开工作台 |
| `ClientModule.commands(root)` | 游戏聊天栏中的本地 Brigadier 指令，例如 `/system settings` |

页面 HTML 与共享 CSS 仍是资源文件。扩展页面需要提供对应模板元素，注册表不根据模型输出生成 HTML。`WorkspaceContext.input(id, placeholder, submit)` 声明 Enter 的目标按钮，输入框宿主不再硬编码每种查询框。

卡片的正文、容器层次、条件、循环、物品/动作区位置及标签由 `assets/<namespace>/cards/<path>.json` 定义，按卡片数据类型 ID 选择。各节点在 `screen/card/node` 独立实现 `CardNode`，自己提供 codec/render/text/css；`CardNodeTypes` 只保存 ID 到 MapCodec 的映射，根接口通过 `type` 分派，`style` 等字段支持字符串或字符串数组，后者由 Codec 拼接。功能只填 `CardContext.Builder`，统一创建不可变的状态、参数与回调三表；循环只选择平铺键，不创建子 Context。分组标题、概率/状态说明和排列交给 JSON，不再编写业务卡片的正文渲染器。需要新布局类型时，在客户端注册事件或 ClientModule.register 中调用 `CardNodeTypes.register(id, NodeClass.CODEC)`；事件完成后冻结，资源重载前保证装配完成。完整格式见[客户端 JSON 卡片](cards.md)。

绑定消息由绑定客户端模块生成。任务模块管理任务列表标签、完成/解锁摘要、新手礼包静默刷新、口播和物品投影。通用工作台、简约界面、HUD 队列、物品渲染与 TTS 文本入口只调用注册后的处理器。

静默结果通过 `AgentResult.silent` 显式声明并随 Codec 编解码，不再由通用结果类按任务文案键推断。静默结果会结束等待，可按注册规则刷新已有卡片，不会新增一条提示或重复口播。

## 可选依赖与可用功能

服务端状态同步实际安装的功能 `Identifier` 列表，客户端按功能 ID 控制导航可用性，不再使用专门的 `questAvailable` 布尔值。客户端纯本地功能可声明 `serverRequired() == false`。

FTB 仅在检测到 `ftbquests` 后注册服务端任务功能；可选 API 工厂通过反射加载，公共定义和客户端卡片只引用自己的 DTO，不解析 FTB 类。FTB 任务 API、通知事件及播报数据源仍集中在 `compat/ftb`。适配器安装失败会报告初始化错误，不把未成功安装的任务功能标为可用。

数据包 `SystemDefinition.modules` 仍只是预留元数据，本次不根据该字段启停功能。权限、执行预算、交易确认、数据包校验和玩家隔离继续由既有服务端入口执行。网络协议保持 `1`；项目未发布，两端需要一起更新，不迁移旧状态字段。
