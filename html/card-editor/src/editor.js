import {
  $,
  clone,
  pretty,
  object,
  own,
  at,
  pathKey,
  displayPath,
} from "./utils.js";
import {
  catalog,
  common,
  rootFields,
  getDescriptor,
  validate,
  checkContext,
  checkTranslations,
} from "./schema.js";
import { templates } from "./resources.js";
import { sampleContext } from "./sample-context.js";
import { createPreview } from "./preview.js";

const STORAGE = "system.card-editor.v2";
let model,
  selected = [],
  contextData = {},
  translations = {},
  errors = [],
  contextError = "",
  translationError = "",
  filename = "card.json";
let history = [],
  historyIndex = -1,
  dirty = false,
  parseTimer,
  draftTimer,
  propertySource;
const canonical = () => pretty(model);
const log = (text) => {
  $("preview-log").textContent = text;
};

const preview = createPreview({
  frame: $("preview"),
  select,
  log,
  getState: () => ({
    model,
    selected,
    contextData,
    translations,
    mode: $("mode").value,
    language: $("language").value,
    outlines: $("outlines").checked,
    width: $("preview-width").value,
    css: $("custom-css").value,
  }),
});
const { schedule: schedulePreview, render: renderPreview, highlight } = preview;

function status() {
  $("issues").replaceChildren();
  for (const problem of errors) {
    const li = document.createElement("li");
    li.textContent = `${problem.severity === "error" ? "错误" : "提示"} · ${displayPath(problem.path)}：${problem.message}`;
    li.onclick = () => {
      let path = problem.path;
      while (path.length && !object(at(model, path))) path = path.slice(0, -1);
      select(path);
    };
    $("issues").append(li);
  }
  const fatal = errors.some((e) => e.severity === "error"),
    extra = [contextError, translationError].filter(Boolean);
  $("status").className = fatal || extra.length ? "error" : "";
  $("status").textContent = fatal
    ? "JSON 无效：预览保留上一份有效布局，树操作与导出已暂停。"
    : extra.length
      ? extra.join(" · ")
      : errors.length
        ? `JSON 有效 · ${errors.length} 条提示`
        : `JSON 有效 · ${catalog.size} 种内置节点 · ${filename}`;
  for (const id of [
    "download",
    "save-project",
    "format",
    "add",
    "up",
    "down",
    "duplicate",
    "remove",
  ])
    $(id).disabled = fatal;
  if (!fatal) {
    for (const id of ["up", "down", "duplicate", "remove"])
      $(id).disabled = !selected.length || typeof selected.at(-1) !== "number";
  }
  $("save-project").disabled ||= !!extra.length;
  $("undo").disabled = historyIndex <= 0;
  $("redo").disabled = historyIndex >= history.length - 1;
}

