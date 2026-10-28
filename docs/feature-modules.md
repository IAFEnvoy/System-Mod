# 功能模块化

功能定义和装配契约在 `com.iafenvoy.system.module`。两端统一使用正式内置注册表：`SystemRegistries.SERVER_MODULE`（`system:server_module`，元素为 `ServerModule`）和 `SystemRegistries.CLIENT_MODULE`（`system:client_module`，元素为 `ClientModule`）。两张表都在两端创建；客户端实现的条目仅由物理客户端入口装配，专用服务端的 CLIENT_MODULE 为空。两者均不启用数字 ID 网络同步，玩家可用模块仍通过已有状态快照同步。内置项由 `registry.SystemServerModules` / `SystemClientModules` 的 `DeferredRegister` 注册，公开的 `DeferredHolder` 在注册完成后通过 `.get()` 取得对象，注册类使用类级别 `@SuppressWarnings("unused")`。绑定、用量、玩家、物品、签到、收件箱、商城、抽奖、成就和 FTB 任务分别管理自己的贡献；Agent 执行代码继续位于 `com.iafenvoy.system.agent`。

## 两层模块

| 类型 | 生命周期与职责 |
| --- | --- |
| `ServerModule` | 无服务器状态的功能定义，声明工作流、游戏指令、存储和运行时工厂 |
| `ModuleContext` | 每台服务器独立创建，提供引擎、游戏桥接、交易服务、共享工具；不再收集独立的玩家回调 |
| `AgentModule<I>` | 一段有独立预算的工作流，声明指令别名、语义样例、参数提取、工具 List 和执行逻辑 |
| `ClientModule` | 仅客户端加载，声明本地指令、页面绑定、卡片展示、通知和口播规则 |

例如商城功能包含浏览、报价和确认购买三个工作流；抽奖功能也分别包含浏览、预览和确认抽取。一个功能可以组合多个工作流，每个工作流继续使用自己的执行策略，每个 Tool 只处理一个行为。

Agent 内置工作流和工具按 `agent/builtin/<功能>` 归类。`builtin/input`、`builtin/output` 保留独立输入输出类型；`BuiltinTools` 和 `ResultTypes` 仍是两端共享的类型与 Codec 契约，不负责安装功能或分派业务。

## 服务端装配与指令

`SystemRegistries` 在 `NewRegistryEvent` 中登记内置目录，模块通过标准 `DeferredRegister` / `RegisterEvent` 注册，冻结时机由 NeoForge 管理。模块 `id()` 默认从实际注册表取得，无须在实现中重复声明；未知模块不会作为默认值回退。定义只创建一次，不能把某台服务器的引擎、玩家或工具存到定义的静态字段。

每台服务器启动时，`ModuleManager` 遍历冻结的服务端注册表，调用各功能的 `workflows(context)`、注册返回的工作流并执行 `installed(context)`，随后创建各功能的服务器运行时。所有功能安装后仍发布原有的 `AssistantRegistrationEvent`，允许只需要添加工作流的扩展沿用原入口；工作流实例、路由与工具目录仍属于各自的 AgentEngine，同一工作流只能注册一次。

共享 Tool 通过上下文取得，保证两个功能使用同一个实例：

```java
var preview = context.tool(BuiltinTools.COMMERCE_PREVIEW,
        () -> new CommercePreviewTool(context.commerce()).tool());
return List.of(new ShopModule(preview), new ShopQuoteModule(preview),
        new ShopPurchaseModule(new ShopPurchaseTool(context.commerce()).tool()));
```

`SystemCommand` 只创建 `/system` 根节点、收集 `ServerModule.commands(root)`，并转发通用的 Agent 指令参数。`grant/revoke/reset` 在绑定模块，`usage` 在用量模块，打开助手及 `ask/routing/simple/full` 在核心模块。权限继续声明在对应 Brigadier 节点上。Agent 文本指令与语义样例仍由工作流的 `routing()` 一次声明，帮助、补全、模型目录复用同一注册表。

添加功能的注册方式如下；`ExampleServerModule` 实现 `ServerModule`，扩展模组须声明对 System 的依赖：

```java
@Mod("example")
public final class ExampleMod {
    public static final DeferredRegister<ServerModule> MODULES =
            DeferredRegister.create(SystemResourceKeys.SERVER_MODULE, "example");
    public static final DeferredHolder<ServerModule, ExampleServerModule> EXAMPLE =
            MODULES.register("example", ExampleServerModule::new);

    public ExampleMod(IEventBus bus) {
        MODULES.register(bus);
    }
}
```

## 玩家生命周期

功能通过 `ServerModule.runtime(context)` 返回每台服务器独立的 `PlayerModule`，统一由 `ModuleManager` 管理，不再调用 `context.players(...)` 或另挂 tick/服务器关闭监听：

