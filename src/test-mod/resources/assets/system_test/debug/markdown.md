# Markdown 渲染测试

在系统界面的输入框中发送 `/debug md` 即可展示本卡片，不调用模型或播报，也不写入服务端历史。

## 文字与段落

普通中文 English 123。**粗体文字**、*斜体文字*、***粗斜体文字***，以及 `行内代码`。

这里是第二个段落。下面的两行之间应当强制换行：  
这是换行后的文字。

### 三级标题

#### 四级标题

##### 五级标题

###### 六级标题

## 嵌套列表

1. 第一项
2. 第二项
    - 无序子项 A
    - 无序子项 B
        1. 深层有序子项
        2. 深层有序子项
3. 第三项，编号不应受嵌套列表影响

- 无序列表第一项
- 无序列表第二项
    - 子项

## 引用与分割线

> 第一层引用，包含 **粗体**。
>
> 第二个引用段落。
>
> > 第二层引用。

---

## 代码准确性

行内比较：`a < b && c > d`，泛型：`List<String>`。

下面应分别显示 `<`、字面的 `&lt;` 和字面的 `&amp;lt;`，不能混在一起：

`<` / `&lt;` / `&amp;lt;`

```java
List<String> names = List.of("宿主", "Dev");
if (count < 10 && count > 0) {
    System.out.println("<tag> &lt; &amp;lt;");
}
```

```html
<a href="https://example.com">代码中的 a 标签不能点击</a>
<img src="https://example.com/test.png" onerror="alert('禁止执行')">
<script>alert("这里只是代码");</script>
```

~~~text
相邻的第三段代码不应显示围栏标记。

上一行的空白需要保留。
~~~

    缩进代码：<a href="https://example.com">同样不能点击</a>
    原始实体：&lt; &amp;lt;

## 链接与 HTML

普通 Markdown 链接应可点击，并走 Minecraft 的网址确认流程：

- [示例网页](https://example.com/?a=1&b=2 "自定义标题")
- [包含格式的 **链接**](https://example.com)
- [引用式链接][example]

[example]: https://example.com "引用式标题"

原始 HTML 应完整显示为文字，以下 a 标签不能点击：

<a href="https://example.com">原始 HTML 链接</a>

<a title="a > b & c < d" href="https://example.com/?a=1&b=2">带比较符号与双引号的属性应按原文显示</a>

<span onclick="alert('禁止执行')" style="color:red">原始属性不能改变页面行为</span>

<script>alert("这段应当显示为文字");</script>

以下网址不应有点击动作：[非法协议](javascript:alert(1))、[本地文件](file:///C:/test.txt)。

尖括号网址按普通文字显示，请使用上面的 Markdown 链接语法：<https://example.com>。

## 图片与特殊字符

图片只显示替代文字，不发起下载：![图片替代文字 $1 \ 路径](https://example.com/test.png "图片标题")。

普通比较：a < b，c > d。实体：&lt;span&gt;、&amp;、&quot;、&#39;。

命名实体应显示成字符：&ldquo;引号&rdquo;、&hellip;、&copy;、&mdash;。

代码中的实体名称应保持原文：`&ldquo; &rdquo; &hellip; &copy; &amp;lt;`。

含特殊字符的代码：`$1 \ {{answer}} &a §a`。

上一行末尾的 `&a` 和 `§a` 都是代码原文，不能被删除或作为颜色标记处理。

### 扩展标题 ID {#sample-id}

标题正文正常显示，扩展 ID 不应带入页面属性。

### 不可信 ID {#test" onclick="alert(1)}

此标题仍应只是标题，不能产生额外事件。