function pushHistory() {
  const source = canonical();
  if (history[historyIndex] === source) return;
  history = history.slice(0, historyIndex + 1);
  history.push(source);
  if (history.length > 80) history.shift();
  historyIndex = history.length - 1;
}
function commit(next, path = selected) {
  if (errors.some((e) => e.severity === "error")) return;
  errors = validate(next);
  $("source").value = pretty(next);
  dirty = true;
  if (errors.some((e) => e.severity === "error")) {
    status();
    showTab("json");
    return;
  }
  model = next;
  selected = at(model, path) === undefined ? [] : path;
  pushHistory();
  update();
}
function update() {
  renderTree();
  renderProperties();
  status();
  schedulePreview();
  scheduleDraft();
}
function parseSource() {
  clearTimeout(parseTimer);
  try {
    const next = JSON.parse($("source").value),
      problems = validate(next);
    errors = problems;
    if (!problems.some((e) => e.severity === "error")) {
      const changed = pretty(next) !== canonical();
      model = next;
      if (at(model, selected) === undefined) selected = [];
      if (changed) {
        dirty = true;
        renderTree();
        renderProperties();
        schedulePreview();
      }
      pushHistory();
    }
  } catch (error) {
    errors = [{ path: [], message: error.message, severity: "error" }];
  }
  status();
  scheduleDraft();
}
function select(path) {
  if (errors.some((e) => e.severity === "error")) return;
  selected = at(model, path) === undefined ? [] : path;
  renderTree();
  renderProperties();
  status();
  showTab("props");
  highlight();
}
function renderTree() {
  const root = $("tree");
  root.replaceChildren();
  const row = (path, title, depth) => {
    const b = document.createElement("button");
    b.textContent = title;
    b.style.paddingLeft = 10 + depth * 14 + "px";
    b.title = displayPath(path);
    b.className = pathKey(path) === pathKey(selected) ? "selected" : "";
    b.onclick = () => select(path);
    b.onpointerdown = (event) => {
      if (event.button !== 0) return;
      event.preventDefault();
      parseSource();
      select(path);
    };
    root.append(b);
  };
  function walk(list, path, depth) {
    if (!Array.isArray(list)) return;
    list.forEach((node, index) => {
      const here = [...path, index];
      if (!object(node)) return;
      const info = getDescriptor(node),
        label = node.text || node.translate || node.summary || node.items || "";
      row(
        here,
        `${info?.name ?? node.type} ${String(label).slice(0, 28)}`,
        depth,
      );
      for (const slot of info?.slots ?? []) {
        if (!Array.isArray(node[slot])) continue;
        const heading = document.createElement("div");
        heading.className = "slot";
        heading.style.paddingLeft = 25 + depth * 14 + "px";
        heading.textContent = slot;
        root.append(heading);
        walk(node[slot], [...here, slot], depth + 1);
      }
    });
  }
  row([], "卡片根 / 显示设置", 0);
  walk(model.body, ["body"], 1);
  if (Array.isArray(model.actions?.label)) {
    row(["actions"], "actions / 文字选择标签", 0);
    walk(model.actions.label, ["actions", "label"], 1);
  }
}
function fieldControl(key, spec, value, optional) {
  const [raw, description] = spec,
    type = raw.replace("!", "");
  const label = document.createElement("label");
  label.className = "field";
  const title = document.createElement("span");
  title.textContent = key + (raw.endsWith("!") ? " *" : "");
  label.append(title);
  let input;
  if (type === "boolean") {
    input = document.createElement("select");
    for (const [text, v] of [
      ["默认 / 不设置", ""],
      ["true", "true"],
      ["false", "false"],
    ])
      input.add(new Option(text, v));
    input.value = value === undefined ? "" : String(value);
  } else if (["style", "strings", "json"].includes(type)) {
    input = document.createElement("textarea");
    input.value = value === undefined ? "" : pretty(value);
    input.placeholder =
      type === "style"
        ? '"card_body" 或 ["card_body", "custom"]'
        : '["{{title}}"]';
  } else {
    input = document.createElement("input");
    input.type = ["integer", "number"].includes(type) ? "number" : "text";
    if (input.type === "number") input.step = type === "integer" ? "1" : "any";
    input.value = value ?? "";
  }
  input.addEventListener("change", () => {
    if (errors.some((e) => e.severity === "error")) return;
    try {
      const next = clone(model),
        target = at(next, selected),
        value = input.value;
      if (optional && value === "") delete target[key];
      else
        target[key] =
          type === "boolean"
            ? value === "true"
            : ["style", "strings", "json"].includes(type)
              ? JSON.parse(value)
              : ["integer", "number"].includes(type)
                ? Number(value)
                : value;
      commit(next);
    } catch (error) {
      log("属性未写入：" + error.message);
    }
  });
  label.append(input);
  const hint = document.createElement("span");
  hint.className = "description";
  hint.textContent = description ?? "";
  label.append(hint);
  return label;
}
function renderProperties() {
  const parent = $("properties"),
    value = at(model, selected);
  parent.replaceChildren();
  if (!object(value)) return;
  const title = document.createElement("h3");
  title.textContent = displayPath(selected);
  parent.append(title);
  if (typeof value.type === "string") {
    const field = document.createElement("label");
    field.className = "field";
    const label = document.createElement("span");
    label.textContent = "type";
    field.append(label);
    const input = document.createElement("input");
    input.value = value.type;
    input.setAttribute("list", "registered-types");
    input.onchange = () => {
      const next = clone(model);
      at(next, selected).type = input.value;
      commit(next);
    };
    field.append(input);
    parent.append(field);
  }
  const fields = !selected.length
    ? rootFields
    : selected.length === 1 && selected[0] === "actions"
      ? { numbered: ["boolean", "是否显示编号"] }
      : { ...common, ...getDescriptor(value)?.fields };
  for (const [key, spec] of Object.entries(fields))
    parent.append(fieldControl(key, spec, value[key], !spec[0].endsWith("!")));
  const detail = document.createElement("details");
  const summary = document.createElement("summary");
  summary.textContent = "完整对象 JSON（含子节点与自定义字段）";
  detail.append(summary);
  propertySource = document.createElement("textarea");
  propertySource.className = "subcode";
  propertySource.spellcheck = false;
  propertySource.value = pretty(value);
  detail.append(propertySource);
  const apply = document.createElement("button");
  apply.textContent = "应用对象 JSON";
  apply.onclick = () => {
    try {
      const parsed = JSON.parse(propertySource.value);
      if (!object(parsed)) throw new Error("对象不能为空或数组");
      const next = clone(model);
      if (selected.length)
        at(next, selected.slice(0, -1))[selected.at(-1)] = parsed;
      commit(selected.length ? next : parsed);
    } catch (error) {
      log("对象未写入：" + error.message);
    }
  };
  detail.append(apply);
  parent.append(detail);
}
function showTab(tab) {
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  document
    .querySelectorAll("[data-panel]")
    .forEach((p) => p.classList.toggle("active", p.dataset.panel === tab));
}
function editList(operation) {
  if (!selected.length || typeof selected.at(-1) !== "number") return;
  const next = clone(model),
    list = at(next, selected.slice(0, -1)),
    index = selected.at(-1);
  let path = selected;
  if (operation === "remove") {
    list.splice(index, 1);
    path =
      index < list.length
        ? selected
        : index
          ? selected.slice(0, -1).concat(index - 1)
          : [];
  }
  if (operation === "duplicate") {
    list.splice(index + 1, 0, clone(list[index]));
    path = selected.slice(0, -1).concat(index + 1);
  }
  if (operation === "up" && index > 0) {
    [list[index - 1], list[index]] = [list[index], list[index - 1]];
    path = selected.slice(0, -1).concat(index - 1);
  }
  if (operation === "down" && index < list.length - 1) {
    [list[index], list[index + 1]] = [list[index + 1], list[index]];
    path = selected.slice(0, -1).concat(index + 1);
  }
  commit(next, path);
}
function addDialog() {
  const target = $("insert-target");
  target.replaceChildren();
  const option = (name, path, index = null) =>
    target.add(new Option(name, JSON.stringify({ path, index })));
  if (typeof selected.at(-1) === "number")
    option("选中节点之后", selected.slice(0, -1), selected.at(-1) + 1);
  for (const slot of getDescriptor(at(model, selected))?.slots ?? [])
    option(`追加到选中节点 ${slot}`, [...selected, slot]);
  option("追加到 body", ["body"]);
  option("追加到 actions.label", ["actions", "label"]);
  $("add-dialog").showModal();
}
function insertNode() {
  const { path, index } = JSON.parse($("insert-target").value),
    next = clone(model);
  let parent = next;
  for (const key of path.slice(0, -1)) {
    if (!own(parent, key)) parent[key] = {};
    parent = parent[key];
  }
  if (!Array.isArray(parent[path.at(-1)])) parent[path.at(-1)] = [];
  const list = parent[path.at(-1)],
    offset = index ?? list.length;
  list.splice(offset, 0, clone(catalog.get($("node-type").value).defaults));
  commit(next, [...path, offset]);
  $("add-dialog").close();
  showTab("props");
}

