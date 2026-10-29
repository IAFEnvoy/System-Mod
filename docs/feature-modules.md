# 功能模块化

公共模块接口和运行时管理位于 `com.iafenvoy.system.module`，客户端通用设施保留在 `module.client`。内置模块及其辅助类平铺在 `module.builtin`，六个内置 DataStorage 实现集中在 `module.builtin.storage`；不再按功能各建一个仅有一两个类的子包。Agent 工作流和单行为工具继续位于独立的 `com.iafenvoy.system.agent.builtin.<功能>`。

功能定义和装配契约在 `com.iafenvoy.system.module`。两端统一使用正式内置注册表：`SystemRegistries.SERVER_MODULE`（`system:server_module`，元素为 `ServerModule`）和 `SystemRegistries.CLIENT_MODULE`（`system:client_module`，元素为 `ClientModule`）。两张表都在两端创建；客户端实现的条目仅由物理客户端入口装配，专用服务端的 CLIENT_MODULE 为空。两者均不启用数字 ID 网络同步，玩家可用模块仍通过已有状态快照同步。内置项由 `registry.SystemServerModules` / `SystemClientModules` 的 `DeferredRegister` 注册，公开的 `DeferredHolder` 在注册完成后通过 `.get()` 取得对象，注册类使用类级别 `@SuppressWarnings("unused")`。绑定、用量、玩家、物品、签到、收件箱、商城、抽奖、成就和 FTB 任务分别管理自己的贡献；Agent 执行代码继续位于 `com.iafenvoy.system.agent`。

## 两层模块

| 类型 | 生命周期与职责 |
| --- | --- |
| `ServerModule` | 服务端生命周期基接口，声明依赖可用性、存储、运行时工厂和安装钩子 |
| `ToolModule extends ServerModule` | 纯服务端工具集合，通过 `tools(context)` 导出单行为工具，不提供工作流、玩家指令、按钮或路由入口 |
| `WorkflowModule extends ServerModule` | 声明 `workflows(context)`，支持成就等没有客户端页面的服务端事件工作流 |
| `SharedModule extends WorkflowModule, ClientModule` | 双端业务功能，增加 `serverCommands(root)`；客户端入口按服务端同步的模块权限启用 |
| `CoreModule extends SharedModule` | 核心基础设施，绑定后始终启用且不能手动移除；客户端基础入口无需可选模块权限 |
| `ModuleContext` | 每台服务器独立创建，提供引擎、游戏桥接、交易服务、共享工具；不再收集独立的玩家回调 |
| `AgentModule<I>` | 一段有独立预算的工作流，声明指令别名、语义样例、参数提取、工具 List 和执行逻辑 |
| `ClientModule` | 公共端安全的客户端基接口，提供本地指令、页面和动作；单独实现时无需同名服务端模块 |

例如商城功能包含浏览、报价和确认购买三个工作流；抽奖功能也分别包含浏览、预览和确认抽取。一个功能可以组合多个工作流，每个工作流继续使用自己的执行策略，每个 Tool 只处理一个行为。

Agent 内置工作流和工具按 `agent/builtin/<功能>` 归类。`builtin/input`、`builtin/output` 保留独立输入输出类型；`BuiltinTools` 和 `ResultTypes` 仍是两端共享的类型与 Codec 契约，不负责安装功能或分派业务。

功能类型由实现的接口决定：`ModuleAccess`、管理员命令和客户端上下文直接检查 `CoreModule` / `SharedModule` 等类型，接口不再提供 `required()`、`serverRequired()` 这类分类布尔值。`ServerModule.available()` 保留为依赖是否存在的运行时检查，例如 FTB 是否安装；它不决定模块类型。`AgentModule.Descriptor` 中的交互 / 事件标记仍描述具体工作流的入口授权，与外层功能分类独立。

### 同一个类提供双端功能

