# 功能模块化

公共接口与运行时位于 `com.iafenvoy.system.module`，客户端缓存位于 `module.client`。内置服务端定义和客户端实现分别为 `module.builtin.*SystemModule`、`*SystemClientModule`，存储类集中在 `module.builtin.storage`。Agent 工作流和单行为工具仍位于独立的 `com.iafenvoy.system.agent.builtin`。

## 两层模块

模块实现类型与模块配置实例分开：

| 入口 | 内容 |
| --- | --- |
| `SystemRegistries.SERVER_MODULE` / `system:server_module` | 静态 `MapCodec<? extends ServerModule>` 类型注册表，由 `SystemServerModules` 注册 |
| `SystemResourceKeys.SYSTEM_MODULE` / `system:system_module` | 原生同步数据包注册表，条目是 `data.SystemModule`，按 `server.type` 解码服务端配置 |
| `SystemClientModules` | 仅客户端初始化的普通实现 Map；数据包的 `client` 只保存该表的 Identifier |
| `SystemResourceKeys.SYSTEM` / `system:system` | 系统名称、介绍与模块 ID/tag/condition；引用上述动态模块实例 |

例如 `data/example/system/system_module/daily.json`：

```json
{
  "server": { "type": "system:signin" },
  "client": "system:signin"
}
```

此配置实例 ID 为 `example:daily`，服务端实现类型为 `system:signin`。系统的 `modules` 写 `"example:daily"`；`/system module add/remove/list`、玩家手动覆盖与 `has_module` 也使用配置实例 ID。模块 tag 路径为 `data/<namespace>/tags/system/system_module/<path>.json`。

`server` 和 `client` 均可省略：纯服务端模块只写 server，纯客户端贡献只写 client。client 始终是 ID，不在服务端解码 ClientModule、客户端 Codec 或 UI 类。专用服务端只加载服务端模块类；原有 ClientHooks 已拆成相邻的客户端实现类。

| 接口 | 职责 |
| --- | --- |
| `ServerModule` | 提供 `codec()`、依赖可用性、存储、运行时工厂和安装钩子 |
| `ToolModule` | 导出 `List<Tool>`，仅供 Agent 工作流内部调用，无玩家指令或意图入口 |
| `WorkflowModule` | 提供 `List<AgentModule<?>>`，包括成就等服务端事件工作流 |
| `SharedModule extends WorkflowModule` | 额外提供 `serverCommands(root)`；不再继承 ClientModule |
| `CoreModule extends SharedModule` | 基础设施；已安装的对应配置对绑定玩家始终启用且不可移除 |
| `ClientModule` | 仅客户端的页面、命令和本地卡片动作 |

类型由接口表达，`available()` 仅检查可选依赖。核心、绑定、用量实现 CoreModule，玩家、物品、签到、收件箱、商城、抽奖和任务实现 SharedModule，成就实现 WorkflowModule，搜索实现 ToolModule。聊天、设置、语音以及基础界面操作由客户端本地目录常驻，不依赖进入世界后的注册表同步。

## 服务端装配与指令

`ServerModule.CODEC` 通过静态类型注册表 `byNameCodec().dispatch("type", ServerModule::codec, codec -> codec)` 分派。12 个内置类型目前没有参数，各自用 `MapCodec.unit(new ...())` 返回单例。动态注册表保存独立的 SystemModule 包装，不将同一单例重复注册为多个 MC 注册表值。

类型 Codec 的扩展示例：

```java
public static final DeferredRegister<MapCodec<? extends ServerModule>> TYPES =
        DeferredRegister.create(SystemResourceKeys.SERVER_MODULE, "example");
public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ExampleModule>> EXAMPLE =
        TYPES.register("example", () -> ExampleModule.CODEC);
```

扩展模组在公共入口绑定 TYPES。没有参数时使用 MapCodec.unit；需要参数时实现不可变 record，并用 RecordCodecBuilder.mapCodec 读取字段，codec() 返回同一个已注册实例。参数位于数据文件的 server 对象内，和 type 平铺；不增加第二套 JSON 手动解析。客户端也会解码同步的 server 数据，因此模块类及 Codec 必须不引用 net.minecraft.client 或可选模组实现类。

ModuleManager 从当前服务器 RegistryAccess 读取动态配置，筛选 available() 后先安装纯工具，再安装工作流，最后创建各实例的 PlayerModule。每份配置有独立 ModuleContext，提供当前条目 `id()`、完整 `modules()` 注册表、引擎、交易与游戏桥接；上下文工具缓存只在该配置内共享。不要把服务器、玩家或运行时对象保存在全局单例定义里。

不同配置可以提供相同动作 ID 的同类工作流。ModuleRegistry 按配置实例 ID 保存候选，命令/模型/语义目录只声明一次，执行时选择该玩家已启用的候选与对应工具。入口 Codec、工作流类型、版本、交互/事件属性、工具契约和只读权限必须一致；参数可影响执行和预算，不应让同一动作类型声明不同的路由契约。同一玩家同时启用多个候选时按模块实例 ID 排序选择第一个；数据包通常应让同类变体的条件互斥。流程选中后保留 owner，移除该配置会取消其在途和排队操作，不会中途换用另一份参数。

每个服务端实现类型的游戏指令只装配一次。SystemCommand 只创建 `/system` 根节点、收集 SharedModule.serverCommands，再连接 Agent 通用指令。绑定、用量和核心模块分别负责 grant/revoke/reset、usage 与打开界面/ask/routing/module 管理。补全和模块管理从当前世界的 system_module 注册表读取 ID。

### 纯工具模块

工具模块实现 ToolModule，提供 codec() 和 `tools(context)`，每个 Tool 只做一个行为，一组行为通过 List 组合。工作流在装配时用 `context.tool(type)` 获取已导出的工具，并列入自身工具白名单；执行仍统一走 ExecutionContext.tool。

