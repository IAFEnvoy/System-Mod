# 物品相对价值 API

System 内嵌 Evaluate `1.0-beta.2`（CurseForge 文件 `9626124`），采用 Gradle `implementation jarJar(...)` 同时加入开发类路径和发布 JAR。最低依赖为 NeoForge `26.1.2.126`、Saturn `1.3.1`。离线签到使用其估值计算玩家进度分位数和奖励包总价值，见[每日签到](sign-in.md)；无限出售与收购也已接入 Evaluate，支持可配置汇率、价格倍率、P90 出售上限与固定库存去重，见[无限交易](value-commerce.md)。固定商品及抽奖仍由数据包指定，不自动改价。

以下签名已通过发布 JAR 的 `javap` 核对，行为依据同版本源码及字节码。

## 查询入口

类：`com.iafenvoy.evaluate.api.ItemValues`。

| 方法 | 返回类型 | 用途 |
| --- | --- | --- |
| `get(MinecraftServer, ItemStack)` | `OptionalDouble` | 按实际物品及组件查询单件相对价值，业务查询优先使用此方法 |
| `get(MinecraftServer, Item)` | `OptionalDouble` | 按默认组件创建物品堆后查询 |
| `all(MinecraftServer)` | `Object2DoubleMap<ValueKey.ItemValue>` | 当前已计算的只读价值快照，用于枚举，不替代实际物品堆的匹配查询 |
| `evaluators(MinecraftServer)` | `Registry<Evaluator>` | 当前重载周期的估值器注册表 |
| `overrides(MinecraftServer)` | `Registry<ValueOverride>` | 当前重载周期的最终覆盖注册表 |

```java
OptionalDouble unitValue = ItemValues.get(server, stack);
OptionalDouble totalValue = unitValue.isPresent()
        ? OptionalDouble.of(unitValue.getAsDouble() * stack.getCount())
        : OptionalDouble.empty();
```

返回值是单件价值，不会乘物品数量。空物品、未知价值、被配置为零或无法推导的物品返回空；调用方不能把未知值默认为免费。图键 `ValueKey.ItemValue` 包含物品和 `DataComponentPatch`，药水等不同组件的变体可有不同价值。

估值没有内置系统币汇率。需要用于商店或回收时，应由业务定义换算比例、取整方式和未知值处理规则。

## 数据来源与优先级

查询先读取存档命令覆盖，再读取数据包最终覆盖、显式基础值，最后使用推导结果。配方推导按材料成本和额外成本计算单件价值，多种路径取最低有效成本；未知材料不会当成零，无法收敛的循环组有计算上限及保护。

- `data/<namespace>/evaluate/evaluator/<path>.json`：估值器实例，通过 `Evaluator.CODEC` 分派。内置 `constant`、`recipe`、`ftb_quests`、`js`，后两者需要对应可选模组。
- `data/<namespace>/evaluate/value_override/<path>.json`：最终值覆盖，不会沿配方重新传播；基础值及存档覆盖参与图计算。
- `/evaluate set`、`/evaluate reset`：Evaluate 自带的存档覆盖命令。

基础值与最终覆盖使用 Saturn 物品匹配，实际 `ItemStack` 的组件或条件可影响结果。不要只按物品 ID 缓存查询结果。

## 调用时机与缓存

这是服务端 API，客户端应使用服务端返回的业务结果。接入 System 时通过 `MinecraftTools.onPlayer` 等现有游戏线程入口查询，不能从渲染线程或 Agent 工作线程直接访问存档与游戏对象。

Evaluate 在启动及重载后构建价值快照，外部来源失效时会重新计算，普通查询复用快照；首次失效后的查询可能触发完整重建。`all()` 返回的快照和两个注册表引用都不应跨重载长期缓存。

## Java 扩展

实现 `Evaluator` 的 `codec()` 与 `evaluate(EvaluationContext)`，将 MapCodec 注册到 `evaluate:evaluator_type`，再用数据包定义实例。`EvaluationContext` 只在当前计算中使用，不能长期保存：

| 方法 | 用途 |
| --- | --- |
| `item(ItemStack)` | 登记带组件的物品变体并返回图键 |
| `group(String)` | 创建当前估值器实例专用的候选组键 |
| `setValue(ItemMatcher, double)` | 添加显式固定值匹配器 |
| `addValue(ItemStack, double)` | 添加可被更低推导值替换的候选值 |
| `addConversion(Conversion<ValueKey>)` | 添加输入数量、产出数量和额外成本的换算关系 |

System 的 `assistant.progression.PlayerProgression` 复用此入口读取物品与已解锁配方产物，不另建价值注册表或长期缓存。阶段模组可通过 `PlayerProgressionEvent` 校准玩家进度，但不改变 Evaluate 原始物品价值；其他业务可以直接使用 Evaluate 或该进度评估结果。
