# FTB Quests API 调研报告

面向「任务模块」改造的可行性调研：查看玩家/队伍任务完成情况、读取指定任务的内容与奖励、领取奖励、以及任务完成的钩子。

**结论：四类能力 FTB Quests 全部提供，且都有稳定的公开 API，无需 Mixin 或访问内部字段。**

版本基线（与 [gradle.properties](../gradle.properties) 一致）：

| 组件 | 版本 | 报告依据 |
| --- | --- | --- |
| FTB Quests | 26.1.2.8 | `ftb-quests-neoforge-26.1.2.8.jar` + 反编译源码 `build/ftbsrc` |
| FTB Library | 26.1.2.9 | `ftb-library-neoforge-26.1.2.9.jar`（`javap` 反汇编） |
| FTB Teams | 26.1.2.4 | `ftb-teams-neoforge-26.1.2.4.jar`（`javap`） |
| 上游 API 版本常量 | `FTBQ_VERSION` | 见下文「版本风险」 |

本报告只依据上述 JAR 与反编译源码的核对结果，未运行游戏；所有签名均由 `javap` 或源码逐条确认。文中标注「源码」的条目有反编译源码佐证，标注「javap」的条目仅由字节码签名确认。

---

## 0. 速查表

| 需求 | 入口 | 返回 |
| --- | --- | --- |
| 取玩家的队伍进度对象 | `BaseQuestFile.getTeamData(Player)` | `Optional<TeamData>` |
| 任务是否完成 | `TeamData.isCompleted(QuestObject)` | `boolean` |
| 任务进度百分比 | `TeamData.getRelativeProgress(QuestObject)` | `int` 0..100 |
| 任务是否已开始 | `TeamData.isStarted(QuestObject)` | `boolean` |
| 完成时间 / 开始时间 | `TeamData.getCompletedTime(long)` / `getStartedTime(long)` | `Optional<Date>` |
| 完成次数（可重复任务） | `TeamData.getCompletionCount(Quest)` | `int` |
| 依赖是否满足 / 可否开始 | `areDependenciesComplete` / `canStartTasks(Quest)` | `boolean` |
| 任务内容（标题/描述/子标题） | `Quest.getTitle()` / `getDescription()` / `getSubtitle()` | `Component` / `List<Component>` |
| 任务的子任务 | `Quest.getTasks()` | `Collection<Task>` |
| 任务的奖励 | `Quest.getRewards()` | `Collection<Reward>` |
| 奖励状态 | `TeamData.getClaimType(UUID, Reward)` | `RewardClaimType` |
| 是否有未领奖励 | `TeamData.hasUnclaimedRewards(UUID, QuestObject)` | `boolean` |
| 领取奖励 | `TeamData.claimReward(ServerPlayer, Reward, boolean notify)` | `void`（内部返回布尔被丢弃） |
| 按 ID 查任务/奖励 | `BaseQuestFile.getQuest(long)` / `getReward(long)` / `getTask(long)` | 对象或 `null` |
| 任务完成钩子 | `FTBQuestsEvent.QuestProgress`（NeoForge 事件总线） | 事件 |

---

## 1. 数据模型

```
BaseQuestFile                     整个任务文件（服务端为 ServerQuestFile）
├── Chapter / ChapterGroup        章节与分组
├── QuestObjectBase               一切可寻址对象的基类（有 long id + Component getTitle()）
│   ├── Quest                     任务
│   │   ├── Task                  子任务（有 getMaxProgress()）
│   │   └── Reward                奖励（有 claim(ServerPlayer, boolean)）
│   └── ...
└── TeamData                      某个 FTB 队伍的进度（一个队伍一份）

TeamData.getFile() -> BaseQuestFile        反向回到文件
TeamData.getTeamId() -> UUID               FTB Teams 的队伍 UUID
```

要点：**`TeamData` 是队伍级的**，不是玩家级。FTB Quests 的进度按 FTB Teams 的队伍存储；同一队伍的成员共享 `isCompleted` / `getRelativeProgress`。只有**奖励领取**是按玩家记录的（`claimedRewards` 的 key 为 `(playerUUID, reward)`）。这与本仓库当前「每个玩家绑定系统」的模型不完全对齐，是本模块设计时最需要先定的事（见 §7）。

