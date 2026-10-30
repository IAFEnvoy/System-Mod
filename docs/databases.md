# 分层本地数据库

当前使用 Codec 编解码的文件型文档数据库，保留本地稀疏 TF-IDF，不接入 JVector、SQLite、外部进程或 embedding API。统一的是知识与扩展数据的存取入口，不改变原有玩家业务事务、模块 DataStorage 和用量账本的职责。

## 作用域与文件

每个存档的 AgentEngine 拥有一个 AgentDatabases，随服务端实例关闭，不能跨存档共享静态单例。

```text
<存档>/system-database/
  shared.json
  players/
    <玩家UUID>.json
```

- **共享数据库**：`engine.databases().shared()`。同一存档所有玩家复用静态知识；FTB 说明书和任务背景分类均保存在这里。
- **玩家独立数据库**：`engine.databases().player(uuid)`。每个 UUID 独立文件、数据表和内存索引，首次实际读写时加载，首次写入时建文件。问答读取当前玩家的私有知识，其他扩展仍可声明独立表；未把现有 PlayerData 或语音密钥搬进来。

两个作用域使用相同的 AgentDatabase API，不会自动合并搜索或回退到其他玩家的数据。数据库入口属于可信服务端扩展接口；未来 Tool 必须从当前执行上下文取得玩家 UUID，不能直接接受模型提供的任意 UUID。共享知识也不意味着任何玩家都能阅读全部来源，权限仍由业务模块检查。

独立数据库在 revoke / reset 时删除，旧句柄同时失效；共享库和累计用量不删除。关闭存档后所有已打开的数据库句柄失效。当前没有玩家独立数据库的用户界面、跨玩家查询或客户端同步入口。

## 类型化数据表

`DatabaseTable<T>` 由命名空间 ID 和 `Codec<T>` 组成，相同 ID 必须保持相同结构。值通过 Codec 转为私有 Dynamic 存储，业务调用方直接读写 Java 对象，不传裸 JsonObject。

```java
public static final DatabaseTable<String> NOTES = new DatabaseTable<>(
        ResourceLocation.fromNamespaceAndPath("example", "notes"), Codec.STRING);

// All database operations run on workers, including the first read.
engine.maintenance(() -> {
    AgentDatabase database = engine.databases().player(playerId);
    database.put(NOTES, "home", "A private note");
    return database.get(NOTES, "home");
});
```

- `get(table, key)`：查询一个值，缺失返回 Optional.empty。
- `entries(table)`：读取该表的类型化快照。
- `put(table, key, value)` / `remove(table, key)`：增改或删除单个值。
- `replace(table, values)`：整体替换指定表，不触碰同库的其他表。
- `AgentDatabase.documents(source)`：声明该来源的文档表，以语言为键、Document 列表为值。
- `prepareIndex(source, language)` / `search(source, language, allowedIds, query, limit)`：构建与复用该库、来源、语言下的内存段落索引。

FTB 的表 ID 为 `system:ftb_guides`，背景判定为 `system:quest_backgrounds`。其他模块使用自己的命名空间表，无需新增存储文件或注册到 FTB 适配器。

## 问答知识表

`DatabaseQueryTool.KNOWLEDGE` 声明 `system:knowledge` 文档表，以玩家语言（如 `zh_cn`）为键，值为 `List<KnowledgeIndex.Document>`。服务端扩展可在后台写入公开或私有资料：

```java
engine.maintenance(() -> {
    engine.databases().shared().put(DatabaseQueryTool.KNOWLEDGE, "zh_cn", publicDocuments);
    engine.databases().player(playerId).put(DatabaseQueryTool.KNOWLEDGE, "zh_cn", privateDocuments);
    return null;
});
```

当前没有自动导入任意数据表、跨语言回退或知识编辑界面。放进共享知识表的资料必须可以公开给同存档玩家；玩家表只在该 UUID 的当前执行上下文中查询，不接受模型传入其他 UUID。

核心 `system:question` 先调用单行为工具 `database.query`，聚合当前玩家和共享知识表各最多 5 条片段；已启用 FTB 功能时再通过它导出的 `ftb.guide` 获取最多 5 条授权说明书。不能直接搜索共享 FTB 原始语料，以免绕过任务可见性。其他业务数据表和背景分类缓存不进入问答检索。

有资料时可交给文本模型回答，关闭外部 AI 或可恢复的整理失败时只展示原文；无结果才考虑本次授权及搜索模块，数据库错误不会当作无结果。模型整理后会重新查询并核对本地来源。详细授权与调用预算见 [数据库优先的问答与联网回退](search-api.md)。

每次成功写入都先替换磁盘文件再发布内存状态；写入同一数据库会串行化，不同玩家数据库独立。临时文件采用同目录 `.tmp`，文件系统支持时使用原子移动。读写失败会报告存储错误，不静默覆盖损坏文件；删除玩家库不需要先解码，故仍可 reset。它不提供跨库事务、SQL 或多进程写入能力。

索引在数据更新时失效，构建和排序在后台执行；稳定索引的排序不持有数据库写锁。磁盘保存原始文档与普通表值，TF-IDF 向量仍为内存派生数据。当前是小规模文件型后端，单次写入会重写该作用域的完整文件；大体量或高频写入需要以后评估新的后端。

## FTB 重新收录

管理员或控制台执行：

```text
/system ftb reload
```

该入口随可用的 FTB 系统模块注册，要求管理员等级 2，不要求执行者绑定系统。它读取 **FTB 当前已经加载的定义**，重新收录符合规则的说明任务、移除已删除或不再匹配的来源并重建向量索引。反馈数量按不同任务 ID 去重。当前缓存语言、在线玩家语言和 FTB 回退语言一起处理；其他语言之后首次查询时生成，不保留旧语言语料作为兜底。

任务定义与本地化只在服务端线程读取，文件写入和索引构建使用 Agent 的有界后台队列。重复执行时提示忙碌；出现新的原生编辑失效事件时，在途旧查询 / 旧重载不得返回旧结果，管理员可以再执行一次。重载只替换说明书数据表，不修改独立库、其他共享表、FTB 完成状态或奖励；背景判定仍按内容和提示词指纹复用，避免无意义地重新请求模型。

如果直接编辑了 FTB 的磁盘文件，应先通过 FTB 自身的重载入口使定义生效，再运行本指令。这里不调用 FTB 的 load / save / submit，不代替原生任务文件重载。FTB ClearFileCacheEvent 也会自动使静态文本快照失效，下次查询重新收录；查询结果返回前仍复核队伍、语言、任务可见性、正文隐藏条件及实际内容。

旧的 `system-quest-knowledge.json`、`system-quest-backgrounds.json` 不再读写，也不自动迁移；说明书从原生 FTB 重新收录，背景判定按需重新生成。旧文件不会自动删除。

当前仅通过编译和静态检查，未启动 Minecraft，尚未实际验收文件持久化、玩家隔离、并发重载和原生编辑事件。26.1.2 同步入口见 [FTB 跨版本记录](ftb-quests-porting.md)。
