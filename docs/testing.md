# 编译与验收

## 2026-10-27 两端运行时职责拆分

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认：`AssistantServer` 的玩家会话、请求分派和用量计量分别交由 `AssistantPlayers`、`AssistantRequests`、`AssistantUsage`，命令、网络及 FTB 桥接调用已同步；异步回复的连接代次校验和计量的主线程写入保留。

客户端对话、请求和服务端元数据归入不可变 `AssistantClientState`，所有界面改从状态快照读取；登出或成功撤销时替换为 `EMPTY`，刷新版本号独立递增。同步修正设置页把 ASR 地区 `Set` 当作列表使用的问题，地区切换从排序快照读取。`git diff --check` 通过；没有运行测试、启动 Minecraft 或调用外部 API，绑定、登出、异步回复与界面刷新尚未作游戏内验收。

## 2026-10-27 全局配置直接读取

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态扫描确认：引擎、执行上下文、模型客户端、语义路由和服务端桥接不再持有配置对象字段，不再传递全局配置构造参数或通过 `settings()` 转发；使用 `XxxConfig.INSTANCE.get()` 直接读取。路由缓存不再保存容量和 TTL 的配置副本，写入时按当前限制淘汰。

设置页只保留未提交草稿，配置提交仍使用 `INSTANCE.set()` 的脏标记机制。TTS 等待队列不再保存配置；TTS / ASR 单次请求保留不可变报文与计量维度，模型在配置实例锁内一起读取地址、鉴权和请求体，HTTP 在锁外执行。词法索引与模型并发信号量仍在启动时构建，相关结构参数修改后需重启。`git diff --check` 通过；没有运行测试、启动 Minecraft 或调用外部 API，资源重载和在途请求尚未作运行验收。

## 2026-10-26 抽奖界面布局

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认中英文各 406 个语言键且集合一致，`git diff --check` 通过。

阅读样式和渲染代码确认：抽奖与商城搜索框伸展占满剩余宽度，按钮等高，窄窗口换行；奖池卡片按名称、奖品种类、ID 和单次消耗分层，奖品卡片突出名称、物品数量与中奖概率。卡片正文通过类型注册渲染，简约模式使用同一套紧凑文本格式；原有可信动作、独立报价与确认流程保留。没有运行测试或启动 Minecraft，实际排版、输入法、滚动和不同 GUI 缩放尚未作游戏内验收。

## 2026-10-26 通知首次展开提示音

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。阅读通知状态机确认：提示音在 `HIDDEN` → `EXPANDING` 时提交一次，连续消息的淡出淡入和收缩中途反向展开只提交正文播报，完全收起后下一轮才重新播放提示音；恢复通知、文档重建不补播。提示音与正文仍共用 session，音量维持播报音量的 75%，切换、关闭和隐藏时取消。

没有运行测试或启动 Minecraft，实际连续播放、音量和动画时序尚未作游戏内验收。

## 2026-10-26 玩家存档读取句柄

读取现有 `run-test-client/logs/latest.log` 确认登录时出现 baseline 失败，打开助手后显示存储不可用；原实现没有记录底层异常，日志无法直接确认具体 I/O 错误。本次使用的玩家文件只有合法的 `{"bound":true}`，没有旧订单或缓存卡片字段。阅读源码发现 `FileAgentStore.load()` 直接创建 `FileReader` 后未关闭，读取句柄可能在 Windows 下阻止同一事务随后原子替换原文件。

改用 UTF-8 `Files.newBufferedReader` 和 try-with-resources，确保读完或解析失败时都关闭文件；存储 I/O 和目录枚举失败统一记录操作、路径及原因，并在 `AgentException` 中保留 cause，网络只返回原错误码。未修改或清理任何玩家存档，未增加格式兼容或解析失败后的空数据兜底。

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过；`testModClasses` 只编译开发源集。静态检查与 `git diff --check` 通过。没有运行测试或启动 Minecraft，原子替换与反复打开助手尚未作运行验收。

