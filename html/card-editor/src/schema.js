import { object, own } from "./utils.js";

export const common = {
  id: ["string", "稳定节点 ID，用于分页和折叠状态"],
  style: ["style", "CSS 类名；字符串或字符串数组"],
  when: ["string", "状态非零时显示"],
  unless: ["string", "状态非零时隐藏"],
  repeat: ["string", "循环次数对应的状态键"],
};
const textFields = {
  text: ["string", "文本，支持 {{占位符}}"],
  translate: ["string", "翻译键，优先于 text"],
  args: ["strings", "翻译参数"],
  inline: ["boolean", "行内文字"],
};
const collectionFields = {
  source: ["string", "平铺集合或数量绑定"],
  filter: ["string", "每条原始索引对应的状态"],
};
const gridFields = {
  ...collectionFields,
  columns: ["integer", "完整模式列数，默认 6"],
  compact_columns: ["integer", "其他模式列数，默认 3"],
  max_row: ["integer", "每页最多行数，默认 3；单页按实际行数收缩"],
  cell: ["integer", "格子高度，默认 104"],
};
export const catalog = new Map();
function register(
  id,
  category,
  name,
  defaults,
  fields = {},
  slots = [],
  css = "",
) {
  catalog.set("system:" + id, {
    name,
    category,
    defaults: { type: "system:" + id, ...defaults },
    fields,
    slots,
    css,
  });
}
register(
  "text",
  "文本",
  "文本",
  { text: "新文本" },
  textFields,
  [],
  "card_body",
);
register(
  "markdown",
  "文本",
  "Markdown",
  { text: "{{answer}}" },
  { text: ["string", "Markdown 原文"] },
  [],
  "card_markdown",
);
register(
  "title",
  "文本",
  "标题",
  { text: "新标题", level: 2 },
  { ...textFields, level: ["integer", "标题级别 1～4"] },
  [],
  "card_title",
);
register(
  "image",
  "媒体",
  "图片",
  { source: "system:icons/pin.svg", width: 32, height: 32 },
  {
    source: ["string!", "AUI 本地资源 ID，预览使用占位"],
    alt: ["string", "替代文字"],
    width: ["integer", "宽度"],
    height: ["integer", "高度"],
  },
  [],
  "card_image",
);
register(
  "item",
  "媒体",
  "单物品",
  { item: "{{result_items.0}}", size: 32 },
  {
    item: ["string!", "单个 ItemPreviewOutput"],
    size: ["integer", "图标像素大小"],
  },
  ["tooltip"],
  "card_item",
);
register(
  "ftb_icon",
  "媒体",
  "单 FTB 图标",
  { icon: "{{actions.0.action_icon}}" },
  { icon: ["string!", "单个 FtbIconData"] },
  [],
  "quest_icon_entry",
);
register(
  "player",
  "媒体",
  "玩家模型",
  { player: "{{player_id}}", follow_mouse: true, scale: 1 },
  {
    player: ["string", "UUID 参数绑定"],
    follow_mouse: ["boolean", "跟随鼠标"],
    yaw: ["number", "水平朝向"],
    pitch: ["number", "俯仰"],
    scale: ["number", "缩放"],
    offset_x: ["number", "水平偏移"],
    offset_y: ["number", "垂直偏移"],
  },
  [],
  "player_preview",
);
register(
  "flex",
  "布局",
  "弹性布局",
  { direction: "row", wrap: true, children: [] },
  {
    ...collectionFields,
    direction: ["string", "row 或 column"],
    wrap: ["boolean", "换行"],
    page_size: ["integer", "0 不分页"],
    text_separator: ["string", "文本分隔符"],
  },
  ["children"],
  "card_flex",
);
register(
  "grid",
  "布局",
  "分页网格",
  { source: "{{offers}}", columns: 6, max_row: 3, cell: 104, children: [] },
  gridFields,
  ["children"],
  "card_grid",
);
register(
  "container",
  "容器",
  "普通容器",
  { children: [] },
  { text_separator: ["string", "文本分隔符"] },
  ["children"],
  "card_container",
);
register(
  "groups",
  "容器",
  "分组选择",
  {
    source: "{{actions}}",
    group: "{{actions.*.action_chapter_id}}",
    group_label: "{{actions.*.action_chapter}}",
    children: [],
  },
  {
    ...collectionFields,
    group: ["string!", "分组 ID"],
    group_label: ["string", "分组名称"],
    section: ["string", "章节组 ID"],
    section_label: ["string", "章节组名称"],
    orientation: ["string", "vertical 或 horizontal"],
    options: ["json", "可选固定分类 [{id,label}]，空分类置灰"],
  },
  ["children"],
  "card_grouped_actions",
);
register(
  "details",
  "容器",
  "折叠区",
  { summary: "展开详情", open: false, children: [] },
  { summary: ["string!", "折叠标题"], open: ["boolean", "默认展开"] },
  ["children"],
  "card_details",
);
register(
  "button",
  "交互",
  "按钮",
  { text: "执行操作", click: "return-val", value: "example:action" },
  {
    text: ["string", "按钮文字"],
    click: ["string", "link / return-val / local"],
    value: ["string", "URL、服务端字符串或本地回调 ID"],
    label: ["string", "无障碍文字"],
    icon: ["string", "本地图片 ID"],
    binding: ["string", "客户端状态绑定"],
    selected: ["string", "选中状态绑定"],
    disabled: ["string", "禁用状态绑定"],
    behavior: ["string", "replace / push / wait / close"],
  },
  ["children"],
  "card_action",
);
register(
  "link",
  "交互",
  "超链接",
  { text: "查看链接", url: "https://example.com" },
  {
    text: ["string", "链接文字"],
    url: ["string!", "HTTP / HTTPS URL；预览仅模拟"],
  },
  [],
  "card_link",
);
register("divider", "展示", "分割线", {}, {}, [], "card_divider");
register(
  "progress",
  "展示",
  "进度条",
  { value: "{{progress}}" },
  { value: ["string!", "0～100 数值参数"] },
  [],
  "card_progress",
);
register(
  "player_information",
  "集成",
  "Saturn 玩家信息",
  { importance: 40 },
  { importance: ["integer", "仅显示重要度严格高于此值的行"] },
  [],
  "card_column",
);
register(
  "recipe",
  "集成",
  "单配方",
  { recipe: "{{sources.0.source}}" },
  { recipe: ["string!", "单个 ItemSource"] },
  [],
  "source_entry",
);