1. `validate()` 在全部工作流注册后检查跨工作流引用。
2. `binding(player)` 在主线程捕获首次绑定所需数据，返回只操作 PlayerData 的事务回调；引擎不再硬编码成就基线。
3. 模块启用时，在主线程调用 `login(player)` 创建 Session，然后统一创建该模块声明的 DataStorage。
4. `Session.initialize(data)` 在玩家文件事务内执行，不能访问实时游戏对象。
5. 事务成功且连接、系统代次、模块激活实例均有效后，发布整批可用权限，再在主线程调用 `Session.ready()`。
6. `Session.tick()` 只对该玩家已就绪的模块调用；`PlayerModule.tick()` 是每服务器一次的共享维护钩子，不能将它当作玩家授权入口。
7. 动态移除、条件失效、重登、退出、撤销、重置或初始化失败时关闭 Session。停用保留账本，重新启用恢复数据，成功 revoke/reset 才清空文件。
8. 服务器关闭时先关闭玩家 Session，再按相反安装顺序调用 `PlayerModule.close()`。

成就模块负责自己的基线、登录事件缓冲与派发；商城和抽奖各自恢复自己的订单，收件箱只恢复投递。FTB 通知的 tick 和服务端清理由任务模块 runtime 接管，FTB 进度事件仍留在可选适配层。AssistantPlayers 保留绑定、连接检查和限流，将模块生命周期交给管理器。

新增存储由所属 ServerModule 声明，例如：

```java
public static final ModuleStorage<ExampleDataStorage> STORAGE = new ModuleStorage<>(
        ID, Identifier.fromNamespaceAndPath("example", "state"),
        ExampleDataStorage.class, ExampleDataStorage.CODEC, ExampleDataStorage::new);

@Override
public List<ModuleStorage<?>> storages() {
    return List.of(STORAGE);
}
```

存储 Codec 需另外注册到 Saturn 的 `DATA_STORAGE_TYPE`；内置项由 `SystemDataStorages.REGISTRY` 注册，扩展可使用自己的 `DeferredRegister<MapCodec<? extends DataStorage>>`，不能只声明 ModuleStorage 而遗漏 Codec 注册。事务内使用 `ModuleData.get(data, STORAGE)`，工具位于 `com.iafenvoy.system.module`。PlayerData 只保留通用字段和数据结构。管理器自动创建声明的存储，并检测存储是否重写 Saturn `tick(DataStorageContext)`；重写后自动纳入 tick，不需另注册事件。writtenAt 使用游戏 tick 时间，存储 tick 必须在状态变化时 markDirty，copy 必须深复制可变成员。

存储 tick 使用内存事务和 tryLock：有文件事务进行时跳过本次，主线程不等待磁盘；下一次仍传入当前游戏时间，可用于时间差结算。脏数据每秒合并一次异步保存，退出时提交保存，存储关闭时最终刷盘。签到、购买、发奖等业务继续使用原子落盘事务。无 tick 的存储不会进入逐 tick 拷贝流程。

## 客户端贡献

客户端模块同样通过标准内置注册表注册，注册入口须限定为 `Dist.CLIENT`。`SystemClientMod` 负责绑定 `SystemClientModules.REGISTRY` 和卡片节点的 DeferredRegister，专用服务端不会加载其实现。扩展可使用自己的客户端入口：

```java
@Mod(value = "example", dist = Dist.CLIENT)
public final class ExampleClientMod {
    public static final DeferredRegister<ClientModule> MODULES =
            DeferredRegister.create(SystemResourceKeys.CLIENT_MODULE, "example");
    public static final DeferredHolder<ClientModule, ExampleClientModule> EXAMPLE =
            MODULES.register("example", ExampleClientModule::new);

    public ExampleClientMod(IEventBus bus) {
        MODULES.register(bus);
    }
}
```

首次使用客户端贡献时，`ClientModuleBindings.get()` 遍历已冻结的客户端注册表，调用所有模块的 `register(context)`，一次性装配命令、页面、卡片、口播与通知动作，然后冻结贡献表。它只保存这些贡献，不另维护模块目录；客户端命令树也直接遍历正式注册表。卡片节点类型和协议类型的正式注册与冻结由 NeoForge 模组加载流程负责；ASR / TTS 工厂使用普通 Map，完整入口见[注册表速查](registries.md)。原来的两种自定义模块注册事件和模块 Map 目录已移除。

`ClientModule.register(context)` 可贡献以下内容：

| 入口 | 用途 |
| --- | --- |
| `command(action, aliases...)` / `pattern(handler, suggestions...)` | 助手内本地控制，执行和补全共用声明 |
| `workspace(page, navigation, group, bind)` | 页面、动态导航、分类与控件绑定；空页面/导航可用于工具栏；可另传翻译键 label |
| `localAction(id, handler)` | 扩展本地卡片动作，按模块可用性检查后执行；不负责业务结果构建 |
| `ClientModule.commands(root)` | 游戏聊天栏中的本地 Brigadier 指令，例如 `/system settings` |

完整侧栏按 `WorkspaceGroup.CHAT / MODULES / SETTINGS` 排列，分类标题来自双语语言键。导航按钮由注册表生成，无须在 HTML 手写；各功能决定自己的分类、标签和点击行为。默认 workspace 重载归入 MODULES，聊天入口显式归入 CHAT，设置与用量入口归入 SETTINGS。空分类自动隐藏，模块不可用时隐藏入口、移除本地补全并拦截控件调用；当前页面被禁用时回到聊天。