## 2026-10-26 HUD 通知提示音

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过；没有运行测试或启动 Minecraft。使用 FFmpeg 将提供的 `1.mp3` 转为 Ogg Vorbis，FFprobe 确认结果为单声道、44.1 kHz、约 1.78 秒、16,584 字节。静态检查确认中英文各 400 个语言键且集合一致，声音定义与字幕键、OGG 路径可对应，`git diff --check` 通过；发布 JAR 包含 `sounds.json`、OGG、`SystemSounds` 与 `NotificationSound`，大小为 861,806 字节。

阅读本地 Minecraft / NeoForge 依赖源码确认注册事件、原版声音实例和静态/流式启动回调签名。阅读调用链确认：HUD 首次渲染提交当前 session 的提示音，按播报音量的 75% 使用 Voice 分类播放；云端合成并行准备，原版声音实例结束后才启动正文播放。通知关闭、确认、切换、隐藏和声音重载复用 session 取消，声音线程的延迟启动回调也检查取消状态；恢复通知、文档刷新不补播。提示音不依赖云端密钥或自动朗读，不增加 API 用量。没有调用外部语音 API；实际音量、播放顺序、通知连续切换与取消尚未进行游戏内验收。

## 2026-10-26 数据驱动抽奖

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 只编译开发源集。静态检查确认中英文各 399 个语言键且集合一致，10 份内置及示例商业目录 JSON 可解析，抽奖界面元素 ID 无重复且与 Java 引用一致，抽奖模块语言键齐全，`git diff --check` 通过。

阅读代码确认：奖池从 `system/lottery_pools` 资源加载，使用 Codec 定义奖品、正整数权重及可选消耗；默认池消耗 1 个绿宝石，三个奖品权重为 60/30/10。浏览和预览接入模块注册表、指令与语义路由，单抽只接受独立结构化确认。商城与抽奖共用报价、支付和订单流程；确认绑定玩家、操作类型及目录版本，服务端选中奖励并保存快照，重复确认不会重新收费或抽样。支付前在游戏线程验证整个奖池的物品模板，待支付或结果未知时隐藏未结算奖品，成功后存入收件箱供领取。完整界面有抽奖入口，简约界面复用编号操作，结算口播只读奖品名称。

没有运行测试、启动 Minecraft、创建存档或调用外部 API；奖池重载、概率分布、实际扣款与交付、重复确认、重启恢复、两种界面交互和 TTS 尚未作运行验收。订单存档已改用 `Purchase.order`，没有旧 `Purchase.offer` 兼容逻辑；开发存档若无法解析，使用 `/system reset [玩家]` 后重新授予。

## 2026-10-26 目录 ID 与支付类型

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有运行测试或启动 Minecraft。阅读代码确认：目录奖励和商品 ID、相关请求及签到模型选择使用 `Identifier`，移除 `validId` 正则；`Price.type` 改为 `PaymentType`，通过原版 `StringRepresentable.fromEnum` 编解码，校验及支付分支不再比较字符串。JSON 支付类型仍为 `ITEMS` / `SYSTEM_COIN`；数据包、存档和游戏内交易未作运行验收。

## 2026-10-26 奖励与商城使用 ItemStackTemplate

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；`testModClasses` 只编译开发源集。阅读依赖 JAR 确认：`ItemStackTemplate.CODEC` 解析物品 ID、1～99 的数量及组件补丁，不访问物品默认组件；`create()` 构建物品堆并通过原版严格校验，失败返回空堆。

阅读代码确认：已移除 `PlayerData.Item` 和重载阶段的 `validateItems`，奖励、商品、价格与结果均直接保存模板；奖励及商品产出列表在 Codec 中统一限制为 1～36 项。发奖时先创建全部物品堆，商城在扣款前检查产出；扣除材料按物品与组件精确匹配并逐槽预留，重复模板不会重复扣同一份材料。未运行测试或启动 Minecraft；数据包重载、组件持久化和游戏内交易未作运行验收。