核心、绑定、用量实现 `CoreModule`；玩家、物品、签到、收件箱、商城、抽奖、任务和搜索实现 `SharedModule`。这些类型通过继承同时具备 `ServerModule` 和 `ClientModule` 契约，两张注册表分别注册同一个实现类的无状态实例。成就实现 `WorkflowModule`，对话、设置和语音单独实现 `ClientModule`；没有强制一一对应关系。接口不提供 `id()`，装配时分别用 `SystemRegistries.SERVER_MODULE.getKey(module)` / `CLIENT_MODULE.getKey(module)` 取得当前注册表的键。运行时 Installed 记录保存查表得到的 ID，不让公共定义判断该查哪张表。

服务端调用 `serverCommands(root)`、workflows、storages 和 runtime；物理客户端的缓存装配调用 register 和 `clientCommands(root)`。联合实现的 register 只转发到私有静态内部类 ClientHooks，原客户端页面、动作、补全和回调都放在该内部类，外层构造和静态字段只保留公共端安全的数据。加载外层类和 ClientModule 接口不会因此自动加载 ClientHooks；不要在公共构造、静态初始化或服务端钩子里调用客户端实现。两张目录仍独立注册，服务器上的 CLIENT_MODULE 为空，不遍历或调用客户端贡献。

```java
public final class ExampleModule implements SharedModule {
    @Override
    public void register(ClientModuleContext context) {
        ClientHooks.register(context);
    }

    private static final class ClientHooks {
        private static void register(ClientModuleContext context) {
            // Client UI declarations and callbacks belong here.
        }
    }
}
```

## 服务端装配与指令

`SystemRegistries` 在 `NewRegistryEvent` 中登记内置目录，模块通过标准 `DeferredRegister` / `RegisterEvent` 注册，冻结时机由 NeoForge 管理。模块 ID 由使用方直接从对应注册表查找，接口不再提供 `id()`；未知模块不会作为默认值回退。定义按注册表创建一次，不能把某台服务器的引擎、玩家或工具存到定义的字段。

每台服务器启动时，`ModuleManager` 从冻结的服务端注册表筛选 `available()` 为 true 的定义，先注册所有 `ToolModule.tools(context)`，再装配 `WorkflowModule.workflows(context)` 并执行各模块的 `installed(context)`，最后创建服务器运行时。工具模块不能同时实现工作流或客户端接口；导出工具先安装，消费方不依赖注册顺序。不可用定义不调用这些钩子，也不访问 `storages()`；服务端指令装配同样先检查可用性。所有可用功能安装后仍发布原有的 `AssistantRegistrationEvent`，允许只需要添加工作流的扩展沿用原入口；工作流实例、路由与工具目录仍属于各自的 AgentEngine，同一工作流只能注册一次。

共享 Tool 通过上下文取得，保证两个功能使用同一个实例：

```java
var preview = context.tool(BuiltinTools.COMMERCE_PREVIEW,
        () -> new CommercePreviewTool(context.commerce()).tool());
return List.of(new ShopModule(preview), new ShopQuoteModule(preview),
        new ShopPurchaseModule(new ShopPurchaseTool(context.commerce()).tool()));
```

`SystemCommand` 只创建 `/system` 根节点、收集 `SharedModule.serverCommands(root)`，并转发通用的 Agent 指令参数。`grant/revoke/reset` 在绑定模块，`usage` 在用量模块，打开助手及 `ask/routing/simple/full` 在核心模块。模块管理的 `/system module add/remove <玩家> <模块ID>` 与 `list [玩家]` 由核心模块的 `ModuleManagementCommands` 装配，复用 ModuleManager，不进入 Agent 路由。权限继续声明在对应 Brigadier 节点上。Agent 文本指令与语义样例仍由工作流的 `routing()` 一次声明，帮助、补全、模型目录复用同一注册表。

添加功能的注册方式如下；`ExampleServerModule` 根据职责实现 `ServerModule` 的对应子接口，扩展模组须声明对 System 的依赖：

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

