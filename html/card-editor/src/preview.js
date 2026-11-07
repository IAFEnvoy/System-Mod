import { object, own, pathKey, pretty } from "./utils.js";
import { getDescriptor } from "./schema.js";
import { languages, sharedCSS } from "./resources.js";
import previewCSS from "./styles/preview.css?raw";

// Keep iframe DOM and local interaction state separate from the editor document.
export function createPreview({ frame, getState, select, log }) {
  let loaded = false,
    renderTimer,
    model,
    contextData = {},
    translations = {};
  const local = new Map();
  const mode = () => getState().mode;
  // Preview data stays flat like CardContext; mode flags override user samples just like CardPresentation.
  function parameters() {
    return previewParameters;
  }
  function binding(reference, indices = []) {
    let key = String(reference ?? "");
    if (key.startsWith("{{") && key.endsWith("}}")) key = key.slice(2, -2);
    for (const index of indices) key = key.replace("*", String(index));
    return key;
  }
  function param(reference, indices = []) {
    return parameters()[binding(reference, indices)];
  }
  function state(reference, indices = []) {
    const key = binding(reference, indices);
    if (["full", "simple", "notification", "text_items"].includes(key))
      return key === mode() ? 1 : 0;
    if (own(contextData.states ?? {}, key)) return contextData.states[key];
    const value = parameters()[key];
    if (typeof value === "boolean") return +value;
    if (typeof value === "number") return value;
    if (typeof value === "string") return +!!value.trim();
    if (Array.isArray(value)) return value.length;
    return value === undefined ? 0 : 1;
  }
  function enabled(reference, indices = []) {
    return !reference || state(reference, indices) !== 0;
  }
  function tr(key, args = []) {
    const text =
      translations[key] ?? languages[getState().language]?.[key] ?? key;
    let next = 0;
    return text.replace(/%(?:(\d+)\$)?([sd%])/g, (whole, index, type) =>
      type === "%"
        ? "%"
        : String(args[index ? Number(index) - 1 : next++] ?? whole),
    );
  }
  function value(text, indices = []) {
    return String(text ?? "").replace(
      /\{\{([a-z0-9_.*:-]+)}}/g,
      (whole, key) => {
        if (key.startsWith("tr:")) return tr(key.slice(3));
        if (key.startsWith("field:")) return tr("system.field." + key.slice(6));
        if (key.startsWith("bool:"))
          return tr(
            enabled(key.slice(5), indices) ? "system.ui.yes" : "system.ui.no",
          );
        if (key.startsWith("state:"))
          return tr(
            "system.state." +
              String(param(key.slice(6), indices) ?? "").toLowerCase(),
          );
        const result = param(key, indices);
        return ["string", "number", "boolean"].includes(typeof result)
          ? String(result)
          : whole;
      },
    );
  }
  function nodeText(node, indices = []) {
    return node.translate
      ? tr(
          node.translate,
          (node.args ?? []).map((arg) => value(arg, indices)),
        )
      : value(node.text ?? "", indices);
  }
  function array(reference, indices = []) {
    const result = param(reference, indices);
    return Array.isArray(result) ? result : [];
  }
  function schedulePreview() {
    clearTimeout(renderTimer);
    renderTimer = setTimeout(renderPreview, 120);
  }
  let doc,
    previewParameters = {},
    collectionSelections = new Map(),
    collectionScopes = [];
  function element(tag, css = "", text) {
    const e = doc.createElement(tag);
    e.className = classNames(css).join(" ");
    if (text !== undefined) e.textContent = String(text);
    return e;
  }
  function textElement(tag, css, text) {
    const e = element(tag, css);
    legacy(e, text);
    return e;
  }
  function legacy(parent, text) {
    const colors = [
      "#000000",
      "#0000aa",
      "#00aa00",
      "#00aaaa",
      "#aa0000",
      "#aa00aa",
      "#ffaa00",
      "#aaaaaa",
      "#555555",
      "#5555ff",
      "#55ff55",
      "#55ffff",
      "#ff5555",
      "#ff55ff",
      "#ffff55",
      "#ffffff",
    ];
    const source = String(text ?? "").replace(
      /(^|[^\\])&([0-9a-fk-or])/gi,
      "$1§$2",
    );
    let style = {},
      last = 0;
    const regex = /§x(?:§[\da-f]){6}|§[0-9a-fk-or]|\n/gi;
    let match;
    function append(text) {
      if (!text) return;
      const span = element("span", "", text);
      Object.assign(span.style, style);
      parent.append(span);
    }
    while ((match = regex.exec(source))) {
      append(source.slice(last, match.index));
      const token = match[0].toLowerCase();
      last = regex.lastIndex;
      if (token === "\n") {
        parent.append(element("br"));
        style = {};
        continue;
      }
      const c = token[1];
      if (c === "r") style = {};
      else if (c === "x")
        style = { color: "#" + token.slice(2).replaceAll("§", "") };
      else if (/[0-9a-f]/.test(c)) style = { color: colors[parseInt(c, 16)] };
      else if (c === "l") style.fontWeight = "bold";
      else if (c === "o") style.fontStyle = "italic";
      else if (c === "n") style.textDecoration = "underline";
      else if (c === "m") style.textDecoration = "line-through";
    }
    append(source.slice(last));
  }
  function button(label, callback, css = "card_action") {
    const b = textElement("button", css, label);
    b.type = "button";
    b.onclick = callback;
    return b;
  }
  function simulate(label) {
    log("模拟操作：" + label + "（未执行业务、发包或打开链接）");
  }
  function mark(e, path) {
    e.dataset.path = pathKey(path);
    if (getState().outlines) e.classList.add("editor-outline");
    return e;
  }
  function highlight() {
    if (!doc) return;
    doc
      .querySelectorAll(".editor-selected")
      .forEach((e) => e.classList.remove("editor-selected"));
    doc
      .querySelectorAll("[data-path]")
      .forEach((e) =>
        e.classList.toggle(
          "editor-selected",
          e.dataset.path === pathKey(getState().selected),
        ),
      );
  }
  let remaining = 1500;
  function renderNodes(parent, nodes, path, indices = [], skip = -1) {
    if (!Array.isArray(nodes)) return;
    nodes.forEach((node, index) => {
      if (index === skip || !object(node) || remaining <= 0) return;
      const here = [...path, index],
        loops = node.repeat
          ? Math.max(0, Math.min(100, Math.trunc(state(node.repeat, indices))))
          : 1;
      for (let repeat = 0; repeat < loops && remaining > 0; repeat++) {
        const loop = node.repeat ? [...indices, repeat] : indices;
        if (
          !enabled(node.when, loop) ||
          (node.unless && enabled(node.unless, loop))
        )
          continue;
        remaining--;
        const result = renderNode(node, here, loop);
        if (result) parent.append(mark(result, here));
      }
    });
  }
  function renderNode(node, path, indices) {
    const info = getDescriptor(node),
      type = node.type?.replace(/^system:/, ""),
      css = node.style ?? info?.css ?? "editor-placeholder";
    let e;
    if (["text", "title"].includes(type)) {
      const text = nodeText(node, indices);
      if (!text.trim()) return null;
      const level = Math.max(1, Math.min(4, node.level ?? 1));
      return textElement(
        type === "title" ? "h" + level : node.inline ? "span" : "div",
        node.style ??
          (type === "title" && level > 1 ? "card_title_" + level : css),
        text,
      );
    }
    if (type === "markdown") {
      e = element("div", css);
      markdown(e, value(node.text ?? "{{summary}}", indices));
      return e;
    }
    if (type === "container") {
      e = element("div", css);
      renderNodes(e, node.children, [...path, "children"], indices);
      return e.children.length ? e : null;
    }
    if (type === "grid" || type === "flex")
      return collection(node, path, indices, css, type === "grid");
    if (type === "groups") return groupsNode(node, path, indices, css);
    if (type === "divider") return element("hr", css);
    if (type === "details") {
      e = element("details", css);
      const key = statePath(path, indices);
      e.open = local.get(key) ?? !!node.open;
      e.ontoggle = () => local.set(key, e.open);
      e.append(
        textElement(
          "summary",
          "card_details_toggle",
          value(node.summary, indices),
        ),
      );
      const body = element("div", "card_details_body");
      renderNodes(body, node.children, [...path, "children"], indices);
      e.append(body);
      return e;
    }
    if (type === "link")
      return button(
        value(node.text || node.url, indices),
        () => simulate(value(node.url, indices)),
        css,
      );
    if (type === "button") {
      const click = value(node.click, indices),
        val = value(node.value, indices),
        key = "button:" + val;
      e =
        mode() === "simple"
          ? element("div", css)
          : button(
              "",
              (event) => {
                event.stopPropagation();
                if (click === "local") {
                  local.set(key, !local.get(key));
                  renderPreview();
                }
                simulate(click + ": " + val);
              },
              css,
            );
      if (node.icon) {
        const icon = element(
          "span",
          "card_button_icon",
          value(node.icon, indices).endsWith("pin.svg") ? "📌" : "◆",
        );
        e.append(icon);
      }
      if (node.text)
        e.append(
          textElement("span", "card_control_label", value(node.text, indices)),
        );
      renderNodes(e, node.children, [...path, "children"], indices);
      e.disabled =
        (!!node.disabled && enabled(node.disabled, indices)) ||
        !["local", "link", "return-val"].includes(click);
      e.setAttribute(
        "aria-pressed",
        String(
          node.binding
            ? !!local.get(key)
            : !!node.selected && enabled(node.selected, indices),
        ),
      );
      if (node.label) e.title = value(node.label, indices);
      return e;
    }
    if (type === "image") {
      e = element(
        "div",
        "editor-native",
        value(node.alt ?? node.source, indices),
      );
      e.style.width = (node.width ?? 32) + "px";
      e.style.height = (node.height ?? 32) + "px";
      e.title = "游戏内本地图片：" + value(node.source, indices);
      return e;
    }
    if (type === "item") {
      const item = param(node.item, indices);
      if (!item) return null;
      e = itemPreview(item);
      e.className += " " + classNames(css).join(" ");
      e.title += "\n" + flatten(node.tooltip, indices);
      return e;
    }
    if (type === "ftb_icon") {
      const icon = param(node.icon, indices);
      if (!icon) return null;
      e = element("div", css);
      const image = element("span", "editor-icon", "◆");
      image.title =
        component(icon.title ?? icon.preview?.title) + " · FTB 原生图标占位";
      e.append(image);
      if (icon.preview?.amount)
        e.append(element("span", "quest_icon_amount", icon.preview.amount));
      return e;
    }
    if (type === "progress") {
      const n = param(node.value, indices);
      if (typeof n !== "number" || !Number.isFinite(n)) return null;
      const amount = Math.max(0, Math.min(100, n)),
        key = "progress:" + binding(node.value, indices),
        previous = local.get(key) ?? 0;
      local.set(key, amount);
      e = element("div", css);
      const bar = element("div", "card_progress_fill");
      bar.style.transformOrigin = "0 0";
      bar.style.transform = `scaleX(${amount / 100})`;
      if (previous !== amount)
        bar.animate(
          [
            { transform: `scaleX(${previous / 100})` },
            { transform: `scaleX(${amount / 100})` },
          ],
          { duration: 350, easing: "cubic-bezier(0.33,1,0.68,1)" },
        );
      e.title = amount + "%";
      e.append(bar);
      return e;
    }
    if (type === "player") {
      e = element("div", [...classNames(css), "editor-player"]);
      e.textContent = "玩家模型";
      e.title = `UUID: ${param(node.player ?? "{{player_id}}", indices) ?? "未提供"}`;
      return e;
    }
    if (type === "player_information") {
      e = element("div", css);
      array("player_information")
        .filter((r) => r.importance > (node.importance ?? 0))
        .forEach((r) =>
          (r.lines ?? []).forEach((line) =>
            e.append(textElement("div", "card_body", component(line))),
          ),
        );
      return e;
    }
    if (type === "recipe") return recipeNode(param(node.recipe, indices), css);
    return element(
      "div",
      "editor-placeholder",
      `未知节点 ${node.type} · 数据已保留`,
    );
  }
  function classNames(value) {
    return Array.isArray(value)
      ? value.flatMap(classNames)
      : String(value ?? "")
          .split(/\s+/)
          .filter(Boolean);
  }
  function component(value) {
    if (typeof value === "string") return value;
    if (!value) return "";
    return (
      (value.text ??
        (value.translate
          ? tr(value.translate, (value.with ?? []).map(component))
          : "")) + (value.extra ?? []).map(component).join("")
    );
  }
  function itemId(preview) {
    const item = preview?.item ?? preview;
    return typeof item === "string"
      ? item
      : (item?.id ?? item?.item?.id ?? "unknown:item");
  }
  function itemPreview(preview) {
    const id = itemId(preview),
      e = element("span", "editor-item");
    e.append(
      element("span", "editor-item-icon", "◇"),
      element("span", "", id.replace(/^minecraft:/, "")),
      element("small", "", preview?.amount ?? preview?.count ?? "1"),
    );
    e.title = `${id}\n${pretty(preview)}`;
    return e;
  }
  function page(parent, entries, size, key, draw, always = true) {
    size = Math.max(1, Math.trunc(Number(size) || 1));
    const total = Math.max(1, Math.ceil(entries.length / size)),
      current = Math.max(0, Math.min(total - 1, local.get(key) ?? 0));
    local.set(key, current);
    const body = element("div");
    draw(body, entries.slice(current * size, (current + 1) * size));
    parent.append(body);
    if (!always && total === 1) return;
    const controls = element("div", "card_pagination"),
      prev = button(tr("system.ui.card_previous_page"), () => {
        local.set(key, current - 1);
        renderPreview();
      }),
      next = button(tr("system.ui.card_next_page"), () => {
        local.set(key, current + 1);
        renderPreview();
      });
    prev.disabled = current === 0;
    next.disabled = current === total - 1;
    controls.append(
      prev,
      element(
        "span",
        "card_page_label",
        tr("system.ui.card_page", [current + 1, total]),
      ),
      next,
    );
    parent.append(controls);
  }
  function statePath(path, indices) {
    return pathKey(path) + JSON.stringify(indices) + collectionScopes.join("/");
  }
  function collectionIndices(node, indices) {
    const source = binding(node.source, indices),
      count = Math.max(0, Math.trunc(state(source, indices)));
    return (
      collectionSelections.get(source) ??
      Array.from({ length: Math.min(count, 10000) }, (_, i) => i)
    ).filter((i) => enabled(node.filter, [...indices, i]));
  }
  function collection(node, path, indices, css, grid) {
    const dynamic = !!node.source,
      rows = dynamic ? collectionIndices(node, indices) : [];
    if (dynamic && !rows.length) return null;
    const root = element("div"),
      cols = Math.max(
        1,
        mode() === "full"
          ? (node.columns ?? 6)
          : Math.min(node.columns ?? 6, node.compact_columns ?? 3),
      );
    const count = Math.max(1, node.max_row ?? 3),
      height = Math.max(1, node.cell ?? 104);
    const visibleRows =
      dynamic && rows.length <= cols * count
        ? Math.ceil(rows.length / cols)
        : count;
    const draw = (parent, slice) => {
      parent.className = classNames([css, grid ? "card_page_grid" : ""]).join(
        " ",
      );
      parent.style.display = grid ? "grid" : "flex";
      if (grid) {
        parent.style.gridTemplateColumns = `repeat(${cols},minmax(0,1fr))`;
        if (dynamic) {
          parent.style.gridTemplateRows = `repeat(${visibleRows},${height}px)`;
          parent.style.height = parent.style.minHeight =
            visibleRows * height + (visibleRows - 1) * 6 + 22 + "px";
        }
      } else {
        parent.style.flexDirection =
          node.direction === "column" ? "column" : "row";
        parent.style.flexWrap = node.wrap === false ? "nowrap" : "wrap";
      }
      if (!dynamic)
        renderNodes(parent, node.children, [...path, "children"], indices);
      else
        for (const index of slice) {
          const cell = grid ? element("div", "card_grid_cell") : parent;
          if (grid) cell.style.height = height + "px";
          renderNodes(
            cell,
            node.children,
            [...path, "children"],
            [...indices, index],
          );
          if (grid) parent.append(cell);
        }
    };
    if (dynamic && (grid || node.page_size > 0))
      page(
        root,
        rows,
        grid ? cols * count : node.page_size,
        statePath(path, indices),
        draw,
      );
    else draw(root, rows);
    return root;
  }
  function groupsNode(node, path, indices, css) {
    const rows = collectionIndices(node, indices),
      groups = new Map(
        (node.options ?? []).map((option) => [
          option.id,
          {
            label: value(option.label, indices),
            rows: [],
            section: "",
            sectionLabel: "",
          },
        ]),
      );
    for (const index of rows) {
      const loop = [...indices, index],
        id = value(node.group, loop),
        label = value(node.group_label, loop) || tr("system.ui.card_ungrouped");
      if (!groups.has(id))
        groups.set(id, {
          label,
          rows: [],
          section: value(node.section, loop),
          sectionLabel: value(node.section_label, loop),
        });
      groups.get(id).rows.push(index);
    }
    const key = statePath(path, indices);
    let chosen = local.get(key);
    if (!groups.get(chosen)?.rows.length)
      chosen = [...groups].find(([, group]) => group.rows.length)?.[0];
    if (chosen === undefined) return null;
    const horizontal = node.orientation === "horizontal",
      root = element("div", [css, horizontal ? "card_groups_horizontal" : ""]),
      tabs = element("div", horizontal ? "source_tabs" : "card_action_groups");
    let previous;
    for (const [id, group] of groups) {
      if (group.section !== previous && group.sectionLabel)
        tabs.append(
          textElement("div", "card_action_group_section", group.sectionLabel),
        );
      previous = group.section;
      const tab = button(
        group.label + " · " + group.rows.length,
        () => {
          local.set(key, id);
          renderPreview();
        },
        (horizontal ? "card_action source_tab" : "card_action_group") +
          (id === chosen ? " selected" : ""),
      );
      tab.disabled = !group.rows.length;
      tabs.append(tab);
    }
    const content = element("div", "card_action_group_results"),
      source = binding(node.source, indices),
      previousSelection = collectionSelections.get(source);
    collectionSelections.set(source, groups.get(chosen).rows);
    collectionScopes.push(chosen);
    renderNodes(content, node.children, [...path, "children"], indices);
    collectionScopes.pop();
    if (previousSelection === undefined) collectionSelections.delete(source);
    else collectionSelections.set(source, previousSelection);
    root.append(tabs, content);
    return root;
  }
  function flatten(nodes, indices = []) {
    return (nodes ?? [])
      .flatMap((node) => {
        const count = node.repeat
          ? Math.max(0, Math.min(100, Math.trunc(state(node.repeat, indices))))
          : 1;
        return Array.from({ length: count }, (_, i) => {
          const loop = node.repeat ? [...indices, i] : indices;
          return enabled(node.when, loop) &&
            !(node.unless && enabled(node.unless, loop))
            ? node.children
              ? flatten(node.children, loop)
              : nodeText(node, loop)
            : "";
        });
      })
      .filter(Boolean)
      .join(" ");
  }
  function recipeNode(source, css) {
    if (!source) return null;
    const root = element("div", css);
    root.append(
      element("div", "source_heading", source.type ?? source.kind),
      element("div", "card_context source_id", source.id),
    );
    const recipe = element("div", "source_recipe"),
      inputs = element("div", "source_slots");
    let slots = source.inputs ?? [];
    if (
      source.width > 0 &&
      source.width <= 3 &&
      slots.length <= source.width * 3
    ) {
      slots = Array.from({ length: 9 }, (_, i) =>
        i % 3 < source.width
          ? slots[Math.floor(i / 3) * source.width + (i % 3)]
          : null,
      );
      inputs.style.display = "grid";
      inputs.style.gridTemplateColumns = "repeat(3,36px)";
    }
    for (const slot of slots) {
      const cell = element("div", "source_slot");
      if (slot?.items?.length)
        cell.append(
          itemPreview({ item: slot.items[0], amount: String(slot.count ?? 1) }),
        );
      inputs.append(cell);
    }
    recipe.append(inputs, element("span", "source_arrow", "→"));
    for (const slot of source.outputs ?? [])
      if (slot.items?.length)
        recipe.append(
          itemPreview({ item: slot.items[0], amount: String(slot.count ?? 1) }),
        );
    root.append(recipe);
    if (source.notes?.length)
      root.append(element("div", "card_context", source.notes.join(" · ")));
    return root;
  }
  function prepareContext() {
    previewParameters = {};
    collectionSelections.clear();
    collectionScopes = [];
    const put = (key, value) => {
      previewParameters[key] = value;
      if (Array.isArray(value))
        value.forEach((entry, i) => {
          put(key + "." + i, entry);
          if (entry?.item)
            previewParameters[key + "." + i + ".label"] =
              itemId(entry) + " × " + (entry.amount ?? 1);
        });
    };
    Object.entries(contextData.parameters ?? {}).forEach(([key, value]) =>
      put(key, value),
    );
    Object.entries(contextData.parameters ?? {}).forEach(([key, value]) => {
      if (!Array.isArray(value)) previewParameters[key] = value;
    });
    previewParameters.show_action_numbers =
      mode() === "simple" && model.actions?.numbered !== false;
    for (let i = 0; i < (previewParameters.actions ?? 0); i++) {
      previewParameters[`actions.${i}.visible`] =
        mode() !== "notification" ||
        (previewParameters.result_actions?.[i]?.placement ?? "footer") ===
          "body";
    }
  }
  // A deliberately small, text-only Markdown projection; HTML and external resources are never inserted.
  function markdown(parent, source) {
    let fence = false,
      code = [],
      list = null;
    function inline(container, text) {
      const re = /(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^)]+\))/g;
      let last = 0,
        m;
      while ((m = re.exec(text))) {
        container.append(doc.createTextNode(text.slice(last, m.index)));
        const token = m[0];
        if (token.startsWith("`"))
          container.append(element("code", "", token.slice(1, -1)));
        else if (token.startsWith("**"))
          container.append(element("strong", "", token.slice(2, -2)));
        else {
          const parts = /\[([^\]]+)\]\(([^)]+)\)/.exec(token);
          container.append(
            button(parts[1], () => simulate(parts[2]), "card_link"),
          );
        }
        last = re.lastIndex;
      }
      container.append(doc.createTextNode(text.slice(last)));
    }
    for (const line of source.split("\n")) {
      if (/^\s*```/.test(line)) {
        if (fence) {
          const pre = element("pre");
          pre.append(element("code", "", code.join("\n")));
          parent.append(pre);
          code = [];
        }
        fence = !fence;
        continue;
      }
      if (fence) {
        code.push(line);
        continue;
      }
      if (!line.trim()) {
        list = null;
        continue;
      }
      const heading = /^(#{1,6})\s+(.*)/.exec(line),
        item = /^\s*(?:[-*+]\s+|\d+\.\s+)(.*)/.exec(line);
      if (item) {
        if (!list) {
          list = element("ul");
          parent.append(list);
        }
        const li = element("li");
        inline(li, item[1]);
        list.append(li);
        continue;
      }
      list = null;
      const block = element(
        heading
          ? "h" + heading[1].length
          : line.startsWith("> ")
            ? "blockquote"
            : "p",
      );
      inline(block, heading ? heading[2] : line.replace(/^> /, ""));
      parent.append(block);
    }
    if (fence) parent.append(element("pre", "", code.join("\n")));
  }
  function renderPreview() {
    ({ model, contextData, translations } = getState());
    if (!loaded || !model) return;
    doc = frame.contentDocument;
    if (!doc) return;
    const scroll = doc.scrollingElement?.scrollTop ?? 0;
    remaining = 1500;
    try {
      prepareContext();
      doc.head.querySelector("style").textContent =
        sharedCSS + "\n" + previewCSS + "\n" + getState().css;
      doc.body.className =
        mode() === "notification"
          ? "notification interactive"
          : mode() === "simple"
            ? "simple"
            : "";
      doc.body.replaceChildren();
      const workspace = element("div");
      workspace.id = "workspace";
      const card = element("div", model.style ?? "result_card");
      workspace.append(card);
      doc.body.append(workspace);
      workspace.addEventListener(
        "click",
        (event) => {
          if (!event.altKey) return;
          const target = event.target.closest("[data-path]");
          if (!target) return;
          event.preventDefault();
          event.stopPropagation();
          select(JSON.parse(target.dataset.path));
        },
        true,
      );
      const hoisted = model.body.findIndex(
        (n) =>
          n.type === "system:title" &&
          (n.level ?? 1) === 1 &&
          !n.repeat &&
          enabled(n.when) &&
          !(n.unless && enabled(n.unless)) &&
          nodeText(n).trim(),
      );
      const summary = String(parameters().summary ?? "");
      const single =
        model.body.length === 1 &&
        model.body[0].type === "system:text" &&
        !model.body[0].repeat;
      const plain =
        single &&
        !array("result_actions").length &&
        !(model.show_title !== false && state("show_card_title")) &&
        (!summary.trim() || summary === flatten(model.body));
      if (plain) {
        card.className = "message_text";
        renderNodes(card, model.body, ["body"]);
      } else {
        if (model.show_title !== false) {
          if (hoisted >= 0)
            card.append(
              mark(
                textElement("h1", "card_title", nodeText(model.body[hoisted])),
                ["body", hoisted],
              ),
            );
          else if (state("show_card_title"))
            card.append(
              textElement(
                "h1",
                "card_title",
                String(parameters().card_title ?? ""),
              ),
            );
        }
        const summaryInBody = model.body.some(
          (n) => n.type === "system:text" && nodeText(n) === summary,
        );
        if (
          hoisted < 0 &&
          !model.hide_summary &&
          !summaryInBody &&
          summary.trim()
        )
          card.append(textElement("div", "message_text", summary));
        renderNodes(card, model.body, ["body"], [], hoisted);
        if (single)
          renderNodes(
            card,
            [
              {
                type: "system:flex",
                source: "{{actions}}",
                filter: "{{actions.*.visible}}",
                style: "card_actions",
                children: [
                  {
                    type: "system:button",
                    text: "{{actions.*.label}}",
                    click: "{{actions.*.click_type}}",
                    value: "{{actions.*.value}}",
                  },
                ],
              },
            ],
            ["automatic_actions"],
          );
      }
      if (mode() === "notification") {
        const footer = element("div", "editor-hud-actions");
        footer.append(button("确定", () => simulate("通知确定")));
        for (const a of array("result_actions")
          .filter((a) => (a.placement ?? "footer").toLowerCase() !== "body")
          .slice(0, 3))
          footer.append(button(a.label ?? a.id, () => simulate(a.id)));
        workspace.append(footer);
      }
      if (remaining <= 0)
        doc.body.append(
          element(
            "p",
            "editor-placeholder",
            "预览达到 1500 节点上限，后续节点未绘制；导出不会截断 JSON。",
          ),
        );
      frame.style.width = (Number(getState().width) || 840) + "px";
      doc.scrollingElement.scrollTop = scroll;
      highlight();
    } catch (error) {
      log("预览错误：" + error.message);
    }
  }

  function mount() {
    frame.addEventListener("load", () => {
      loaded = true;
      renderPreview();
    });
    frame.srcdoc =
      "<!doctype html><html><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><style></style></head><body></body></html>";
  }

  return {
    mount,
    schedule: schedulePreview,
    render: renderPreview,
    highlight,
    reset: () => local.clear(),
  };
}
