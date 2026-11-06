# Card JSON 离线编辑器

双击打开 [tools/card_editor.html](../tools/card_editor.html)，使用现代浏览器即可，不需要安装依赖或启动服务。页面不调用模型或其他外部 API，也不会修改游戏内数据。

## 编辑流程

1. 从下拉框载入内置模板，或使用“打开 JSON”／拖入文件读取 `assets/<namespace>/cards/*.json`。
2. 左侧节点树支持添加、复制、删除、上移和下移。中间“属性”页编辑已知字段，“完整对象 JSON”保留子节点和自定义字段；也可以直接修改原始 JSON。
3. 在“上下文”填写示例参数，在“文案”提供翻译覆盖。右侧切换完整、简约、通知模式及语言，查看布局、分组、折叠和本地分页。按住 Alt 点击预览中的节点可定位到属性页。
4. “导出卡片”下载可用于资源包的 JSON；放回原资源路径后，在游戏内重载资源。浏览器无法直接覆盖原文件。

编辑器按当前 `CardTemplate` 和 23 种内置 `CardNode` 提供字段编辑与基础类型检查。未知节点会显示占位提示，未知字段在编辑、复制与导出时保留；自定义节点的字段可在完整对象或原始 JSON 中修改。语法或结构错误时保留上一份有效预览，并暂停树操作与导出，修复 JSON 后恢复。它没有运行 Minecraft 的 Codec，最终解析结果以游戏为准。

快捷键：`Ctrl / ⌘ + S` 导出卡片，`Ctrl / ⌘ + Shift + F` 格式化。顶部撤销／重做回退有效的卡片版本；文本框内使用浏览器原生撤销。上下文、翻译和 CSS 不参与卡片版本的撤销。

## 示例上下文

上下文共用一份平铺参数表，点号是键名的一部分，不能改成嵌套对象。`parameters` 模拟 `CardContext.parameters`；`states` 模拟数值状态及循环数量。没有显式状态时，编辑器按 `CardContext.Builder.put` 的规则推导：布尔为 0/1、数值保持原值、非空文字为 1、数组为长度。

```json
{
  "parameters": {
    "summary": "叮！今日资源已就绪。",
    "host": "Dev",
    "entries": 2,
    "entries.0.name": "木材",
    "entries.1.name": "铁锭",
    "result_items": [
      { "item": { "id": "minecraft:iron_ingot" }, "amount": "16" }
    ]
  },
  "states": {
    "show_card_title": 0,
    "show_extra": 1
  },
  "callbacks": ["example"]
}
```

支持 `{{id}}`、`{{tr:key}}`、`{{field:id}}`、`{{bool:id}}`、`{{state:id}}`，以及 `when`、`unless`、`repeat` 和循环索引 `*`。`full`、`simple`、`notification`、`text_items` 由当前预览模式设置；前三者仅当前模式为 1，浏览器布局预览的 `text_items` 始终为 0。

例如加入下面的节点，可显示两个条目：

```json
{
  "type": "system:column",
  "repeat": "entries",
  "children": [
    { "type": "system:text", "text": "{{entries.*.name}}" }
  ]
}
```

`callbacks` 仅声明哪些回调可用，`result_actions` 可提供模拟按钮列表。点击按钮只在预览下方记录动作，不发包、不打开链接、不领奖或购买。通知底部按钮是模拟控件，仍由游戏代码定义，不能通过卡片 JSON 修改。

## 工作文件与草稿

“导出工作文件”生成带 `format: "system-card-editor"` 的 `.card-project.json`，保存卡片、示例上下文、翻译覆盖及自定义预览 CSS。重新打开该文件可恢复编辑环境；它不能直接作为游戏卡片资源，需再导出卡片。

有效编辑会尽力写入浏览器本地草稿，并在下次打开时恢复。`file://` 下的存储支持因浏览器和配置而异，移动文件也可能改变草稿归属；需要长期保留时导出工作文件。清除草稿不会清空当前工作区，继续编辑会重新保存。

## 预览范围与内置快照

页面包含当前 27 份卡片模板、中英文语言表及共享 `assistant.css` 的快照。快照不会自动读取仓库的后续修改；最新卡片通过“打开 JSON”载入，翻译与额外样式可分别填写。完整字段和游戏侧布局规则见 [客户端 JSON 卡片](cards.md)。

浏览器预览用于检查结构与参数替换，和 AUI 的布局实现有差异：物品、FTB 图标、玩家模型用占位块和普通 tooltip 展示；Saturn 玩家信息使用示例参数；Markdown 仅近似支持标题、列表、引用、代码、强调及链接。资源包纹理、原生 tooltip、主题细节、按钮权限与业务行为需在游戏内确认。预览对循环和节点绘制设有上限，导出的 JSON 保持完整。

维护工具时，节点字段和浏览器投影位于页面 `editor-script` 中的 `catalog` 与渲染函数；`bundled` 是被动 JSON 快照。新增游戏节点后同时更新这两处。只刷新资源快照可在仓库根目录运行下面的 Python 片段：

```python
import json
import re
from datetime import date
from pathlib import Path

assets = Path("src/main/resources/assets/system")
bundle = {
    "snapshotDate": date.today().isoformat(),
    "templates": {
        p.stem: json.loads(p.read_text(encoding="utf-8"))
        for p in sorted((assets / "cards").glob("*.json"))
    },
    "languages": {
        lang: json.loads((assets / "lang" / f"{lang}.json").read_text(encoding="utf-8"))
        for lang in ("zh_cn", "en_us")
    },
    "previewCSS": Path(
        "src/main/resources/assets/apricityui/apricity/system/assistant.css"
    ).read_text(encoding="utf-8")
}
encoded = json.dumps(bundle, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c")
page = Path("tools/card_editor.html")
source, count = re.subn(
    r'(<script id="bundled" type="application/json">)[\s\S]*?(</script>)',
    lambda m: m[1] + encoded + m[2],
    page.read_text(encoding="utf-8"),
    count=1
)
assert count == 1
page.write_text(source, encoding="utf-8")
```