### 纯工具模块

工具模块只登记到服务端功能注册表，数据包 / tag / 管理员命令可以控制其启用状态。它导出的工具进入当前 AgentEngine 的工具目录，不产生 AgentAction、文本指令、语义例句、模型意图路由或卡片按钮入口；玩家请求无法把工具 ID 当作工作流执行。每个工具继续只处理一个行为，模块用 List 组合多个工具：

```java
public final class ExampleToolModule implements ToolModule {
    @Override
    public List<ToolRegistry.Tool<?, ?>> tools(ModuleContext context) {
        return List.of(context.tool(ExampleTools.QUERY,
                () -> new ExampleQueryTool(context.game()).tool()));
    }
}
```

消费方在 `workflows(context)` 中用 `context.tool(ExampleTools.QUERY)` 取得已安装的同一工具实例，并传入工作流构造方法、列入该工作流的 `tools()` 白名单。运行中的工作流通过 `ExecutionContext.tool(type, stepId, input)` 调用；每次执行同时检查工具所属功能的玩家权限、工作流白名单、预算、事件授权与写入回执。依赖导出工具的工作流仅在这些工具的所属模块均启用时可用；移除工具模块后，路由、补全、在途流程及已排入游戏线程的操作共用权限复核。工具模块未安装时，消费方应避免装配依赖它的工作流。

该接口提供进程内类型化工具，不建立新的网络传输或玩家请求通道；具体模型规划仍由消费方工作流负责。当前内置工具继续随原业务工作流装配，没有为接口分类新增功能 ID 或改变现有数据包格式。

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

客户端模块同样通过标准内置注册表注册，注册入口须限定为 `Dist.CLIENT`。`SystemClientMod` 负责绑定 `SystemClientModules.REGISTRY` 和卡片节点的 DeferredRegister，专用服务端不会执行客户端登记或 UI 钩子。联合模块的外层类会通过服务端注册表加载，实际 UI 实现隔离在 ClientHooks 中。扩展可使用自己的客户端入口，示例中的 ExampleClientModule 也可以换成同时实现两个接口的 ExampleModule：

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

`ClientModuleCaches.BINDINGS.get()` 确保客户端贡献已装配；Workspace 列表和本地动作 Map 直接放在 `ClientModuleBindingsCache`，命令声明及编译树直接放在 `ClientModuleCaches.COMMAND` 对应的 `ClientCommandCache`。每个 `ClientModuleContext` 保存模块定义，按 `SharedModule` / `CoreModule` 类型实时检查权限；Workspace 复用同一上下文，不另存分类布尔值或重复实现可用性逻辑。绑定缓存继承 Saturn `RegistryCache` 并统一协调两者重建和清理，从物理客户端入口提前初始化，构造时只加入 Saturn 的生命周期列表，不读取尚未冻结的模块条目。首次实际读取时按需装配；`TagsUpdatedEvent.shouldUpdateStaticData()` 为 true 时重建，客户端退出时清空，下次使用再建。它按注册表生命周期维护，没有定时器或逐 tick 重扫；集成服务端下沿用 Saturn 对静态数据更新事件的筛选。

每次装配清空旧数据，遍历已冻结客户端注册表一次并调用 `register(context)` 与 `clientCommands(root)`，结束后冻结页面和动作集合并编译命令树；临时命令 Builder 随即释放。旧 ClientModuleBindings / ClientCommandRegistry 中转类已删除，数据和维护代码都收进对应 Cache 类。装配失败同时清空两份缓存，后续读取可重新构建。`register` 应只声明贡献，可以重复执行，不应修改游戏状态、发请求或向外部静态表注册回调。页面绑定函数仍在每次创建页面时接收当前 Document，不把已打开页面或玩家业务状态存进缓存；模块可用性在调用时读取当前玩家快照，不缓存成布尔值。

