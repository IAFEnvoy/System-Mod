# 页面布局审计（2026-11-06）

本次覆盖完整对话、简约模式、设置、用量统计、通知 HUD、常驻卡片 HUD，以及它们共用的卡片节点。目标版本为 Minecraft 26.1.2 / ApricityUI 1.2.9。最初的源码审计未解决用户实测的打开卡顿；2026-11-07 用户提供的 spark 采样进一步定位到滤镜合成的同步 GPU 回读，见下节。首次布局日志和此前的源码推断不能代替采样结论。

## spark：持续掉帧的主要阻塞点（2026-11-07）

用户采样：[7NHlatpgiI](https://spark.lucko.me/7NHlatpgiI)。采样持续 13.625 秒，间隔 4ms，Render thread 记录 13,324ms。该次配置**只收录超过 30ms 的帧 / tick**，共 227 个；以下百分比仅针对这些慢帧，不代表整个游戏的全部帧。

| 调用 | 渲染线程采样时间 | 占比 |
| --- | ---: | ---: |
| AUI `Base.drawOverlayDocument` | 9,240ms | 69.35% |
| 其中 `GL11C.nglReadPixels` | 6,596ms | 49.50% |
| `LayoutLifecycle.prepareRender` | 964ms | 7.24% |
| `AssistantScreen.tick` | 132ms | 0.99% |
| `AssistantInputScreen.tick` | 24ms | 0.18% |

表中存在包含关系，不能相加。两条独立 PIP 绘制分支分别有 5,424ms 和 1,172ms 落在像素回读中；采样没有记录 DOM 元素 ID，不能单凭两条分支判定具体是哪两个页面或元素。

主要调用链为：

```text
ApricityUiPipRenderer.renderToTexture
  → Base.drawOverlayDocument → Base.paintDocumentNodes
  → RenderNode.FilterPopNode.render → FilterRenderer.popFilter
  → RenderService.resolveTarget → resolveBlitDestination
  → GL11.glReadPixels → GL11C.nglReadPixels
```

核对当前依赖的实际 sources JAR：

- `Drawer.hasCompositedFilter` 和 `RenderNode.FilterPushNode / FilterPopNode` 将普通 `opacity < 1` 也纳入离屏滤镜合成；并不要求 CSS 显式写 `filter`。
- `FilterRenderer.popFilter` 在采样刚绘制的图层之前无条件调用 `resolveTarget`。26.1 的 `RenderService.resolveTarget` 对同一个目标读取五个位置，每次同步读回一个 RGBA 像素。
- 回读字节数虽小，CPU 仍可能等待 GPU 前序工作完成；采样确认主要时间停在这里，不能据此进一步断言 GPU 的具体硬件 / 驱动故障。
- 依赖源码说明这是为了解决附件写入后采样到旧内容的问题。直接删除所有回读可能导致滤镜 / 半透明图层出现黑块，不在 System 内增加全局 Mixin 来绕过。

System 的针对性修正：卡片 `::after` 的静态 `opacity: 0.6` 改为边线颜色，hover 恢复强调色；禁用按钮、下拉框和开关使用主题的 disabled 颜色，不再通过分组 opacity 变暗。原先这些样式只要可见就会持续产生合成层，常驻卡片也可能在空白欢迎页上方一起绘制。消息、通知和常驻卡片的短暂淡入淡出仍保留，因此动画期间仍会走依赖的合成路径；资源包新增的 filter / opacity 也可能触发。

上述修正消除了已确认的静态样式触发点，**尚未取得修正后的运行采样，不能宣称所有打开卡顿都已解决**。依赖层的长期修复应处理离屏写入到纹理采样的可见性 / 同步，避免每帧 CPU 回读。本次只做资源打包、Java 编译和 diff 检查，不启动游戏、不插入探针。

## spark：新卡片弹出时的尖峰（2026-11-07）

用户确认上一节修改解决持续掉帧，随后提供新采样：[eNwmBEM9iw](https://spark.lucko.me/eNwmBEM9iw)。该次持续 17.379 秒，间隔 4ms，只收录超过 **50ms** 的帧 / tick，共 123 个；Render thread 记录 12,720ms。筛选条件与上一份不同，不能直接按两个百分比推算帧率改善倍数。

- `glReadPixels` 仅占 52ms / 0.41%；新的主要热点转为样式更新与布局。
- `Transition.build` 的最外层分支合计 3,060ms / 24.06%，其中客户端 tick 内的一条分支为 2,896ms，几乎全部进入 `Size.of → Flex / Grid` 尺寸测量。
- `LayoutLifecycle.prepareRender` 为 2,952ms / 23.21%；`CardPresentation.render` 合计仅 40ms / 0.31%。这些是包含时间，不能与其子调用相加。

源码依据：AUI 1.2.9 `Transition.build` 遇到 `transform` 先执行 `Size.of(element)`，然后 `Transform.createTransition` 才比较起止变换。默认主题 `.t08-arwes-theme button` 比 System 原先的 `button` 选择器优先级高，给新按钮重新加上了 transform 过渡；即使变换没有变化，样式更新仍会触发深层测量。原先所有历史消息行和图钉 SVG 也一直保留 transform 过渡声明。采样没有 DOM 元素 ID，不能量化某一个具体按钮的贡献，但调用路径和这些 CSS 触发点都已核对。

本轮修正：

- 普通按钮的选择器明确覆盖原生主题，仅过渡颜色、边框和阴影，不附带 hover 位移或 transform 过渡。图钉继续按状态倾斜 / 竖直显示，取消旋转插值。
- `.message_new` 只保留起始位置与透明度，首次布局时不创建过渡。已提交几何后切到 `.message_entering`，保留 240ms 平滑上移与淡入；等待 `Transition.isActive` 结束再移除此类。静止历史行没有 transform 或过渡声明，后续消息到达时不会为旧行重建变换过渡。
- 对话滚动区明确使用 `flex: 1 1 0`，由固定主区域分配空间，不用内容高度决定初始 flex basis；保留原来的底部对齐、滚动和分页。

未添加定时延迟、布局强制提交、Mixin 或运行时探针。首次真实布局与动画绘制仍有成本，本次编译 / 资源打包及静态检查不能证明尖峰已完全消除；修正后的游戏内表现尚未采样。

## spark：边框颜色动画误触发全量布局（2026-11-07）

用户运行后仍看到尖峰，并提供只包含异常帧的采样：[cR5WPLTxKf](https://spark.lucko.me/cR5WPLTxKf)。元数据记录持续 11.108 秒、4ms 采样间隔、50ms 筛选阈值，共 87 个帧 / tick；Render thread 累计 7,576ms。下列占比**只描述入选的慢帧**，不能当作平时每帧的平均开销。

| 调用 | 采样时间 | 占比 |
| --- | ---: | ---: |
| `LayoutCommit.commitFull` 两条独立分支合计 | 3,220ms | 42.50% |
| 其中渲染阶段分支 | 1,836ms | 24.23% |
| 其中客户端 tick 阶段分支 | 1,384ms | 18.27% |
| `Document.commitStyleRecalc` | 128ms | 1.69% |
| `CardPresentation.render` | 40ms | 0.53% |
| `GL11C.nglReadPixels` | 68ms | 0.90% |

该次没有采到 `Transition.build`；这不代表方法绝对不执行，但上一轮最大的过渡创建路径已不再是本报告的热点。真正阻塞仍在 `LayoutCommit → Rect → Position → Flex / Grid`，不能将其归因于卡片 JSON 解析或网络请求。

继续核对当前依赖源码，确认此前遗漏的触发点：

1. `Transition.merge` 将插值后的 `border-*-color` 写回 Style 的边框字段。
2. `LayoutInvalidation.motionAffectsLayout` 对 `border / borderTop / borderRight / borderBottom / borderLeft` 直接比较完整字符串，没有像普通样式失效路径那样只比较宽度和线型；颜色不同也返回 true。
3. `MotionTrack.invalidateMotionCaches` 因此进入 `invalidateLayoutMotion → LayoutInvalidation.motionChanged`，使尺寸和祖先布局缓存失效，登记 layout roots。
4. `LayoutLifecycle.prepareRender` 看到非空 layout roots，调用全量 `LayoutCommit.commit`；一次本应只影响边框颜色的过渡，会在多个动画帧重复布局。

这是源码确认的可触发路径，采样没有元素 ID，无法将全部 3,220ms 精确归到某一个按钮。System 上一轮仍保留普通按钮、卡片 hover 和设置开关轨道的 `border-color` 过渡，均会进入此路径。本轮移除这四处过渡声明，边框仍按 hover / focus / selected / disabled 即时变色，继续保留背景、文字、阴影、消息位移和通知淡入淡出。普通按钮的高优先级规则也排除主题重新引入此过渡。

没有关闭动画总开关、缩减任务数量、额外强制提交布局或修改 AUI 依赖。新卡片加入 DOM 时的真实布局仍存在；本轮只完成源码核对、编译 / 资源打包和 diff 检查，尚无修正后的运行采样，不能宣称尖峰已经完全消失。

## 打开页面卡顿的后续修正

用户运行后的 `run-test-client/logs/aui.log` 记录：18:43:37 创建 `system/assistant.html` 用时 175ms，18:43:46 首次布局记录为 8786ms、45 个元素；同一页面后续一次提交仅 12ms。首次布局分片记录的是跨帧墙钟耗时，不能直接作为某一个方法的 CPU 耗时，也不能据此把卡顿归因于 CSS 文件解析。

按实际依赖源码进一步核对并调整：

- 移除提取阶段的 `AssistantPages.prepareFrame → prepareRender`。AUI 的 `Base.drawDocumentInContext` 本身会调用 `prepareRender`；再次调用会重复推进首次布局、动画和滚动，并重置分片的帧内标记。页面现在只观察原生布局就绪状态，不自行执行渲染生命周期。
- 初始化时申请主题、填充内容，不再额外 `applyViewport(true)` 或切换 `manuallyRendered`。原生 Screen 已处理初始视口；主题选择与资源重载仍会应用到页面。
- `AbstractText.setSelectionRange` 内部调用 `clampScroll → Box.innerSize → Size.of`，不是单纯写入光标位置。空输入框保留默认光标；非空草稿在布局完成后、几何缓存作用域内定位光标，避免初始化未完成时测量祖先页面。
- 全部窗口已有视口锚点，body 改为普通块。完整页面的固定尺寸外壳、左右栏通过定位边界划分，切断 workspace / layout / main 之间的嵌套 flex 自然尺寸测量；主区域内的对话、工具栏和输入框仍按原有 flex 排列。小屏侧栏宽度与主区起点同步调整。

这些是源码确认的重复调用和测量路径修正；没有通过改变分片预算或延迟显示来掩盖耗时。保留模板本地 CSS 预热，实际是否消除几秒停顿仍需用户运行后核对。

## 设置页打开卡顿的后续修正

用户反馈在新增常驻卡片配置后，刚打开设置就再次卡死。最新 `run-test-client/logs/aui.log` 在 21:03:05 记录页面创建 147ms，21:03:06 记录首次布局 35ms / 79 个元素，之后没有阻塞段信息。排查时游戏已退出，未取得当时的线程堆栈；不能把首次布局日志当作完整打开耗时，也不能确认唯一阻塞点。

实际 AUI 1.2.9 `Transition.build` 对 transform 无条件调用 `Size.of(element)`，然后才比较起止值；设置页开关滑块的伪元素原来长期声明 transform 过渡，祖先又包含 workspace → settings_content → settings_page → speech_step → speech_controls → button 多层 flex。以下修改移除了这条提前测量路径并缩短自然尺寸依赖：

- 开关改为普通块按钮、绝对定位的固定尺寸轨道及块状标签；滑块以 `left:3px / 19px` 过渡，保持开关外观和操作，避免 transform 过渡在样式计算时测量整条祖先链。
- 设置内容区、活动标签页和分组使用普通块流；滚动区保留 `flex:1 1 0` 与 `min-height:0`，由固定尺寸窗口分配空间，长内容只在内容区滚动。组内与组间间距改用 margin。
- 原生主题选择器在分组内绝对定位，不再参与自动高度行的 flex 测量。设置窗口的位置计算使用自身的 960 × 800 尺寸，避免继承完整对话的中心偏移。
- 标签切换不再把页面切回 flex，控件刷新只写入发生变化的 class、aria-selected、display 和 disabled，减少不必要的样式失效。

保留所有设置、即时保存和原生渲染生命周期；未加入额外布局提交、循环测量、延迟计时或运行时探针。只完成源码核对、编译和静态检查，实际打开、换页、滚动和开关滑动仍需游戏内验收。

## 依赖依据

读取 Gradle 缓存中实际使用的 `ApricityUI-neoforge-26.1-1.2.9-sources.jar`，核对 Document、Element、LayoutLifecycle、LayoutCommit、ScrollModel、Box、Grid、Rect 和 Base。不要直接以可能残留旧文件的 build/auisrc 或另一个分支的本地 AUI 工程代替当前依赖。

- `commitRenderState()` 可能触发同步全量几何提交；首次布局分片由 `prepareRender()` 启动。直接在初始化、点击事件中提交会绕过这个启动点。
- `GeometryQueryScope` 提供矩形、样式和测量缓存，不是递归保护或执行超时。
- `Box.innerSize()` 会进入 `Size.of()`；`hasVerticalScrollRange()` / `hasHorizontalScrollRange()` 还会重新提交滚动度量，不是简单字段读取。
- 同值 `setAttribute`、`setInlineStyleProperty`、`classList.toggle(token, force)` 仍可能使样式失效；重复 `setTextContent` 也会清理文本测量缓存。
- AUI 插入原始 Element 时可能替换为标签的原生实现，后续事件必须引用初始化后的节点。

## 各页面处理

| 页面 / 路径 | 处理 |
| --- | --- |
| 所有 Screen | 初始化只申请主题、创建内容和绑定；原生 AUI 绘制负责 prepareRender，提取阶段只观察已完成的布局并执行滚动 / 草稿光标恢复，不额外提交或循环至稳定。 |
| 完整对话 | 原先新增一条消息就重建全部历史卡片，改为按 ChatEntry 复用原生消息节点，只创建或替换变化的行。资源重载和文档重建仍完整刷新。滚动位置在修改前记录，在新布局就绪后恢复，保留底部跟随和向上阅读。 |
| 简约模式 | 忙碌状态变化只更新状态文字；未变的回复不重建卡片、不重置正文滚动。 |
| 设置 | 内容、标签页和分组改为块流，滚动区使用零 flex basis；主题选择器与开关轨道绝对定位，滑块用 left 过渡。保持即时保存，重复刷新不重写相同 class / aria-selected / display / disabled，不主动测量布局。 |
| 用量统计 | 仍在响应、翻页或本地用量快照变化时更新；没有新增逐帧重建。共用新的 Screen 布局边界和文本保护。 |
| 通知 HUD | 移除创建时连续两次整页提交；保留展开、文字淡入淡出的样式起点提交及手动推进队列。识别状态 display 不变时不重复写入。Saturn 定位继续只读取已提交矩形。 |
| 常驻 HUD | 原先每 tick 重写 interactive class，改为仅在状态变化时更新。进度快照只替换变化卡片的内部内容，保留动画包装层及其余 DOM；按 Saturn 锚点选择左右出入，只过渡 transform / opacity，使用像素位移。首次入场等待有效的已提交矩形，状态切换只提交待更新样式，AUI 原生渲染负责动画推进，不在 tick 中强制整页布局。 |
| 卡片交互 | 翻页、章节切换、来源分类、折叠和图钉更新只修改 DOM / 排队失效，取消事件中的整页布局提交。折叠回调改为持有原生节点。 |
| 几何观察 | 补全 / IME、滚动范围和侧栏滚轮改用已提交几何，未就绪时不触发布局补算。玩家和 FTB 图标的自定义绘制也从有效矩形计算内容区域。 |
| 全局鼠标释放 | 按用户要求移除 System 的鼠标释放 Mixin 及其注册，不再扫描全局 AUI 文档或修改依赖的呼出鼠标行为；通知与常驻卡片仍通过聊天栏交互。 |
| 货架 / 配方 | 等宽列使用 minmax(0, 1fr)，避免自动最小列宽把任务标题 / 奖励子树带入额外的固有尺寸测量。单页按条目数与列数收缩行数，多页保留配置的固定行区域；仅创建当前页 DOM，保留单页置灰按钮，不查询布局计算行数。 |

AssistantView.reconcile 共用增量子节点协调；AssistantGeometry 共用几何观察。没有增加后台布局线程、任意重试循环、页面数量限制、第三方依赖或新的配置开关。

## 验证与边界

执行 `gradlew compileJava processResources --console=plain`、语言键 / JSON 静态检查和 `git diff --check`；不启动 Minecraft、不插入运行时探针，不创建存档。打开卡顿后续修正也已编译通过；编译通过只说明 API 与源码相符，无法确认实际卡顿已经消失。首次布局的原生预算也不保证单个复杂元素能在固定毫秒内完成。

游戏内仍需验收：长历史中打开任务卡片、章节与分页切换、滚轮沿父级传播、滚动条出现后的跟随、消息入场动画、补全和中文候选定位、连续通知 / 返回、HUD 进度刷新及 F3+T 后的恢复。若再次卡住，应在游戏仍停住时获取渲染线程堆栈，以区分样式解析、尺寸测量、字体栅格化或其他阻塞。