任务与奖励的寻址都用 `long id`，通过 `getQuest(long)` / `getReward(long)` / `getTask(long)` 查找，找不到返回 `null`（源码 `ClaimRewardMessage.handle` 即按 `reward != null` 判空）。

---

## 2. 查看玩家/队伍的任务完成情况

`TeamData` 公开方法（源码 `build/ftbsrc/dev/ftb/mods/ftbquests/quest/TeamData.java`）：

```java
// 取队伍的进度对象
Optional<TeamData> BaseQuestFile.getTeamData(Player player)          // 无该玩家数据时为 empty

// 进度与完成状态
int     getRelativeProgress(QuestObject object)   // 完成=100，未开始=0，否则按子项算；已 clamp
boolean isStarted(QuestObject object)
boolean isCompleted(QuestObject object)
int     getCompletionCount(Quest quest)           // 可重复任务为次数，否则 0/1
Optional<Date> getStartedTime(long questId)
Optional<Date> getCompletedTime(long questId)

// 依赖与可开始性
boolean areDependenciesComplete(Quest quest)
boolean areDependenciesVisible(Quest quest)
boolean canStartTasks(Quest quest)                // 另有 canStartTasks(Quest, boolean flex)

// 队伍信息
UUID getTeamId()
String getName()
Collection<ServerPlayer> getOnlineMembers()
boolean isValid()
boolean isLocked()
```

`getRelativeProgress` 的语义（源码 TeamData:470-478）：已完成的**任务**直接返回 100；未开始的返回 0；否则交给 `QuestObject.getRelativeProgressFromChildren(this)`，`Quest` 的实现（源码 Quest:486-497）是子任务进度求和后取平均，且**依赖未完成时强制返回 0**。因此它已经是适合直接显示的 0..100 值，本仓库现有 `Math.max(0, Math.min(100, ...))` 的 clamp 是冗余但无害的。

**可见性筛选**：

```java
boolean Quest.isVisible(TeamData)                       // 源码 Quest:718
List<Chapter> BaseQuestFile.getVisibleChapters(TeamData)
```

可用的遍历入口：

```java
void forAllQuests(Consumer<Quest>)          // 全部任务，无排序、无过滤
void forAllChapters(Consumer<Chapter>)
List<Chapter> getAllChapters()
List<Task>    getAllTasks()
Collection<QuestObjectBase> getAllObjects()
<T extends QuestObjectBase> List<T> collect(Class<T>)                    // javap
<T extends QuestObjectBase> List<T> collect(Class<T>, Predicate<T>)      // javap
```

注意 `collect` 用 `Class` 作参数，在反射场景下需要传 `Class<?>` 对象，可用 `Class.forName` 得到。

也可经 FTB Teams 拿到队伍再关联（`FTBTeamsAPI.api().getManager()`，javap 确认 `isManagerLoaded()` / `getManager()` / `Team.getOnlineMembers()`），但**读任务进度不需要走这条路**，`getTeamData(Player)` 已足够。

---

## 3. 指定任务的内容与奖励

任务内容（源码 `QuestObjectBase` / `Quest`，javap 确认签名）：

```java
Component            getTitle()          // QuestObjectBase，final；可翻译
Component            getAltTitle()       // 可被自定义覆盖的替代标题
Component            getSubtitle()       // Quest
List<Component>      getDescription()    // Quest，多行
Chapter              getQuestChapter()
ProgressionMode      getProgressionMode() // LINEAR / FLEXIBLE
boolean              isVisible(TeamData)
boolean              isCompletedRaw(TeamData)
boolean              canBeRepeated()
int                  getRepeatCooldown()  // 秒
```

子任务（源码 `task/Task.java`，接口 `QuestFile` 不涉及）：