本地卡片动作通过 `module.localAction` 装配进绑定缓存，CardController 只负责检查来源、处理按钮状态并从当前缓存分派动作，不再持有永久处理器 Map。关闭、打开助手、工作台跳转属于 CoreSystemModule.ClientHooks，设置动作属于 SettingsClientModule。模板资源重载只加载 JSON，不再顺带初始化模块绑定。正式注册表仍是模块目录，RegisterClientCommandsEvent 直接复制命令缓存中的游戏命令节点，避免合并修改缓存；异步补全以缓存 revision 排除重建前的迟到结果。卡片节点类型和协议类型由 NeoForge 登记和冻结，ASR / TTS 工厂仍使用普通 Map，见[注册表速查](registries.md)。

`ClientModule.register(context)` 可贡献以下内容：

| 入口 | 用途 |
| --- | --- |
| `command(action, aliases...)` / `command(node)` | 助手内本地控制；简单别名或完整 Brigadier 节点，执行与补全共用声明 |
| `suggest(node)` | 仅提供助手补全，如当前任务序号和名称，不拦截服务端命令执行 |
| `workspace(page, navigation, group, bind)` | 页面、动态导航、分类与控件绑定；空页面/导航可用于工具栏；可另传翻译键 label |
| `localAction(id, handler)` | 扩展本地卡片动作，按模块可用性检查后执行；不负责业务结果构建 |
| `ClientModule.clientCommands(root)` | 游戏聊天栏中的本地 Brigadier 指令，例如 `/system settings` |

命令回调通过当前玩家快照判断模块权限；补全前过滤不可用节点，因为 Brigadier.getCompletionSuggestions 本身不会检查 requires。任务模块负责当前任务列表的候选，核心模块负责当前按钮序号，均不把候选存进缓存。原生服务端补全来自连接已同步的 `/system` 树，仅展示助手有执行入口的工作流和本地代理；模块管理等仅有聊天栏入口的指令不混入助手候选。ClientState.commands 仅提供工作流别名范围，不再充当字符串补全列表。

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

服务端状态同步该玩家实际启用的功能 `Identifier` 列表，客户端按功能 ID 控制导航、本地别名与补全的可用性。`ModuleManager` 统一管理权限与运行时，内部 ModuleAccess 负责主线程条件求值和玩家持久覆盖，Agent 不在工作线程访问实时玩家或执行 Saturn 条件。单独实现 `ClientModule` 的本地功能无需服务端模块权限，`CoreModule` 的基础入口也保持可用；独立用量账本继续在未绑定时可用。

`ServerModule.available()` 默认 true，表示当前安装环境是否满足该模块的依赖，与玩家级的模块开关及 `required()` 分开。可选模块也始终使用普通 `DeferredHolder` 注册安全定义，在模块内部重写此方法检查依赖；定义的构造、静态字段、方法签名和 Codec 必须不引用可选 API，检查结果应在模组加载后保持稳定。不可用模块不创建工作流、运行时或玩家会话，不注册指令，也不进入模块增删补全；手动添加和 Saturn action 均无法绕过检查。`/system module list` 仍显示完整注册目录，带玩家参数时只显示实际已激活的模块。

FTB 的 `QuestSystemModule.available()` 检查 `ModList.get().isLoaded("ftbquests")`，注册类不再处理条件注册或 `Optional<DeferredHolder>`。通过检查后才反射加载 `FtbQuestAdapter`，公共定义和客户端卡片只引用自己的 DTO。FTB 任务 API、通知事件及播报数据源仍集中在 `compat/ftb`。适配器安装失败会报告初始化错误，不把未成功安装的任务功能标为可用。