## 2026-10-26 成就规则使用 Identifier

执行 `compileJava testModClasses --console=plain`，主源码和开发源集编译通过；没有运行测试或启动 Minecraft。阅读代码确认：`AdvancementRule` 的规则 ID 与成就 ID 改为 `Identifier`，使用原版 Codec 解析，移除手写正则；登录完成状态、待处理成就事件和已启用基线缓存使用该类型，查询成就不再重复解析。持久化键、事件任务入口与模块参数按原有文本格式转换，配置 JSON 结构不变；缺失或无效的 ID 不生成默认规则，仍由外层 `rules` 的 lenient 字段回退。游戏内事件触发与配置容错未作运行验收。

## 2026-10-26 移除任意上限与重复校验

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译及打包通过；`testModClasses` 只编译开发源集。发布 JAR 为 805,695 字节。静态检查确认中英文各 376 个语言键且集合一致，`git diff --check` 通过。

阅读代码确认：模块、语义 profile、指令别名、成就规则、例句、提示词和收费表不再受 128/256 等任意数量上限约束；移除对应的资源大小及元数据长度限制。执行策略只检查非负次数、正数预算和超时关系，具体预算由模块或管理员决定；缓存仍按配置容量和 TTL 淘汰。查询输入使用通用字符串与非负分页偏移，不在指令解析器、模块和工具中重复限制查询长度。配置错误通过 Codec 返回，字段继续使用 `lenientOptionalFieldOf`；引擎与平台启动不再重复校验同一份配置。

商城取消包数量、包内条目数量、标签长度和固定 64 数量上限，产出在资源加载时按物品自身的堆叠上限校验；付款材料支持跨堆扣除，重复材料使用 `long` 合并数量。重复 ID、类型身份、权限、玩家隔离、支付状态、实际执行预算、网络长度和分页边界继续保留。未运行测试、启动 Minecraft、创建存档或调用外部 API；大规模目录、配置容错及游戏内交付未作运行验收。

## 2026-10-26 请求分派与网络编码统一

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集构建及打包通过；`testModClasses` 只构建开发源集。静态检查确认：请求和响应通过 `DispatchRegistry` 的 `Codec.dispatch` 编解码，网络 `StreamCodec` 直接携带对象；已删除 `AgentRequest.decode`、`mode/encodedInput` 和拆开的 C2S 字符串字段。通知、绑定变更和导航不再使用特殊请求 ID，用量响应使用独立 `open` 标志。网络协议更新为 3。

阅读调用链确认：请求执行、结果投影、卡片展示与口播改用类型处理 Map；模型候选只从可选模块建立 Codec Map，任务查询结果使用命名类型。限流、绑定检查、异步连接代次检查、结果长度限制继续共用入口；打开界面响应关联并完成原请求，导航不占用随后状态刷新的限流间隔。NLS 事件和错误码使用 Map，仍校验 task ID、namespace 与文本长度。

静态检查确认中英文各 376 个语言键且集合一致，Java 包路径及文档本地链接有效，`git diff --check` 通过。发布 JAR 为 811,210 字节，包含新的请求、响应及分派类型，不含开发数据包资源。未运行测试、启动 Minecraft、创建存档或调用外部 API；实际网络往返、类型静态初始化、界面交互及语音行为尚未作运行验收。

## 2026-10-26 配置重载与目录入口收拢

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。静态检查确认：语音页使用 `SpeechConfig.INSTANCE.load()` 重读 `system-speech.json` 并刷新本页草稿；重载按钮不依赖 API Key。TTS 缓存固定使用 `ConfigInstance.getConfigFolder().resolve("tts-cache")`，已移除 `SystemConfig` 和独立音色导入逻辑。

中英文各 376 个语言键且集合一致，Python 工具语法检查和 `git diff --check` 通过。发布 JAR 包含新的重载按钮及双语文案，不含 `SystemConfig`、`SpeechConfig$VoiceProfile` 或开发数据包资源，大小为 784,463 字节。未运行测试、启动 Minecraft 或调用外部 API；配置重载和界面交互尚未作游戏内验收。

