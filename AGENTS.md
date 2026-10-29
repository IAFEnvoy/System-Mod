# AGENTS.md

本文件约束 AI 编码代理在本仓库中的工作方式。人类贡献者也可参考。

## 测试：默认不执行

**用户没有明确要求时，默认不要运行测试。**

以下操作默认都**不做**，除非用户当次明确要求：

- `gradlew runServer`、`runClient`、`runTestClient`、`runData`、`runGameTestServer` 等启动游戏的命令
- 启动任何 Minecraft 客户端或服务端进程
- 临时插入探针、测试命令、调试事件监听器来验证运行时行为
- 为了验证而新建存档、临时数据包或临时玩家配置

原因：启动游戏代价高（构建、解压、加载数百个模组，常需 2~3 分钟），会产生大量日志，并且**容易留下难以清理的残留**——世界目录的 `session.lock`、被占用的 25565 端口、以及强制结束后仍驻留的 Java 孤儿进程，都会让后续运行莫名失败。

用户说「看一下」「改一下」「重构」时，是指阅读和修改代码，不是指运行验证。

### 允许默认执行的

- `gradlew compileJava`、`gradlew build`、`gradlew testModClasses`：编译与打包校验，不启动游戏
- 静态检查、格式化、依赖解析（`gradlew dependencies`）
- 直接读取 JAR / 反编译源码来确认第三方 API 签名（见下）

### 用户要求测试时

按用户指定的范围执行，并在回复中说明实际执行到哪一步。区分清楚三种结论，不要混为一谈：

1. 编译通过
2. 进程成功启动
3. 功能实际验收通过

启动成功**不等于**功能验证通过。

### 若确实启动了游戏进程

必须收尾干净：

- 正常停止进程，避免使用强制结束
- 停止后确认没有残留的 Java 进程占用端口或存档锁
- 删除遗留的 `run*/world/session.lock`
- 报告日志路径与关键结论

## 依赖与第三方 API

- **不要凭记忆猜测第三方 API 的签名或可空性。** 本仓库的依赖 JAR 在 Gradle 缓存中，且 ModDev 会把反编译源码放在 `build/ftbsrc`、`build/auisrc` 等目录，直接读取即可确认。
- 例如 FTB Quests 的 `ServerQuestFile.getInstance()` 是 `Objects.requireNonNull`，**不可空判断无效，会直接抛 NPE**；FTB 自身代码统一用 `ServerQuestFile.exists()` / `ifExists()` 防御。
- 可选依赖必须做到「未安装时不注册、不加载相关类」：类引用会触发 `NoClassDefFoundError`，需要隔离或用反射。
- 改依赖后同步更新 `gradle.properties`、`build.gradle`、`docs/development.md` 的版本表。

## 项目约定

- 文档在 `docs/`，中英文语言文件键数必须一致（当前各 493 个，`assets/system/lang/en_us.json` 与 `zh_cn.json`），键集合必须完全相同。
- 新增功能类型需同时改：`SystemFunction` 实现、注册表注册、服务端快照、HTML 页面、`SystemClient` 填充、双语语言键、`docs/`。
- 数据包注册表路径为 `data/<条目命名空间>/system/system/<文件名>.json`，三个 `system` 分别代表条目命名空间、注册表命名空间、注册表路径。
- 不要把 `system:ftb_quests` 等可选功能写进必须能加载的内置数据包，否则未安装对应模组时整个数据包注册表会解析失败，服务器拒绝启动。引用可选功能的定义放在测试源集（`src/test-mod/resources/data/system_test/system/system/ftb_quests.json`），它只进入开发运行、不进发布 JAR。
- 只在开发/测试需要的数据、定义与探针一律放 `src/test-mod/`（test 源集），不要放进 `src/main/`；`src/main/resources` 的数据包必须只引用必装功能类型。

## 回复要求

- 报告结论时说明依据：哪些是阅读代码得出的，哪些是实际执行验证的。
- 保留原有代码风格与注释语言（本仓库源码注释为英文）。