模块 ID、动作 / 卡片 Codec 和存储 Codec 的登记不依赖 `available()`，持久化数据也不在停用时删除。数据包中的模块引用、保存的手动覆盖与任务卡片 / 动作仍可解析，依赖缺席时只跳过实际执行；恢复依赖后沿用原定义和覆盖。扩展新增存储时，同样应始终登记不依赖可选 API 的 Codec，而将实际依赖访问留在可用性检查后的运行时。

`SystemDefinition.modules` 现在提供模块 ID 和可选 Saturn condition；完整格式、内置 ID 与 action/condition 见[数据包与扩展配置](datapacks.md#系统定义)。内置工作流注册时记录所属 ServerModule，功能包含多个工作流时统一受同一开关约束。`ServerModule.required()` 默认 false，核心、绑定与用量模块声明为基础设施。

`/system grant <系统ID> [玩家]` 从数据包注册表选择系统并持久保存，省略玩家时使用 `/system grant <系统ID>`。ModuleAccess 根据该玩家的绑定 ID 读取 SystemDefinition，不再全服固定使用默认定义；`has_system` 可精确匹配此 ID。当前只允许一个绑定，SystemBindings 的集合结构为后续多系统预留；同 ID 重复授予不会再次初始化，其他 ID 必须先 revoke。

Java 扩展可用 `runtime.moduleManager().add/remove(player, id)` 在主线程持久调整玩家可用功能；模块本身须预先注册到 `SystemRegistries.SERVER_MODULE`，不能手动启用未注册的模块。新增模块完成存储初始化后才发布可用权限，移除立即关闭会话；初始化失败不宣称可用。FTB 通知与任务口播也检查任务模块开关。

每个 ServerModule 通过 storages() 声明自己的 Saturn DataStorage，Codec 通过 Saturn 注册事件独立注册；`ModuleData` 从正式服务端模块注册表查找存储声明。`ModuleData.get(data, STORAGE)` 负责读取和创建；底层 Holder 键固定为所属模块 ID，Codec 类型 ID 独立。商城和抽奖复用 CommerceDataStorage 类型但分别存到 system:shop、system:lottery，不会串用交易记录。

内置存储实现统一位于 `module.builtin.storage`：SignInDataStorage、InboxDataStorage、AdvancementDataStorage、CommerceDataStorage、ModuleAccessDataStorage 和 AgentJournalDataStorage，仍由所属模块的静态 ModuleStorage 字段声明并通过 ModuleData 访问。权限和 Agent 幂等日志由必需的 Core 模块声明，不随可选工作流停用清除。玩家文件仍为 `<存档>/system-agent/<UUID>.json`，不使用玩家附件，不提供未发布格式的旧档迁移。

`engine.registerModule(ownerId, workflow)` 可为直接注册的工作流指定所属功能。仅使用原来无 owner 的 `engine.registerModule(workflow)` 注册的独立工作流不受 ServerModule 的开关约束；需要纳入数据包管理的扩展应提供 ServerModule 并在 workflows 中返回工作流。Tool 的单行为契约、工作流预算、交易确认与玩家隔离保持原入口。网络协议保持 `1`，两端一起更新。

## 联网搜索模块

`system:web_search` 在两张模块表均登记 SearchSystemModule，服务端贡献工作流和独立 Search/Fetch 工具，客户端通过其 ClientHooks 贡献“模块 → 联网搜索”页面、标签和搜索提交按钮。仅开发源集的默认系统启用它；发布 JAR 不提供默认系统及默认模块 tag，由服务端数据包选择模块。游戏内 `/system search` 沿用通用指令转发与注册表补全，不新增中央业务 switch。

明确指令、主动按钮或路由模型识别到当前查资料请求后触发该工作流；“帮我查一下……”无需包含“联网”，普通提问不作为搜索授权。禁用模块后入口隐藏、请求被拒绝，移除和重加无需改变独立用量账本。执行中关闭外部 AI、取消、撤销或失去模块权限会阻止后续请求，已经发出的调用仍计量。独立 ReAct 策略和按次费用见[搜索模块](search-api.md)。