## 2026-10-26 PlayerData 与 RecordCodecBuilder

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。阅读代码确认：对话状态及回放缓存归入 `Conversation`，每日模型调用额度归入 `ModelQuota`；顶层构建参数从 17 项缩减为 13 项，两个内联 MapCodec 使用原有字段名。交付、事件、步骤回执和订单同步改用 `RecordCodecBuilder`，已移除无调用的 `ObjectCodec`。静态检查确认中英文各 377 个语言键且集合一致，`git diff --check` 通过；发布 JAR 包含两个新分组，不含 `ObjectCodec` 或开发数据包资源。

解码时为列表、映射、基线集合和解锁集合创建独立可变容器；清空对话和关闭历史复用 `Conversation.clear()` 推进记忆代次。相关读取、写入、保留策略、在途代次检查和每日额度判断已同步。这里只作静态检查与编译，没有运行测试、启动 Minecraft、创建存档或调用外部 API；存档读写与游戏内行为未作运行验收。

## 2026-10-26 ConfigInstance 与变更标记统一

执行 `compileJava testModClasses jar --console=plain`，最新主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。

静态检查确认八种配置均采用 `AdvancementSettings` 的 `ConfigInstance<T> INSTANCE` 写法，读取统一使用 `get()`，界面模式、TTS、ASR 的已提交修改调用 `set(next)` 并自动 `markDirty()`；旧独立加载、保存入口已移除。中英文各 377 个语言键且集合一致，`git diff --check` 通过。

阅读代码确认：资源重载更新已注册容器，tick 与关闭事件保存待写配置；检查标记与保存共用实例锁，失败恢复标记，重载先保存未落盘的改动。客户端事件放在独立客户端订阅类中。服务端引擎、模型连接和成就事件仍持有启动时快照，需要重启完整应用修改。

本轮未运行测试、未启动 Minecraft、未调用模型或语音 API；默认文件复制、重载和延迟保存的实际运行行为尚未验收。下文为此前版本的历史检查记录。

## 2026-10-26 数据包目录与配置拆分

执行 `compileJava testModClasses jar --console=plain`，主源码、开发源集编译与打包通过；`testModClasses` 只编译开发源集。静态检查确认：`AgentSettings` 仅含执行预算、限额、并发和时区；模型连接、语义路由与成就规则分别使用独立配置文件。配置使用 `RecordCodecBuilder`、record getter 与 `lenientOptionalFieldOf`，各配置类管理 `INSTANCE`，统一通过 `ConfigLoader.load(path, codec)` 复制缺失的默认文件并解析。客户端服务不再保存重复的设置对象，收费表也复用同一加载入口。

奖励和商品仅从数据包加载，三个默认签到奖励和一个默认商品均为内置 JSON；没有配置列表兜底。默认资源、示例 JSON 和文档本地链接静态检查通过；发布 JAR 包含八份配置默认文件和四个商品/奖励条目，不含开发源集资源及旧 `AgentSettings.EventRule` 类。中英文语言键未改动。

本轮未运行测试、未启动 Minecraft、未调用模型或语音 API；配置容错、默认文件复制及数据包热重载的实际运行行为尚未验收。

## 2026-10-26 Agent 分包简化

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过。静态检查确认：Agent 根包集中执行与注册职责，领域接口、数据和实现合并到同一包；内置输入输出仍为独立类型。七个 Codec/Schema/JSON 工具移至 `com.iafenvoy.system.util`，包名与目录一致，源码与发布 JAR 均无旧 `api/data/registry/runtime/util/command` Agent 子包引用或类。发布 JAR 为 790,191 字节，低于 10 MB。指令注册修改方法保留包内访问权限。

本轮仅调整目录、引用和文档；没有运行测试、启动 Minecraft 或调用外部 API，编译与静态检查不作为运行时功能验收。

