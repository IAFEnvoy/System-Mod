# 模块合并

当前分支已接入通用合并基础设施，并将固定商城作为第一个使用者。多个独立定义可以共用一项侧边栏入口、一套工作流和一张商品列表卡片。Evaluate 无限出售与收购已接入，分别展示在商城与收购页面。

## 公共契约

模块通过接口声明能力，不使用 mergeable 布尔字段，也不新增数据包注册表：

```java
public interface MergeableModule<C> extends ServerModule {
    MergeType<C> mergeType();
    C contribution(ModuleContext context);
}
```

| 契约 | 责任 |
| --- | --- |
| `MergeableModule<C>` | 声明合并类型，为本定义创建类型化贡献对象 |
| `MergeType<C>` | 规范的 Java 类型键，包含 Identifier、贡献对象 Class 和组运行时工厂 |
| `MergeContext<C>` | 共用服务、真实成员 Holder，以及按玩家当前权限筛选成员的方法 |
| `MergeContext.Member<C>` | 真实 `Holder<SystemModule>` 与贡献对象，不重复存储定义 ID |
| `MergeRuntime extends PlayerModule` | 组公共工具、工作流、共享存储与生命周期 |

MergeableModule 可与 SharedModule、WorkflowModule、ToolModule 等能力组合；原有“工具模块不能同时提供工作流”的规则仍保留。成员仍可提供独有工作流、存储和运行时，公共动作只由组运行时提供一次。

MergeType 按对象身份比较。同组实现必须返回同一个公开常量；不同对象即使 ID 相同也会在装配时报错，不能以字符串相同绕过泛型校验：

```java
public static final MergeType<MyContribution> TYPE = new MergeType<>(
        Identifier.fromNamespaceAndPath("example", "catalog"),
        MyContribution.class,
        MyMergeRuntime::new);
```

工厂接收 MergeContext<MyContribution>，返回 MergeRuntime。组运行时的 `storages()`、`tools()`、`workflows()` 默认均为空。tools 是主动导出的组工具；工作流自己的 tools 仍按工作流声明登记。工厂只组装服务和运行时，工具依赖在随后调用 workflows 时解析，此时普通模块与组导出的工具均已安装。

每个服务端实例、每种 MergeType 创建一个组，不生成虚假的 SystemModule、Codec 或注册表条目，不按 MinecraftServer 建全局 Map。

## 上下文与权限

MergeContext 提供：

```java
ModuleContext services();
ModuleScope.Group scope();
List<Member<C>> enabled(UUID player);
Member<C> require(UUID player, Identifier definition);
```

services 提供 AgentEngine、模块注册表和 ContextType 服务，每组有独立工具缓存。它不代表任何成员，调用其 id 会失败。enabled 每次读取现有权限快照，只返回玩家当前已启用且已就绪的成员，不再缓存一份玩家成员列表。require 按真实定义 ID 复核来源，已禁用时抛出能力不可用错误。

创建 contribution 时传入真实定义的 ModuleContext，可在这里捕获配置、服务和来源，不依赖“第一个成员”。成员条件、管理员授予 / 移除仍使用真实定义 ID；组作用域不是可授予的合成模块。

## 装配与生命周期

ModuleManager 先收集所有可用定义的贡献，校验规范类型键、贡献 Class 和 client 一致性，再创建组运行时。纯工具与组导出工具先登记，普通工作流和组公共工作流随后登记。

1. 玩家首次启用成员时，先创建组 Session，再创建成员 Session。
2. 同一批次先初始化全部声明的存储，再调用 Session.initialize，组在其成员之前初始化。同一玩家后续批次的存储工作串行衔接。
3. 成员与依赖组均完成初始化才发布真实成员 ID；整批发布之后调用 ready，组钩子先于其成员。
4. 成员保持独立 Session。加入第二个成员不会重建组；最后一个成员离开才关闭组。
5. 组初始化、ready 或玩家 tick 失败时撤销依赖成员。移除后重加拥有新的激活对象，旧异步回调不能恢复它。
6. 共享存储只声明一次，存储 tick 按 owner 去重；服务端维护与关闭按运行时对象身份去重。

关闭 Session 不删除已支付订单、奖励权益或存储。服务端停止时反序关闭运行时，初始化中断也沿用清理路径。

系统空间的待处理交易查询改由 CommerceTransactions 读取已安装的交易账本，覆盖组共享账本；不再只扫描普通模块定义的 storages，避免共享后漏掉异常订单提示。

## Agent 作用域与预算

`agent.module.ModuleScope` 区分三类授权：

| 作用域 | 可用条件 |
| --- | --- |
| Global | 原有无所属模块的工作流与工具 |
| Definition | 对应真实模块定义已就绪 |
| Group | 声明成员中至少一个真实定义已就绪 |

ModuleRegistry.registerGroup 只登记一次组入口，不走“第一个可用配置变体”路径；与普通工作流抢占同一动作 ID 会报错。路由、指令和模型只看到一套公共动作。

工具分别解析当前作用域、显式导出的可用处理器和真正的全局工具，不把其他成员私有工具当作全局兜底。可信工作流或其分派工具可调用：

```java
context.memberTool(realDefinitionId, TOOL_TYPE, "operation", input);
```

只允许当前组的真实成员、工作流已声明的 Type，以及该成员确实登记的处理器；可选工具不能用于写操作。执行临时进入真实成员作用域，完成后恢复组作用域，不回退到其他成员。组分派工具可显式声明并委派同一 Type，不能直接取全部成员工具的权限并集。