ToolRegistry 保存按模块实例区分的处理器。工作流工具使用当前 owner 的实例；外部导出工具在调用时根据玩家启用的导出模块选择。预算、事件授权、写入回执与工具类型身份检查保留。工具模块不能同时实现 WorkflowModule；未启用的工具不能通过直接发请求绕过权限。

必需工具缺失会禁用工作流；Descriptor.optionalTools 则声明可选只读依赖，缺少对应功能不影响工作流的本地路径。执行时仍检查工具所属模块，不因为声明可选项就自动启用该模块。FTB 模块也可在安装时导出其说明书 Tool，供通用问答复用同一权限与检索处理器。

## 玩家生命周期

ServerModule.runtime(context) 返回每服务器、每模块配置的 PlayerModule：

1. 全部工作流装配后执行 validate。
2. binding(player) 在主线程捕获首次绑定数据，返回在 PlayerData 事务内执行的回调。
3. 模块启用时创建玩家 Session，事务内创建声明的 DataStorage 并调用 initialize。
4. 初始化成功且连接与激活代次仍有效时，发布整批可用 ID，再调用 ready。
5. 只给已就绪玩家 Session 分发 tick；PlayerModule.tick 是该配置的服务器维护入口。
6. 条件失效、移除、退出、撤销或失败时关闭 Session；关闭服务器时反序关闭运行时。

纯客户端数据条目也参与权限和状态同步，但没有服务端工作流、存储或业务运行时。

ServerModule.storages() 返回 ModuleStorage 列表，包含 owner、存储类型 ID、Java 类型、MapCodec 和工厂。Codec 另行注册到 Saturn DATA_STORAGE_TYPE；业务在事务中调用 ModuleData.get(data, STORAGE)。存储仍按 ModuleStorage.owner 隔离：内置同类配置共用该功能的账本，不会因配置条目改名获得第二份每日签到或重复订单。扩展需要分开账本时应声明不同 owner。

管理器按声明的 storage.owner tick，并对相同 owner 去重，不能使用动态模块 ID 代替存储 owner。自动检测重写的存储 tick，状态改变时 markDirty；copy 深复制可变成员。逐 tick 操作用内存事务，不阻塞等待文件锁；脏数据每秒合并异步保存。revoke/reset 删除玩家业务文件，独立用量统计保留。

## 客户端贡献

客户端实现通过仅客户端入口注册：

```java
SystemClientModules.register(Identifier.parse("example:daily"), new ExampleClientModule());
```

数据包填 `"client": "example:daily"`，无需给公共 MC 注册表增加 ClientModule 类型。内置客户端实现集中在 registry.SystemClientModules 公开静态字段；专用服务端不会加载该类或 module.builtin.*ClientModule。未知 client ID 只缺少对应界面，不参与服务端业务分派。

ClientModuleCaches.BINDINGS 按同步的动态条目对 client ID 分组，每种 UI 装配一次。ClientModuleContext 保存关联的配置 ID 列表，任一 ID 在当前玩家同步状态中启用即允许操作。Workspace、文本命令、补全和本地动作复用该权限判断。常驻本地目录使用空关联列表，保持进入世界前的设置、关闭通知及基础界面操作可用。

绑定和命令数据均直接保存在各自 Cache 类。ClientModuleBindingsCache 监听客户端标签同步（含单人连接）重建、客户端退出清空，不再使用 Saturn RegistryCache 的共享静态数据过滤；服务端加载事件不构建客户端贡献。首次读取优先使用当前连接的注册表，无连接时才装配本地功能，并保留配置阶段已收到的注册表；连接注册表变化后按需重建。客户端游戏命令从 ClientCommandCache 复制，异步补全按 revision 丢弃旧结果。资源模板继续由自己的资源重载监听器维护。

完整侧栏按 CHAT / MODULES / SETTINGS 分组。模块声明 workspace、文案、按钮与回调，界面绑定与显示只遍历缓存，不硬编码功能 switch。本地卡片动作从绑定缓存分派，服务端动作仍发送结构化请求。

## 数据包启用与条件

SystemDefinition.modules 的 Either 改为 `Holder<SystemModule> | TagKey<SystemModule>`，读取动态 system_module 注册表。引用格式、condition、tag 合并与手动增删规则不变；详见[数据包](datapacks.md#系统模块定义)。未知直接 ID 会报数据包错误，可选 tag 成员可用 required:false。

可选 FTB 定义的 Codec 保持公共安全，依赖缺席时 available() 为 false，跳过工作流、运行时与存储钩子。FTB 通知检查已启用模块的实现 Codec，因此任务模块改名或自定义条目 ID 不会丢失通知。FTB 适配器仍只在依赖存在时反射加载。搜索仅在测试源集提供模块配置，不再向默认 tag 追加；管理员需明确配置或手动添加。

system 和 system_module 都是原生数据包注册表，在世界加载时读取、登录时同步，不是 retained reload listener；新增或修改参数需正常重启服务器/重新进入世界。tag 的资源重载仍使用原版规则。没有旧模块注册表/tag 路径兼容层。

## 联网搜索模块

服务端类型为 system:web_search，仅导出 Search/Fetch 两个单行为 Tool；客户端实现 SearchSystemClientModule 可独立配对搜索页。开发动态条目保留 server.type / client，但不自动启用。核心 `system:question` 统一处理问题：先查私有 / 共享知识及可读 FTB 说明，命中则按资料回答；仅本地为空且当前请求允许联网时才调用已启用的搜索工具。搜索页提交同样先查库，普通提问不授权网络回退。详见 [问答与联网](search-api.md)。