## 2026-10-26 配置字段容错

执行 `compileJava testModClasses --console=plain`，主源码与开发源集编译通过，未运行测试或启动 Minecraft。静态检查确认 config 包内显式字段均使用 `lenientOptionalFieldOf`，可变配置通过 `ObjectCodec.lenientOf` 使用同一机制并在字段层执行原有校验；普通 `ObjectCodec.of` 的存档严格解析未改。收费表跳过不完整或无效的条目，不补造单价。没有对损坏配置进行运行时验收。

## 2026-10-26 Agent 注册与调度重构

执行 `compileJava testModClasses jar --console=plain`，主源码与开发源集编译、打包通过。仅作编译和静态检查，没有运行测试、启动 Minecraft、调用模型或进行语义准确率验收。

阅读代码及静态扫描确认：Module 自带的 `ModuleRouting` 统一生成指令、语义 profile、模型目录与快捷入口；助手补全与游戏聊天转发共用注册表。交互、事件与维护队列复用 `TaskExecutor`，模块执行和权限校验集中到引擎。Tool 仍是单行为、Module 以 List 组合；旧指令 switch、独立内置 profile 安装器、商城安装包装和重复的模块 dispatch Codec 已移除。语言键仍为中英文各 377 个且集合一致，包名与目录一致，JAR 不含已删除的分派类，大小仍低于 10 MB。

运行时待验收：扩展模块一次注册后在两种输入入口执行与补全；未装 FTB 时无任务入口；低分语义回退；结构化付款来源校验；取消与偏好切换的写回隔离；持久事件重启恢复；队列满和关闭时排队 Future 的完成。以上未宣称实测通过。

## Agent 外包与内置数据类型整理

Agent 代码重新集中到 com.iafenvoy.system.agent 内部；输入与输出拆为 30 个 builtin 独立类型。已删除旧结果迁移器、事件授权来源回退和写回执格式归一，账本必需字段直接由当前 Codec 读取。编译与静态检查不作为运行时功能验收；本次未启动 Minecraft 或执行测试。


## 2026-10-26 分包与 Codec 重构

执行了 compileJava、testModClasses 和 jar，均通过；testModClasses 仅编译开发源集。未启动 Minecraft、未执行测试、未调用付费 API，因此不作为运行时功能验收。

静态检查：源码包名与目录一致；中英文各 377 个语言键且集合相同；发布 JAR 的源码目录映射与当次分包一致，没有开发源集资源，仍小于 10 MB。可选 FTB / SVC 的反射类名已随分包更新。配置、玩家存档、调用链及固定 API 报文已改为 Codec，所有结果直接使用 AgentResult.CODEC，无旧结果迁移。


遵循仓库 AGENTS.md：用户未明确要求时不运行测试、不启动 Minecraft，也不创建临时存档、玩家配置或探针。

## 本次检查

本次轻量路由重做已执行 `gradlew compileJava testModClasses jar --console=plain`，主源码与开发源集编译、模组打包通过。`jar` 的完整产物 10,000,000 字节上限检查也通过。System 自身约 260KB，原有 Minecraft/NeoForge/UI 等安装依赖另计。

静态检查确认 JAR 没有内嵌第三方库、ONNX/DJL/Caffeine 类、native 资源或模型文件；新词表约 4.2KB，路由代码仍统一位于 `com.iafenvoy.system.agent.runtime.routing`。构建配置和版本表同步移除三类运行库依赖，旧 E5 阈值不复用到词法匹配。

双语各 193 个键且键集合相同，资源与配置示例 JSON 可解析，文档本地链接有效。阅读代码确认了不同模块分差、输入覆盖率和陌生特征保留、只读模块及工具层限制、低把握单次回退、精确意图缓存不保存业务结果、当前参数重新解析、玩家独立作用域和清理、MC 语言注入。这些是代码审查结论，尚未作运行验收。