```java
Collection<Task>  Quest.getTasks()
List<Task>        Quest.getTasksAsList()
long              Task.getMaxProgress()
TaskType          Task.getType()
Component         Task.getAltTitle()
MutableComponent  Task.getButtonText()
long              TeamData.getProgress(Task)   // 当前进度，配合 getMaxProgress 可显示 "3/10"
```

奖励（源码 `reward/Reward.java`）：

```java
Collection<Reward> Quest.getRewards()
RewardType    Reward.getType()             // item / xp / xp_levels / loot / choice / random / command / stage / advancement / toast / custom / currency / all_table
Component     Reward.getAltTitle()         // 展示用标题
boolean       Reward.isTeamReward()
boolean       Reward.getExcludeFromClaimAll()
RewardAutoClaim Reward.getAutoClaimType()  // DISABLED / ENABLED / NO_TOAST / INVISIBLE
boolean       Reward.ignoreRewardBlocking()
Quest         Reward.getQuest()
```

**用 `getAltTitle()` 而不是 `getTitle()`**：`QuestObjectBase.getTitle()` 是 final 且会走翻译管理器，而 `getAltTitle()` 正是 gui 用来显示的名称（`ItemReward`、`XPReward` 等都覆写它来带上数量等信息）。两者都返回 `Component`。

**强烈建议改造 `QuestView` 传输 `Component` 而不是 `String`**。当前实现（`SystemService.addQuest`）在服务端调用 `quest.getTitle().getString()`，把组件提前解析成字符串再跨网络发送。若客户端语言与服务端不同（或任务标题本身是可翻译组件），客户端拿到的就是服务端语言的文本，无法重新本地化——这正是 `docs/development.md` 已记录的已知限制。改为随 `QuestView` 发送 `Component`（`Component.Serializer` / `ByteBufCodecs` 支持组件）即可彻底解决。

---

## 4. 领取奖励的 API

### 4.1 领取入口

```java
// TeamData
void claimReward(ServerPlayer player, Reward reward, boolean notify)
void claimReward(ServerPlayer player, Reward reward, boolean notify, long when)
```

源码实现（TeamData:528-536）：

```java
public void claimReward(ServerPlayer player, Reward reward, boolean notify, long when) {
    if (markRewardAsClaimed(player.getUUID(), reward, when)) {
        reward.claim(player, notify);
    }
}
```

注意两点：**返回类型是 `void`**，成功与否只能通过外部状态判断；且 `markRewardAsClaimed` 返回 false 时静默什么都不做。

### 4.2 领取前的三重守卫

`markRewardAsClaimed`（源码 TeamData:281-309）自行检查：

```java
if (locked || isRewardBlocked(reward)) return false;      // 1) 队伍进度锁定 2) 奖励被屏蔽
if (!claimedRewards.containsKey(key)) { ... }             // 3) 幂等：已领过直接 false
```

`isRewardBlocked` 还要求 `reward.ignoreRewardBlocking()` 与 `reward.getQuest().ignoreRewardBlocking()` 都为 false（源码 TeamData:231-235）。

因此**业务侧不需要自己防重复领取**，但需要自己给玩家反馈，因为失败是静默的。

### 4.3 官方推荐的调用范式

`ClaimRewardMessage.handle`（源码 `net/ClaimRewardMessage.java`）是 FTB 自己的服务端处理，可直接照抄其校验链：

```java
ServerQuestFile.ifExists(sqf -> {
    Reward reward = sqf.getReward(message.id);
    if (reward != null && context.player() instanceof ServerPlayer player) {
        sqf.getTeamData(player).ifPresent(teamData -> {
            if (teamData.isCompleted(reward.getQuest())) {   // 关键：任务必须已完成
                teamData.claimReward(player, reward, message.shouldNotify);
            }
        });
    }
});
```

**`teamData.isCompleted(reward.getQuest())` 这一步必须保留**——它是「未完成也能领奖」的唯一拦截点，`claimReward` 内部并不检查完成状态。

### 4.4 领取状态查询