function parseContext() {
  try {
    contextData = checkContext(JSON.parse($("context").value));
    contextError = "";
    preview.reset();
    schedulePreview();
    scheduleDraft();
  } catch (error) {
    contextError = "上下文错误：" + error.message;
  }
  status();
}
function parseTranslations() {
  try {
    translations = checkTranslations(JSON.parse($("translations").value));
    translationError = "";
    schedulePreview();
    scheduleDraft();
  } catch (error) {
    translationError = "文案错误：" + error.message;
  }
  status();
}
function snapshot() {
  return {
    format: "system-card-editor",
    version: 1,
    filename: $("filename").value,
    card: model,
    context: contextData,
    translations,
    css: $("custom-css").value,
  };
}
function scheduleDraft() {
  clearTimeout(draftTimer);
  draftTimer = setTimeout(() => {
    if (
      errors.some((e) => e.severity === "error") ||
      contextError ||
      translationError
    )
      return;
    try {
      localStorage.setItem(STORAGE, pretty(snapshot()));
      $("draft-status").textContent = "有效草稿已保存到本机";
    } catch {
      $("draft-status").textContent = "本地草稿不可用，请导出工作文件";
    }
  }, 500);
}
function load(
  card,
  name,
  context = sampleContext(name.replace(/\.json$/i, "")),
  customTranslations = {},
  css = "",
  markDirty = false,
) {
  const problems = validate(card);
  if (problems.some((e) => e.severity === "error"))
    throw new Error(
      problems.map((p) => displayPath(p.path) + " " + p.message).join("\n"),
    );
  checkContext(context);
  checkTranslations(customTranslations);
  if (typeof css !== "string") throw new Error("预览 CSS 应为字符串");
  model = clone(card);
  filename = name;
  $("filename").value = name;
  selected = [];
  errors = problems;
  contextData = clone(context);
  translations = clone(customTranslations);
  contextError = "";
  translationError = "";
  dirty = markDirty;
  clearTimeout(parseTimer);
  clearTimeout($("context").timer);
  clearTimeout($("translations").timer);
  $("source").value = pretty(model);
  $("context").value = pretty(contextData);
  $("translations").value = pretty(translations);
  $("custom-css").value = css;
  history = [];
  historyIndex = -1;
  preview.reset();
  pushHistory();
  update();
}
function discard() {
  parseSource();
  return (
    !dirty ||
    confirm(
      "当前卡片有编辑。载入新内容会替换工作区；需要保留时请先导出工作文件。继续？",
    )
  );
}
function download(name, value) {
  const url = URL.createObjectURL(
    new Blob([typeof value === "string" ? value : pretty(value) + "\n"], {
      type: "application/json;charset=utf-8",
    }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportCard() {
  parseSource();
  if (errors.some((e) => e.severity === "error")) return;
  filename = $("filename").value.trim() || "card.json";
  if (!filename.toLowerCase().endsWith(".json")) filename += ".json";
  download(filename, model);
  dirty = false;
  status();
}
async function openFile(file) {
  if (!file) return;
  if (file.size > 4 * 1024 * 1024) {
    log("文件超过 4 MB，请确认选择的是卡片 JSON。");
    return;
  }
  try {
    const value = JSON.parse((await file.text()).replace(/^\uFEFF/, ""));
    if (!discard()) return;
    if (value.format === "system-card-editor") {
      if (value.version !== 1) throw new Error("不支持此工作文件版本");
      load(
        value.card,
        value.filename || "card.json",
        value.context || sampleContext(),
        value.translations || {},
        value.css || "",
      );
      parseContext();
      parseTranslations();
    } else load(value, file.name);
  } catch (error) {
    log("载入失败：" + error.message);
  } finally {
    $("file").value = "";
  }
}

export function initializeEditor() {
  for (const name of Object.keys(templates).sort())
    $("preset").add(new Option(name, name));
  $("preset").value = "quest_list";
  const datalist = document.createElement("datalist");
  datalist.id = "registered-types";
  const categories = new Map();
  for (const [id, info] of catalog) {
    datalist.append(new Option(info.name, id));
    if (!categories.has(info.category)) {
      const group = document.createElement("optgroup");
      group.label = info.category;
      categories.set(info.category, group);
      $("node-type").append(group);
    }
    categories
      .get(info.category)
      .append(new Option(`${info.name} · ${id}`, id));
  }
  document.body.append(datalist);
  $("node-type").onchange = () => {
    $("node-help").textContent = pretty(
      catalog.get($("node-type").value).defaults,
    );
  };
  $("node-type").onchange();
  $("source").addEventListener("input", () => {
    dirty = true;
    clearTimeout(parseTimer);
    parseTimer = setTimeout(parseSource, 280);
  });
  $("source").addEventListener("blur", parseSource);
  $("context").addEventListener("input", () => {
    dirty = true;
    clearTimeout($("context").timer);
    $("context").timer = setTimeout(parseContext, 280);
  });
  $("translations").addEventListener("input", () => {
    dirty = true;
    clearTimeout($("translations").timer);
    $("translations").timer = setTimeout(parseTranslations, 280);
  });
  $("custom-css").addEventListener("input", () => {
    dirty = true;
    schedulePreview();
    scheduleDraft();
  });
  document
    .querySelectorAll("[data-tab]")
    .forEach((button) => (button.onclick = () => showTab(button.dataset.tab)));
  document
    .querySelectorAll("[data-close]")
    .forEach(
      (button) => (button.onclick = () => $(button.dataset.close).close()),
    );
  $("help").onclick = () => $("help-dialog").showModal();
  $("add").onclick = addDialog;
  $("insert-node").onclick = insertNode;
  for (const action of ["up", "down", "duplicate", "remove"])
    $(action).onclick = () => editList(action);
  $("load-preset").onclick = () => {
    if (discard())
      load(templates[$("preset").value], $("preset").value + ".json");
  };
  $("new-card").onclick = () => {
    if (discard())
      load(
        {
          show_title: false,
          body: [{ type: "system:text", text: "{{summary}}" }],
        },
        "card.json",
        {
          parameters: { summary: "叮！欢迎使用系统。" },
          states: {},
          callbacks: [],
        },
      );
  };
  $("import").onclick = () => $("file").click();
  $("file").onchange = () => openFile($("file").files[0]);
  $("download").onclick = exportCard;
  $("save-project").onclick = () => {
    parseSource();
    parseContext();
    parseTranslations();
    if (
      errors.some((e) => e.severity === "error") ||
      contextError ||
      translationError
    )
      return;
    download(
      ($("filename").value || "card.json").replace(/\.json$/i, "") +
        ".card-project.json",
      snapshot(),
    );
    dirty = false;
  };
  $("format").onclick = () => {
    parseSource();
    if (!errors.some((e) => e.severity === "error"))
      $("source").value = canonical();
  };
  for (const [id, delta] of [
    ["undo", -1],
    ["redo", 1],
  ])
    $(id).onclick = () => {
      const index = historyIndex + delta;
      if (index < 0 || index >= history.length) return;
      historyIndex = index;
      model = JSON.parse(history[index]);
      selected = [];
      errors = validate(model);
      $("source").value = canonical();
      dirty = true;
      update();
    };
  for (const id of ["mode", "language", "preview-width", "outlines"])
    $(id).addEventListener("change", schedulePreview);
  $("refresh").onclick = () => {
    preview.reset();
    renderPreview();
  };
  $("filename").oninput = () => {
    dirty = true;
    filename = $("filename").value;
    scheduleDraft();
    status();
  };
  $("clear-draft").onclick = () => {
    clearTimeout(draftTimer);
    try {
      localStorage.removeItem(STORAGE);
      $("draft-status").textContent = "本地草稿已清除；继续编辑会重新保存";
    } catch {
      $("draft-status").textContent = "浏览器不支持本地存储";
    }
  };
  document.addEventListener("keydown", (event) => {
    if (!(event.ctrlKey || event.metaKey)) return;
    if (event.key.toLowerCase() === "s") {
      event.preventDefault();
      exportCard();
    }
    if (event.shiftKey && event.key.toLowerCase() === "f") {
      event.preventDefault();
      $("format").click();
    }
  });
  for (const textarea of document.querySelectorAll("textarea"))
    textarea.addEventListener("keydown", (event) => {
      if (event.key !== "Tab") return;
      event.preventDefault();
      const start = textarea.selectionStart,
        end = textarea.selectionEnd;
      textarea.setRangeText("  ", start, end, "end");
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
    });
  document.addEventListener("dragover", (event) => {
    if (!Array.from(event.dataTransfer.types).includes("Files")) return;
    event.preventDefault();
    document.body.classList.add("drop-active");
  });
  document.addEventListener("dragleave", (event) => {
    if (!event.relatedTarget) document.body.classList.remove("drop-active");
  });
  document.addEventListener("drop", (event) => {
    event.preventDefault();
    document.body.classList.remove("drop-active");
    openFile(event.dataTransfer.files[0]);
  });
  window.addEventListener("beforeunload", (event) => {
    if (dirty) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
  let draft;
  try {
    draft = JSON.parse(localStorage.getItem(STORAGE) ?? "null");
  } catch {}
  try {
    if (draft?.format === "system-card-editor" && draft.version === 1)
      load(
        draft.card,
        draft.filename ?? "card.json",
        draft.context,
        draft.translations,
        draft.css,
        true,
      );
    else load(templates.quest_list ?? { body: [] }, "quest_list.json");
  } catch {
    load(templates.quest_list ?? { body: [] }, "quest_list.json");
  }
  preview.mount();
}