`testModClasses` 只编译开发源集，不运行测试。没有执行 `test`、任何 `run*` 任务、客户端或服务端；没有调用 DeepSeek、训练分类器或运行准确率/性能基准。编译通过、启动成功与验收通过是三种不同结论。默认阈值是工程起点，不是验收过的正确率或节省比例。

## 常用编译命令

```powershell
.\gradlew.bat compileJava testModClasses jar --console=plain
```

FTB 仅为 `compileOnly`，默认开发运行不安装。需要用户明确要求测试且选择 FTB 范围时，再使用 `-PwithFtbQuests` 加入三个 `localRuntime` 依赖。没有旧数据包条目再强制要求开发环境安装 FTB。

## 后续人工验收范围

- OFF 不使用本地索引/意图缓存；SHADOW 不影响路由、候选提示和缓存；无外部模型文件即可使用新配置。
- ENFORCE 的独立语料评估：领域别名、陌生词与低覆盖率、高分小分差、否定/指代/复合表达、写请求与不支持表达、缺失/多个物品 ID；相似度不充当正确率。
- 精确意图缓存的成功写入条件、每玩家/全局上限、TTL/LRU、清空/撤销/掉线与迟到写入；更换配置后索引和缓存重建，结果始终查询当前状态。
- `/ai off` 禁止远程回退但允许已启用的本地只读直达；`/memory off` 不缓存意图；诊断不泄露原文/UUID。
- 明确指令与高把握直达的真实调用次数、本地索引/缓存耗时及 MSPT；本地诊断不能代替完整性能基准。

- 默认未绑定、管理员自身/指定在线玩家 grant、重复 grant、成功落盘后绑定提示、未授权命令与未绑定请求拒绝。
- revoke 删除玩家记录、临时领取/报价引用失效、旧模型/游戏/存储回调不能回写、清理失败时解除旧等待但不提示成功；重新授予不会补发既有成就。
- 两种界面切换及本机偏好、简约模式只显示当前轮、编号选择/分页/兑换确认、模式切换保留当前结果、简约模式不加载历史。
- 明确短语与指令跳过模型路由；否定句、普通自然语言仍遵守单次路由约束；签到理由语言与文案状态准确性。
- 无 FTB 的专用服务端加载；客户端 `/system`、输入、滚动、关闭重开及不同 GUI 缩放。
- 没有密钥时结构化查询可用、AI 签到不入账；真实 DeepSeek 的 JSON 格式、超时与调用次数。
- 查询/事件采用不同策略；同玩家并发请求、其他玩家公平性、主线程调度开销。
- 双语键、缺少引用、自选奖励、FTB 换队/重复任务/任务重载、奖励类型失败。
- FTB 任务完成后 HUD 依次显示完成及解锁项；多前置/任选前置、STARTED 依赖、章节依赖、隐藏任务、FLEXIBLE 模式；同队已绑定在线成员接收，未绑定或其他队伍不接收。
- FTB 首次绑定/重登/换队/重载不批量补播；锁定切换、进度重置与重复冷却后的状态比较；客户端进度回显不重复通知，静默管理操作不播报；按 T 点击详情/确定后继续队列。
- 相同签到请求、跨日、掉线、收件箱格数不足和物理交付关键窗口崩溃。
- 玩家 A/B 历史隔离、旧连接回包、清空记忆代次、关闭保存/外部模型。
- 新规则基线、重复成就、停用规则、重启恢复、部分成功与事件持久化缺口。
- 商城浏览/分页/确认、材料不足不扣除、重复材料合并、排除命名/附魔/损坏物品、重复确认及跨玩家报价隔离。
- 数据包目录分别覆盖、同路径优先级、禁用条目、无效物品拒绝重载、旧报价失效和签到推理期间重载。
- 扣款前存储额度预留、锁定权益不可领取、支付后崩溃/掉线、未知订单人工核对；未启用系统币时无法支付。

只有明确指定并实际执行后，才能记录这些场景通过；本轮没有作这些结论。