模型、工具、写步骤、期限和回执仍属于原 ExecutionContext。成员调用只给步骤键增加来源前缀，不重置预算；工具前后检查所属作用域，业务付款还需在修改游戏状态前复核来源。

事件任务保存结构化作用域与原累计预算，兼容旧版字符串 owner；缺省 owner 保持 Global。恢复时要求作用域与当前入口一致。

## 客户端与卡片

沿用 SystemModule.client 与 ClientModuleBindingsCache，同一个 client ID 只创建一个侧边栏入口。组内非空 client ID 必须一致，缺省 client 的纯服务端成员也可参与。校验只检查 Identifier，不加载客户端实现类。

页面 order / icon 沿用现有规则。纯服务端成员如需独立提供完整页面，应在自身定义中填写相同 client ID。

通用层不拼接卡片 JSON。组工作流先合并类型化业务数据、排序 / 去重，再构造一份结果 DTO，由既有 CardData 和模板渲染。分页与结果上限针对聚合结果生效。

## 已接入：固定商城

ShopSystemModule 实现 MergeableModule<ShopContribution>，返回 `ShopMergeRuntime.TYPE`；只提供本定义的商品选择，公共浏览、报价、购买工作流和 system:shop 账本移至组运行时。CommerceCatalogs 对商城只读取组聚合结果，没有第二条固定商品兜底路径。

`ShopContribution.offers(player, catalog)` 返回商品流。回调可能在服务端或工作线程执行，应只读取不可变 / 已缓存快照并及时返回；世界读取、Evaluate 运算、I/O 应在成员运行时调度，不能在回调中等待游戏线程或读写文件。

商品视图 `data.catalog.ShopOffer` 提供 id、value、revision。Fixed 保留原生 Holder<ShopOfferEntry>，不复制注册表 ID 和值；Sourced 为非固定商品补上真实定义 Holder，将来源身份纳入版本。动态来源可实现 ShopOffer，无需把生成商品放入数据包注册表；替换内容或价格时必须更新版本。

聚合只读取当前启用成员，每个来源读取后再检查权限。同一固定 Holder 由多份定义暴露时只列一次，共用原商品 ID 与限购计数；其他 ID 冲突直接报错，不按名称或物品类型覆盖。每个来源最多读取 4096 项，合并后最多 4096 个不同商品，超出明确失败；卡片沿用最多 128 项的查询上限与既有网格分页。来源抛错使本次聚合失败，不静默删项。

列表版本包含当前可见的常规商品 ID 与来源版本；搜索生成的子集不参与列表版本，逐项报价另绑定对应商品版本。报价冻结商品奖励和费用，单独保存所选商品版本；付款前通过同一聚合入口复核可用性、版本和条件。无关库存刷新不改变所选商品版本；成员权限变化仍沿用原有报价清理策略。支付防重、限购、未知订单与已入账权益逻辑保留。

两份可直接使用的固定商城定义：

`data/example/system/system_module/fixed_shop.json`：

```json
{
  "order": 60,
  "server": { "type": "system:shop", "offers": "#example:fixed" },
  "client": "system:shop"
}
```

`data/example/system/system_module/extra_shop.json`：

```json
{
  "order": 60,
  "server": { "type": "system:shop", "offers": "#example:extra" },
  "client": "system:shop"
}
```

分别定义对应的 shop_offer tag，并在系统定义中启用这两个真实模块。玩家看到一个商城入口与合并列表，可以单独授予 / 移除任一模块。offers 仍支持 ID、tag 或混合数组，省略允许全表，空数组为空。

## 后续与验证边界

固定收购复用商城的合并运行时，但客户端使用独立的收购页面。`ShopOfferEntry.trade` 区分 `purchase` / `buyback`，收购要求通过 `saturn:item` 费用描述，报酬仍为原有奖励。它可由单独的模块文件和 tag 提供；聚合目录内部仍去重，购买页和收购页分别筛选各自方向。报价和持久订单保存交易方向，详见[固定收购](shop-buyback.md)。

`system:value_shop` 与 `system:value_buyback` 通过同一合并组接入，但不从 `offers()` 返回整张库存。新增的 `ShopContribution.requested(player, fixed, request, limits)` 只在服务端游戏线程执行，接收已按权限及可见性筛选的常规库存、工作线程读取的固定额度及有界搜索／ID 解析请求。`ShopRequest.candidates` 可携带已验证的客户端 ItemStack 批次，来源保留组件但自行复核估值和准入；目标 ID 只解析服务端缓存的玩家选择器。空查询返回零个生成条目；搜索每个来源及聚合最多生成 129 项，最终卡片最多 128 项。来源需标记搜索生成条目并保持 ID 与版本稳定；报价和付款通过同一个方法按 ID 重新生成及校验，不能只信任缓存结果。详见[无限交易](value-commerce.md)。

任务、在线作业等尚未迁入合并组，可声明自己的贡献接口和 MergeType。公共层不依赖商城；来源操作继续复核真实成员。通用层尚未提供跨来源游标或部分失败协议，各组按实际需求实现。

本轮执行 compileJava testModClasses 编译及静态检查，未运行测试或启动 Minecraft。双来源启停、重登、条件变化、失败生命周期、实际卡片与交易行为仍需游戏内验收。