```java
RewardClaimType TeamData.getClaimType(UUID player, Reward reward)
// CAN_CLAIM / CANT_CLAIM / CLAIMED，带 canClaim() / cantClaim() / isClaimed() 便捷方法

boolean TeamData.isRewardClaimed(UUID player, Reward reward)
Optional<Date> TeamData.getRewardClaimTime(UUID player, Reward reward)
boolean TeamData.hasUnclaimedRewards(UUID player, QuestObject object)
boolean TeamData.areRewardsBlocked()
```

`getClaimType` 的判定（源码 TeamData:574-582）就是 `CLAIMED` → `CAN_CLAIM`（任务已完成）→ `CANT_CLAIM`，正好可直接映射成 UI 按钮状态。

开发期强制打进度可用 `Reward.forceProgress(TeamData, ProgressChange)`（javap 确认存在），但属于写操作，不建议在正式逻辑中使用。

---

## 5. 任务完成钩子

### 5.1 公开的 NeoForge 事件（推荐）

FTB Quests **确实提供了** NeoForge 事件总线上的进度事件，位于 `dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent`（javap 确认，且这些类在 `ftb-quests-neoforge` 包内）：

| 事件类 | 承载数据 | 触发时机 |
| --- | --- | --- |
| `FTBQuestsEvent.QuestProgress` | `QuestProgressEvent.Data` | 任务开始 / 完成 |
| `FTBQuestsEvent.TaskProgress` | `TaskProgressEvent.Data` | 子任务开始 / 完成 |
| `FTBQuestsEvent.ChapterProgress` | `ChapterProgressEvent.Data` | 章节开始 / 完成 |
| `FTBQuestsEvent.FileProgress` | `FileProgressEvent.Data` | 整个任务文件完成 |
| `FTBQuestsEvent.CustomTask` | `CustomTaskEvent.Data` | 自定义任务类型创建时（扩展点） |
| `FTBQuestsEvent.CustomReward` | `CustomRewardEvent.Data` | 自定义奖励类型发放时（扩展点） |
| `FTBQuestsEvent.ClearFileCache` | `ClearFileCacheEvent.Data` | 任务文件缓存清空 |

全部继承 `BaseEventWithData<T>`，用 `getEventData()` 取数据（javap 确认）：

```java
public abstract class BaseEventWithData<T> extends net.neoforged.bus.api.Event {
    public T getEventData();
}
```

进度事件的载荷（源码 `api/event/progress/`）：

```java
record QuestProgressEvent.Data(ProgressType type, ProgressEventData<Quest> progressData)
enum ProgressType { STARTED, COMPLETED }

record ProgressEventData<T extends QuestObject>(
        @Nullable Date date,          // 客户端侧可能为 null
        TeamData teamData,            // 队伍进度
        T object,                     // 具体 Quest / Task / Chapter
        Collection<ServerPlayer> onlineMembers,   // 服务端有值，客户端为空集合
        Collection<ServerPlayer> notifiedPlayers) // 已收到提示的玩家
```

订阅方式就是普通 NeoForge 监听器：

```java
@EventBusSubscriber(modid = "...")
public final class QuestHooks {
    @SubscribeEvent
    public static void onQuestProgress(FTBQuestsEvent.QuestProgress event) {
        QuestProgressEvent.Data data = event.getEventData();
        if (data.type() != ProgressType.COMPLETED) return;
        Quest quest = data.progressData().object();
        TeamData team = data.progressData().teamData();
        // ...
    }
}
```

**关键时序**：`QuestProgressEvent` 在 `Quest.onCompleted` 中、`data.setCompleted(id)` **之后**才 post（源码 Quest:510-514）。所以事件触发时 `teamData.isCompleted(quest)` 已经为 true，可以放心读取完成状态。同一处还会先调用 `data.notifyPlayers(id)` 之外的可选 toast 逻辑与 `checkAutoCompletion`，钩子早于自动发奖。

### 5.2 客户端同样会触发

`QuestProgressEvent.Data` 也在客户端 post（源码 `client/FTBQuestsNetClient.java:272-284`），载荷来自网络同步，此时 `onlineMembers` / `notifiedPlayers` 为空集合、`date` 可能为 null。**若钩子要执行写操作（发奖、写数据），必须判断服务端**（`teamData.getFile().isServerSide()` 或检查 `player.level().isClientSide`）。