```java
module.workspace("example", "nav_example", "example.ui.open", WorkspaceGroup.MODULES, workspace -> {
    workspace.click("nav_example", () -> workspace.show("example"));
});
```

页面 HTML 与共享 CSS 仍是资源文件。扩展页面需要提供 `page_example` 等正文模板元素，导航按钮不再需要 HTML 元素；注册表不根据模型输出生成 HTML。`WorkspaceContext.input(id, placeholder, submit)` 声明 Enter 的目标按钮，输入框宿主不再硬编码每种查询框。

卡片 DTO 的 `buildCard()` 返回 CardBuilder，一处描述模板、参数、按钮、主文案和 SpeechContent；模板通过客户端 assets 加载。各实现只读服务端准备的快照，不访问模块装配、游戏状态、页面或模型。CardPresentation 补充本地翻译、模式和版本受控回调；普通卡片不需要在 ClientModule 里登记构建、文案、刷新或播报处理器。

JSON 节点仍在客户端 mod 入口通过 DeferredRegister 注册，不能在 ClientModule.register 或资源重载时增删类型。类型的 Codec 与资源 ID 独立，模板使用小写 {{参数}}、style 字符串/数组及 type 分派。仅单个纯文本节点且没有按钮/额外内容时显示为普通消息，带按钮仍为卡片。完整格式见[客户端 JSON 卡片](cards.md)。

绑定内容由服务端发送 BindingCardData；任务通知及礼包也是服务端完整 DTO。更新通过 CardRef + SHOW/REPLACE/REMOVE 定位到指定卡片，不按 Java 类型刷新。AgentResult.silent 显式控制是否新增提示/播报；请求完成不依赖是否有卡片。状态与历史分别使用 StateResponse、HistoryResponse，恢复历史不重新执行按钮。

卡片按钮显式区分本地和服务端动作。客户端处理 Local，Server 通过 CardActionRequest 进入 ModuleRouting.button 参数适配及原工作流；labelKey 仅用于标签和语音候选。所有扣款、发奖、引用与权限复核仍由服务端处理。ClientCardBindings 和 notificationAction 策略表已删除。

## 可选依赖与可用功能

服务端状态同步该玩家实际启用的功能 `Identifier` 列表，客户端按功能 ID 控制导航、本地别名与补全的可用性。`ModuleManager` 统一管理权限与运行时，内部 ModuleAccess 负责主线程条件求值和玩家持久覆盖，Agent 不在工作线程访问实时玩家或执行 Saturn 条件。客户端纯本地功能可声明 `serverRequired() == false`；独立用量账本继续在未绑定时可用。

FTB 仅在检测到 `ftbquests` 后注册服务端任务功能；可选 API 工厂通过反射加载，公共定义和客户端卡片只引用自己的 DTO，不解析 FTB 类。FTB 任务 API、通知事件及播报数据源仍集中在 `compat/ftb`。适配器安装失败会报告初始化错误，不把未成功安装的任务功能标为可用。

`SystemDefinition.modules` 现在提供模块 ID 和可选 Saturn condition；完整格式、内置 ID 与 action/condition 见[数据包与扩展配置](datapacks.md#系统定义)。内置工作流注册时记录所属 ServerModule，功能包含多个工作流时统一受同一开关约束。`ServerModule.required()` 默认 false，核心、绑定与用量模块声明为基础设施。

Java 扩展可用 `runtime.moduleManager().add/remove(player, id)` 在主线程持久调整玩家可用功能；模块本身须预先注册到 `SystemRegistries.SERVER_MODULE`，不能手动启用未注册的模块。新增模块完成存储初始化后才发布可用权限，移除立即关闭会话；初始化失败不宣称可用。FTB 通知与任务口播也检查任务模块开关。

每个 ServerModule 通过 storages() 声明自己的 Saturn DataStorage，Codec 通过 Saturn 注册事件独立注册；`ModuleData` 从正式服务端模块注册表查找存储声明。`ModuleData.get(data, STORAGE)` 负责读取和创建；底层 Holder 键固定为所属模块 ID，Codec 类型 ID 独立。商城和抽奖复用 CommerceDataStorage 类型但分别存到 system:shop、system:lottery，不会串用交易记录。

存储类靠近模块：签到 module/signin，收件箱 module/inbox，成就 module/advancement，共用交易类型 module/CommerceDataStorage。权限和 Agent 幂等日志由必需的 Core 模块声明，日志类位于 module/core，不随可选工作流停用清除。玩家文件仍为 `<存档>/system-agent/<UUID>.json`，不使用玩家附件，不提供未发布格式的旧档迁移。

`engine.registerModule(ownerId, workflow)` 可为直接注册的工作流指定所属功能。仅使用原来无 owner 的 `engine.registerModule(workflow)` 注册的独立工作流不受 ServerModule 的开关约束；需要纳入数据包管理的扩展应提供 ServerModule 并在 workflows 中返回工作流。Tool 的单行为契约、工作流预算、交易确认与玩家隔离保持原入口。网络协议保持 `1`，两端一起更新。