export const rootFields = {
  style: common.style,
  show_title: ["boolean", "是否显示通用标题，默认 true"],
  hide_summary: ["boolean", "隐藏摘要，默认 false"],
};

export const getDescriptor = (node) => catalog.get(node?.type);

export function validate(value) {
  const problems = [];
  const fail = (path, message, severity = "error") =>
    problems.push({ path, message, severity });
  function fields(node, spec, path) {
    for (const [key, [raw]] of Object.entries(spec)) {
      const required = raw.endsWith("!"),
        type = raw.replace("!", "");
      if (!own(node, key)) {
        if (required) fail(path, `缺少 ${key}`);
        continue;
      }
      const v = node[key];
      const valid =
        type === "style"
          ? typeof v === "string" ||
            (Array.isArray(v) && v.every((x) => typeof x === "string"))
          : type === "strings"
            ? Array.isArray(v) && v.every((x) => typeof x === "string")
            : type === "json"
              ? Array.isArray(v) &&
                v.every(
                  (x) =>
                    object(x) &&
                    typeof x.id === "string" &&
                    typeof x.label === "string",
                )
              : type === "integer"
                ? Number.isInteger(v)
                : type === "number"
                  ? typeof v === "number" && Number.isFinite(v)
                  : typeof v === type;
      if (!valid) fail([...path, key], `应为 ${type}`);
    }
  }
  function nodes(list, path) {
    if (!Array.isArray(list)) {
      fail(path, "应为节点数组");
      return;
    }
    list.forEach((node, index) => {
      const here = [...path, index];
      if (!object(node)) {
        fail(here, "节点应为对象");
        return;
      }
      if (typeof node.type !== "string") {
        fail(here, "缺少字符串 type");
        return;
      }
      const info = getDescriptor(node);
      if (!info)
        fail(
          here,
          `未知节点 ${node.type}，预览用占位显示；请确认游戏中已注册`,
          "warning",
        );
      fields(node, { ...common, ...info?.fields }, here);
      for (const slot of info?.slots ?? []) {
        if (!own(node, slot)) {
          if (
            slot === "children" &&
            [
              "system:container",
              "system:flex",
              "system:grid",
              "system:groups",
              "system:details",
            ].includes(node.type)
          )
            fail(here, "缺少 children");
        } else nodes(node[slot], [...here, slot]);
      }
    });
  }
  if (!object(value)) {
    fail([], "卡片根必须为对象");
    return problems;
  }
  fields(value, rootFields, []);
  nodes(value.body, ["body"]);
  if (own(value, "actions")) {
    if (!object(value.actions)) fail(["actions"], "应为对象");
    else {
      fields(value.actions, { numbered: ["boolean"] }, ["actions"]);
      if (own(value.actions, "label"))
        nodes(value.actions.label, ["actions", "label"]);
    }
  }
  return problems;
}

export function checkContext(value) {
  if (
    !object(value) ||
    !object(value.parameters ?? {}) ||
    !object(value.states ?? {}) ||
    !Array.isArray(value.callbacks ?? [])
  )
    throw new Error("应包含 parameters / states 对象和 callbacks 数组");
  if (
    Object.values(value.states ?? {}).some(
      (v) => typeof v !== "number" || !Number.isFinite(v),
    )
  )
    throw new Error("states 的值必须是有限数值");
  if ((value.callbacks ?? []).some((v) => typeof v !== "string"))
    throw new Error("callbacks 只能包含字符串 ID");
  return value;
}
export function checkTranslations(value) {
  if (!object(value) || Object.values(value).some((v) => typeof v !== "string"))
    throw new Error("翻译表必须是字符串到字符串的对象");
  return value;
}