### 5.3 一个必须避开的陷阱

进度事件在内部经由 `dev.ftb.mods.ftblibrary.platform.event.NativeEventPosting` post；NeoForge 侧的桥接由 FTB Quests 自己在 `FTBQuestsNeoForge.registerNeoEventPosters()` 注册（javap 反汇编确认，注册了上表前 8 个类型）。

`NativeEventPosting.postEvent` 在没有注册消费者时**抛 `IllegalArgumentException` 而不是静默丢弃**（字节码确认：`consumers.get(type)` 非 `Consumer` 即 `new IllegalArgumentException(type.getClass().getName())`）。

结论：**要订阅进度事件，就用 `FTBQuestsEvent.*` 挂在 NeoForge 事件总线上**；不要去实现 `QuestProgressEvent` 这个 `Consumer` 接口并期望经由 `NativeEventPosting.registerEvent` 生效——那是 FTB 内部通道，而且弄错会直接抛异常。

---

## 6. 对本仓库「可选依赖」架构的约束（重要）

FTB Quests 在本仓库是可选的（见 [docs/development.md](../docs/development.md#可选依赖ftb-quests)），现有实现集中在 [FtbQuestsIntegration](../src/main/java/com/iafenvoy/system/integration/FtbQuestsIntegration.java)，全部通过反射调用。任务模块改造**必须沿用同一模式**，并注意以下由本仓库已踩过的坑：

### 6.1 不能用 `Class.getMethod`

`Quest implements Movable`，而 `Movable` 声明了以客户端专用类型 `GuiGraphicsExtractor` 为参数的方法（已确认 `Movable.class` 与 `ChapterImage.class` 引用了它）。`Class.getMethod` 会**递归解析全部继承方法**，在专用服务端上会为一个我们从不调用的方法抛 `NoClassDefFoundError`。

必须用逐级 `getDeclaredMethod` 向上查找（现有 `FtbQuestsIntegration.resolve` 即为此实现）。

### 6.2 事件监听器的类加载风险

事件类 `FTBQuestsEvent.*` 一旦被写进方法签名，未安装 FTB 时加载该类就会 `NoClassDefFoundError`。可选方案：

1. **反射注册**：`IEventBus.addListener(Consumer<T>)` 的类型参数是泛型擦除的，可以用 `Class.forName` 得到事件类、用 `MethodHandles`/动态代理构造一个 `Consumer`，从而使事件类只在 FTB 存在时才被解析。可行但较绕。
2. **独立类 + 显式门禁**（更清晰）：把监听器放在单独类里，仅在 `FtbQuestsIntegration.isAvailable()` 为真时通过 `Class.forName(...)` 触发其加载。参考 FTB 自己的 `ServerQuestFile.ifExists()` 用法。

无论哪种，都要保证未安装 FTB 时**没有任何路径会解析 FTB 类型**。`AGENTS.md` 已把这条列为仓库约定。

### 6.3 `getDeclaredMethod` 走不到接口方法

逐级向上查找只沿**父类**链（`getSuperclass()`），不包含接口。若某方法只声明在接口上（例如 `Movable` 的成员），`getDeclaredMethod` 找不到，会抛 `NoSuchMethodException`。任务模块要用的方法都已确认落在类上（`isVisible` 在 `Quest`、`getId`/`getTitle` 在 `QuestObjectBase`、`getRelativeProgress`/`isCompleted` 在 `TeamData`），但新增调用点时需逐个核对，必要时再走接口。

### 6.4 `ServerQuestFile.getInstance()` 会抛 NPE

它是 `Objects.requireNonNull`，任务文件未就绪时抛异常而非返回 `null`。必须先 `ServerQuestFile.exists()`。现有代码已处理。

---

## 7. 改造建议与待决策项

### 7.1 建议的改造方向

1. **`QuestView` 改为携带 `Component`**（去掉服务端 `getString()`），顺带修掉已知的本地化缺陷（§3）。
2. **补上奖励数据**：`QuestView` 增加奖励条目（`rewardId`、`Component` 标题、`RewardClaimType`），使任务页显示「可领取 / 已领取 / 不可领取」。
3. **新增领取请求 Payload**，服务端照抄 §4.3 的校验链：存在性 → 是 `ServerPlayer` → 有 `TeamData` → `isCompleted` → `claimReward`；失败要回发反馈，不能静默。
4. **用 `FTBQuestsEvent.QuestProgress` 做完成钩子**，供本仓库自己的系统（例如签到、自定义奖励发放）响应。
5. 读任务列表时优先用 `getVisibleChapters(TeamData)` + 逐章节取任务，而不是 `forAllQuests` 全量扫描，顺便解决现有实现「遍历全部任务且不提前中断」的开销问题。

### 7.2 必须先定的两件事

**（a）队伍与玩家的语义。** FTB Quests 进度是**队伍级**的，而本仓库的 `SystemPlayerData`（绑定系统、签到日期）是**玩家级**的。同一队伍里 A 完成任务，B 也会看到「已完成」，但奖励是各领各的。这直接影响 UI 文案（「已完成」还是「你的队伍已完成」）和签到等既有功能的语义边界，需要先确认是否符合预期。

**（b）领取奖励的权限边界。** `claimReward` 是 FTB 的写操作，会触发真实物品/经验/命令奖励。是否允许本模块代为领取、是否需要额外的权限或日志、以及是否需要与 FTB 自身的奖励 GUI 并存，是需要产品决策的点。另外 `RewardType` 里包含 `command`（执行命令）与 `loot`（战利品表），代领等于间接执行这些副作用。

---

## 8. 版本风险

本仓库依赖 FTB 的 `26.1.2.x` 版本线；`neoforge.mods.toml` 中声明为 `type="optional"`、范围 `[26.1.2.8,26.1.3)`。

- 上述 API 中大量成员标注了 `@Nullable` / `Optional`，且 `TeamData` 的签名在 FTB 各版本间曾多次调整（例如 `getRelativeProgress` 参数类型是 `QuestObject` 而非 `Quest`）。
- 由于本仓库走反射，**FTB 升级后若签名变化不会编译失败，而是在运行时被捕获并降级为空任务列表**（现有 `FtbQuestsIntegration` 会 `LOGGER.error`）。这意味着升级 FTB 后需要专门验证任务模块，不能只看编译是否通过。
- 所有反射调用点都应保持「查不到方法即降级 + 记录 error」的行为，不要抛到调用方。

---

## 附：核对记录

| 结论 | 核对方式 |
| --- | --- |
| `FTBQuestsEvent.*` 是公开 NeoForge 事件且由 FTB 自行注册 | `javap -c FTBQuestsNeoForge` 反汇编 `registerNeoEventPosters()`，逐个 `ldc` 出事件数据类并调用 `NeoEventHelper.registerNeoEventPoster` |
| `BaseEventWithData.getEventData()` | `javap` |
| `NativeEventPosting.postEvent` 无消费者时抛 `IllegalArgumentException` | `javap -c NativeEventPosting` 字节码分支 |
| `getRelativeProgress` / `isCompleted` 在 `TeamData` 且参数为 `QuestObject` | `javap TeamData` + 源码 TeamData:470-486 |
| `Quest.isVisible` / `getId` / `getTitle` 的声明位置 | `javap Quest` / `QuestObjectBase` |
| `Quest implements Movable`，`Movable` 引用 `GuiGraphicsExtractor` | `javap Quest` 类头 + 扫描 JAR 全类常量池 |
| 领取校验链与「必须自行判断 isCompleted」 | 源码 `net/ClaimRewardMessage.java`、`TeamData.claimReward` / `markRewardAsClaimed` |
| `QuestProgressEvent` 在 `setCompleted` 之后触发 | 源码 `Quest.java:509-525` |
| 客户端也会触发进度事件 | 源码 `client/FTBQuestsNetClient.java:272-284` |
