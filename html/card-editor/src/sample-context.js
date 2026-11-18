export function sampleContext(template = "quest_list") {
  const preview = (id, amount = "1") => ({ item: { id }, amount });
  const p = {
    summary: "叮！检测到宿主的新进展，请选择下一步操作。",
    card_title: "系统消息",
    title: "收集基础资源",
    notice_title: "任务完成",
    id: "5A710001",
    chapter: "资源仓库",
    source: "FTB 任务",
    description: "收集材料，为接下来的冒险做好准备。",
    progress: 65,
    answer:
      "## 获取基础资源\n先收集 **木材**，再制作工作台。\n\n- 木板可以合成木棍\n- 使用石镐获取铁矿\n\n> 完成准备后即可继续探索。",
    emotion: "system:happy",
    query: "如何获取铁锭",
    item_id: "minecraft:iron_ingot",
    player_id: "00000000-0000-0000-0000-000000000001",
    name: "Dev",
    content: "测试消息",
    system_name: "冒险系统",
    system_description: "每日为宿主提供资源与任务。",
    result_items: [
      preview("minecraft:diamond", "3"),
      preview("minecraft:iron_ingot", "16"),
    ],
    price_items: [preview("minecraft:emerald", "2")],
    reward_label: "资源补给",
    reward_items: "钻石 × 3，铁锭 × 16",
    price: "绿宝石 × 2",
    price_text: "绿宝石 × 2",
    catalog_title: "系统商城",
    reward_heading: "商品",
    cost_heading: "支付费用",
    payment_unavailable_text: "当前无法支付此费用，请检查材料和余额后重新报价。",
    show_price: true,
    has_price_description: false,
    balance: 120,
    can_claim: true,
    tasks: 2,
    "tasks.0.task_title": "收集铁锭",
    "tasks.0.task_progress": 6,
    "tasks.0.task_required": 10,
    "tasks.0.task_items": [preview("minecraft:iron_ingot", "10")],
    "tasks.0.task_icons": [],
    "tasks.1.task_title": "确认准备完成",
    "tasks.1.task_progress": 0,
    "tasks.1.task_required": 1,
    "tasks.1.task_icons": [],
    rewards: 1,
    "rewards.0.reward_title": "补给箱",
    "rewards.0.reward_state": "can_claim",
    "rewards.0.result_items": [preview("minecraft:diamond", "3")],
    "rewards.0.reward_icons": [],
    player_information: [
      { importance: 80, lines: ["宿主：Dev", "生命值：20 / 20"] },
      { importance: 50, lines: ["经验等级：18", "所在维度：主世界"] },
    ],
    entries: 1,
    "entries.0.title": "整合包说明",
    "entries.0.chapter": "入门",
    "entries.0.source": "FTB 任务",
    "entries.0.passage": "先阅读任务书，再领取新手礼包。",
    "entries.0.has_more": true,
    "entries.0.body": "这里是更多说明。",
    sources: [
      {
        kind: "recipe",
        id: "minecraft:iron_ingot_from_smelting_raw_iron",
        type: "minecraft:smelting",
        station: ["minecraft:furnace"],
        inputs: [{ items: ["minecraft:raw_iron"], count: 1 }],
        outputs: [{ items: ["minecraft:iron_ingot"], count: 1 }],
        notes: ["需要燃料"],
      },
      ...Array.from({ length: 4 }, (_, i) => ({
        kind: "loot",
        id: `minecraft:chests/sample_${i + 1}`,
        notes: ["weight 1"],
        outputs: [],
      })),
      ...Array.from({ length: 29 }, (_, i) => ({
        kind: "tag",
        id: i ? `c:ingots/example_${i}` : "c:ingots/iron",
        outputs: [],
      })),
    ],
  };
  p.result_actions = Array.from({ length: 22 }, (_, i) => ({
    id: `quest.${i}`,
    label: `查看任务 ${i + 1}`,
    placement: "body",
  }));
  p.result_actions.forEach((a, i) =>
    Object.assign(p, {
      [`actions.${i}.label`]: a.label,
      [`actions.${i}.number`]: i + 1,
      [`actions.${i}.action_number`]: i + 1,
      [`actions.${i}.action_title`]: `收集资源 ${i + 1}`,
      [`actions.${i}.action_source`]: i < 19 ? "FTB 任务" : "内置任务（示例）",
      [`actions.${i}.action_icon`]: "minecraft:book",
      [`actions.${i}.action_progress`]: (i * 13) % 101,
      [`actions.${i}.action_quest_id`]: `quest_${i}`,
      [`actions.${i}.action_chapter_id`]: i < 19 ? "resources" : "welcome",
      [`actions.${i}.action_chapter`]: i < 19 ? "资源仓库" : "初来乍到",
    }),
  );
  p.offers = Array.from({ length: 23 }, (_, i) => ({
    id: `offer_${i}`,
    label: `资源补给 ${i + 1}`,
    trade: "购买",
    buyback: false,
    items: i % 4 === 0 ? [] : [
      preview(i % 2 ? "minecraft:apple" : "minecraft:diamond", String(i + 1)),
    ],
  }));
  p.prize_list = p.offers.map((o, i) => ({
    reward: { label: o.label, items: o.items },
    chance: String(i + 1),
  }));
  Object.assign(p, {
    label: "资源补给奖池",
    prizes: p.prize_list.length,
    prize_count: p.prize_list.length,
    preview_count: p.prize_list.length,
    range: template === "lottery_catalog" ? "奖池 1–23 / 151" : "奖品 1–23 / 87",
    payment_available: true,
    payment_unavailable: false,
    condition_unmet: false,
    has_purchase_limit: template === "shop_offer",
    quantity: "交易数量：8 份",
    limit_reached: false,
    purchase_limit: "每日限购 3 次 · 剩余 2 次",
    priced: true,
  });
  p.items = 1;
  p["items.0.item_name"] = "铁锭";
  p["items.0.item_id"] = "minecraft:iron_ingot";
  p["items.0.max_stack"] = 64;
  if (template !== "quest_list")
    p.result_actions = p.result_actions
      .slice(0, 2)
      .map((action, index) => ({
        ...action,
        label: index ? "查看详情" : "领取奖励",
      }));
  p.result_actions.forEach(
    (action, index) => (p[`actions.${index}.label`] = action.label),
  );
  if (template === "quest_pin") {
    p.result_actions = [];
    p.other_tasks = 2;
    p.completed = false;
    p.status = "§c✗§r";
  }
  p.pools = p.offers.map((o) => ({ ...o, tooltip: "奖池概率预览" }));
  for (const [key, entries] of [
    ["offers", p.offers],
    ["pools", p.pools],
  ])
    entries.forEach((entry, i) =>
      Object.assign(p, {
        [`${key}.${i}.label`]: entry.label,
        [`${key}.${i}.items`]: entry.items,
        [`${key}.${i}.tooltip`]: entry.tooltip ?? "",
        [`${key}.${i}.condition_unmet`]: i === 2,
      }),
    );
  p.prize_list.forEach((entry, i) =>
    Object.assign(p, {
      [`prizes.${i}.reward_label`]: entry.reward.label,
      [`prizes.${i}.preview_items`]: entry.reward.items,
      [`prizes.${i}.chance`]: entry.chance,
    }),
  );
  p.sources.forEach((entry, i) => {
    const category = ["loot", "tag"].includes(entry.kind)
      ? entry.kind
      : "recipe";
    Object.assign(p, {
      [`sources.${i}.source`]: entry,
      [`sources.${i}.category`]: category,
      [`sources.${i}.id`]: entry.id,
      [`sources.${i}.notes`]: entry.notes?.join(" · ") ?? "",
    });
    for (const kind of ["recipe", "loot", "tag"])
      p[`sources.${i}.${kind}`] = kind === category;
  });
  if (["shop_catalog", "lottery_catalog"].includes(template))
    p.result_actions = p.offers.map((entry, i) => ({
      id: entry.id,
      label: entry.label,
      placement: "footer",
    }));
  if (["lottery_catalog", "lottery_pool"].includes(template)) {
    p.result_actions.forEach((_, i) => (p[`actions.${i}.footer`] = template === "lottery_pool"));
    p[`actions.${p.result_actions.length}.pager`] = true;
    p.result_actions.push({ id: "next", label: "下一批", placement: "body" });
  }
  if (template === "items") {
    p.single_item = false;
    const examples = [
      ["diamond", "钻石"],
      ["diamond_pickaxe", "钻石镐"],
      ["diamond_axe", "钻石斧"],
      ["diamond_sword", "钻石剑"],
      ["diamond_shovel", "钻石锹"],
      ["diamond_hoe", "钻石锄"],
      ["diamond_helmet", "钻石头盔"],
      ["diamond_chestplate", "钻石胸甲"],
      ["diamond_leggings", "钻石护腿"],
      ["diamond_boots", "钻石靴子"],
      ["diamond_block", "钻石块"],
      ["diamond_ore", "钻石矿石"],
      ["deepslate_diamond_ore", "深层钻石矿石"],
      ["iron_ingot", "铁锭"],
      ["gold_ingot", "金锭"],
      ["emerald", "绿宝石"],
      ["coal", "煤炭"],
      ["redstone", "红石粉"],
      ["lapis_lazuli", "青金石"],
      ["copper_ingot", "铜锭"],
    ];
    p.items = examples.length;
    p.result_actions = examples.map(([id, name], i) => {
      Object.assign(p, {
        [`items.${i}.item_name`]: name,
        [`items.${i}.item_id`]: `minecraft:${id}`,
        [`items.${i}.max_stack`]: i > 0 && i < 10 ? 1 : 64,
        [`items.${i}.preview`]: preview(`minecraft:${id}`),
      });
      return {
        id: `item.${i}`,
        label: name,
        placement: "body",
        value: `system:item_query?query=minecraft%3A${id}`,
      };
    });
    p[`actions.${examples.length}.more_items`] = true;
    p[`actions.${examples.length}.footer`] = true;
    p.result_actions.push({
      id: "next",
      label: "更多物品",
      placement: "footer",
      value: "system:item_query?offset=20&query=ore",
    });
  }
  if (template === "space") {
    const examples = [
      ["iron_ingot", "铁锭", 128],
      ["diamond", "钻石", 12],
      ["bread", "面包", 32],
      ["diamond_pickaxe", "钻石镐", 1],
    ];
    p.entries = examples.length;
    p.feedback = "";
    p.result_actions = examples.map(([id, name, amount], i) => {
      Object.assign(p, {
        [`entries.${i}.name`]: name,
        [`entries.${i}.icon`]: { id: `minecraft:${id}` },
        [`entries.${i}.amount`]: String(amount),
        [`entries.${i}.blocked`]: false,
        [`actions.${i}.shift_value`]: `system:space_take?entry=example-${i}&stack=true`,
      });
      return {
        id: `take.${i}`, label: `取出 ${name}（存量 ${amount}）`, placement: "body",
        value: `system:space_take?entry=example-${i}`,
      };
    });
  }
  if (template === "anchor_list") {
    p.capacity = "已保存 4 个锚点 · 当前容量 3";
    p.cooldown = "查询时传送冷却剩余 5 秒";
    p.cooling = true;
    p.feedback = "已保存锚点：家";
    p.anchors = 4;
    p.destinations = 1;
    p["destinations.0.label"] = "公共广场";
    p["destinations.0.description"] = "数据包定义的公共目的地";
    p["destinations.0.dimension"] = "minecraft:overworld";
    p["destinations.0.teleport_value"] = "system:anchor_teleport?anchor=public:example:plaza";
    p["destinations.0.locked"] = false;
    p.result_actions = [];
    ["家", "矿井", "村庄", "下界入口"].forEach((name, i) => {
      const teleport = `system:anchor_teleport?anchor=example-${i}`;
      const remove = `system:anchor_delete?anchor=example-${i}`;
      Object.assign(p, {
        [`anchors.${i}.name`]: name,
        [`anchors.${i}.dimension`]: i === 3 ? "minecraft:the_nether" : "minecraft:overworld",
        [`anchors.${i}.position`]: `${128 + i * 160}.5, 64.0, -32.5`,
        [`anchors.${i}.locked`]: i >= 3,
        [`anchors.${i}.teleport_value`]: teleport,
        [`anchors.${i}.rename_binding`]: `actions.${i * 3 + 1}.click`,
        [`anchors.${i}.delete_value`]: remove,
        [`actions.${i * 3}.disabled`]: i >= 3,
      });
      p.result_actions.push(
        { id: `teleport.${i}`, label: `传送至 ${name}`, placement: "body", value: teleport },
        { id: `rename.${i}`, label: `重命名锚点 ${name}`, placement: "body", click: "local", value: `actions.${i * 3 + 1}.click` },
        { id: `delete.${i}`, label: `删除锚点 ${name}`, placement: "body", value: remove },
      );
    });
    p[`actions.${p.result_actions.length}.footer`] = true;
    p[`actions.${p.result_actions.length}.disabled`] = true;
    p.result_actions.push({ id: "save", label: "保存当前位置", placement: "footer", value: "system:anchor_save" });
  }
  if (template === "module_controls") {
    p.title = "在线作业";
    p.description =
      "一次进行一项作业。离线或禁用模块时暂停；完成后手动领取，不自动循环。点击刷新查看最新进度。";
    p.action_label = "刷新作业";
    p.result_actions = [];
  }
  const currency = { name: "系统币", icon: { id: "minecraft:gold_nugget" }, amount: "100", label: "系统币 × 100" };
  const experience = { name: "经验点", icon: { id: "minecraft:experience_bottle" }, amount: "80", label: "经验点 × 80" };
  const levels = { name: "经验等级", icon: { id: "minecraft:experience_bottle" }, amount: "2", label: "经验等级 × 2" };
  const effect = { name: "恢复生命", icon: { id: "minecraft:golden_apple" }, amount: "1", label: "恢复生命 × 1" };
  const contents = (prefix, values) => {
    p[prefix + "contents"] = values.length;
    values.forEach((value, i) => {
      for (const [key, entry] of Object.entries(value))
        p[`${prefix}contents.${i}.${key}`] = entry;
    });
  };
  contents("", [currency, experience, levels, effect]);
  p.offer_items = p.result_items;
  for (const key of ["offers", "pools"])
    p[key].forEach((_, i) => contents(`${key}.${i}.`, i % 4 === 0 ? [currency] : i % 4 === 1 ? [experience] : []));
  p.prize_list.forEach((_, i) => {
    const grants = i % 4 === 0 ? [currency] : i % 4 === 1 ? [experience] : [];
    contents(`prizes.${i}.`, grants);
    contents(`prizes.${i}.preview_`, grants);
  });
  if (template === "sign_in_status") {
    p.counts = "累计 7 天 · 连续 3 天 · 最长连续 5 天";
    p.period_counts = "本周 3 天 · 本月 7 天";
    p.fixed = false;
    p.milestones = ["累计签到：7 / 7 · 七日补给", "连续签到：3 / 7 · 连签补给"];
    p.external_model = false;
    p.offline_signin = true;
    p.progression_summary = "本地评估 P90：32.00 · 12 种样本 · 中置信度";
    p.progression_range = "优先奖励包价值范围：25.60～41.60；无匹配时仅向下选择。";
    p.candidates = 2;
    p["candidates.0.reward_label"] = "系统币补给";
    p["candidates.0.result_items"] = [];
    p["candidates.0.reward_items"] = "";
    p["candidates.1.reward_label"] = "经验补给";
    p["candidates.1.result_items"] = [];
    p["candidates.1.reward_items"] = "";
    p["candidates.1.condition_unmet"] = true;
    contents("candidates.0.", [currency]);
    contents("candidates.1.", [experience, levels]);
  }
  if (template === "collection") {
    p.detail = true;
    p.label = "基础矿物收藏";
    p.description = "收集矿物后登记，不消耗背包物品。";
    p.counts = "已登记 2 / 4";
    p.progress = 50;
    p.rows = 4;
    ["diamond", "gold_ingot", "iron_ingot", "emerald"].forEach((item, i) => {
      p[`rows.${i}.label`] = ["钻石", "金锭", "铁锭", "绿宝石"][i];
      p[`rows.${i}.item`] = preview(`minecraft:${item}`, "1");
      p[`rows.${i}.complete`] = i < 2;
      p[`rows.${i}.status`] = i < 2 ? "已登记" : "未登记";
    });
    p.milestones = ["登记 1 项 · 初次收藏 · 待领取", "登记 4 项 · 矿物专家 · 未达成"];
    p.result_actions = [
      { id: "register", label: "登记背包物品", value: "system:collection_register?collection=system%3Adefault", placement: "body" },
      { id: "back", label: "返回", value: "system:collection_query", placement: "body" },
      { id: "records", label: "进度与奖励", value: "system:record_query", placement: "body" },
    ];
    p.result_actions.forEach((_, i) => (p[`actions.${i}.footer`] = true));
  }
  if (template === "record_rewards") {
    p.pending = "待领取 2 项";
    p.rewards = 3;
    p.counts = ["签到：7", "登记收藏：4", "完成任务：12"];
    p.result_actions = ["初次收藏", "累计七日签到", "任务积分"].map((label, i) => {
      p[`rewards.${i}.reward_label`] = label;
      p[`rewards.${i}.preview`] = i === 0 ? [preview("minecraft:bread", "2")] : [];
      p[`rewards.${i}.reward_text`] = i === 0 ? "面包 × 2" : i === 1 ? "系统币 × 5" : "任务积分 × 1";
      p[`rewards.${i}.status`] = i === 2 ? "已存入系统空间" : "待领取";
      p[`actions.${i}.disabled`] = i === 2;
      return { id: `claim.${i}`, label: "领取到系统空间", value: `system:record_claim?entitlement=sample_${i}`, placement: "body" };
    });
    p["actions.3.footer"] = true;
    p.result_actions.push({ id: "refresh", label: "刷新", value: "system:record_query", placement: "body" });
  }
  if (template === "achievements") {
    p.overview = "已完成 1 / 3 项成就";
    p.series = 2;
    p["series.0.text"] = "签到之路 · 1 / 2";
    p["series.1.text"] = "收藏之路 · 0 / 1";
    p.entries = 3;
    p.result_actions = ["首次签到", "签到七次", "收藏五件"].map((label, i) => {
      Object.assign(p, {
        [`entries.${i}.label`]: label,
        [`entries.${i}.description`]: "积累可信行为，达成目标后领取奖励。",
        [`entries.${i}.series`]: i === 2 ? "收藏之路" : "签到之路",
        [`entries.${i}.progress_text`]: i === 0 ? "1 / 1" : "1 / 5",
        [`entries.${i}.progress`]: i === 0 ? 100 : 20,
        [`entries.${i}.status`]: i === 0 ? "待领取" : "未达成",
        [`entries.${i}.reward`]: "奖励：系统币 × 2",
        [`actions.${i}.disabled`]: i !== 0,
      });
      return { id: `claim.${i}`, label: "领取到系统空间", value: `system:record_claim?entitlement=achievement:sample_${i}`, placement: "body" };
    });
    p["actions.3.footer"] = true;
    p.result_actions.push({ id: "refresh", label: "刷新", value: "system:achievement_query" });
  }
  if (template === "online_jobs") {
    p.active = true;
    p.quote = true;
    p.quote_label = "木炭生产";
    p.quote_cost = "原木 × 2";
    p.current_label = "训练";
    p.current_status = "进行中";
    p.current_time = "30 / 60 秒";
    p.progress = 50;
    p.current_items = [];
    contents("current_", [experience]);
    p.jobs = 2;
    [currency, experience].forEach((grant, i) => {
      p[`jobs.${i}.label`] = i ? "训练" : "赚取系统币";
      p[`jobs.${i}.duration`] = "60 秒";
      p[`jobs.${i}.cost`] = i ? "原木 × 2" : "无需费用";
      p[`jobs.${i}.items`] = [];
      p[`jobs.${i}.reward_text`] = grant.label;
      p[`jobs.${i}.condition_unmet`] = i === 1;
      p[`actions.${i}.disabled`] = i === 1;
      contents(`jobs.${i}.preview_`, [grant]);
    });
  }
  if (template === "lottery_pool") {
    p.draw_count = "10 抽";
    p.rules = 3;
    p["rules.0.text"] = "距离保底最多 4 抽（上限 10 抽）";
    p["rules.1.text"] = "保底范围：钻石补给";
    p["rules.2.text"] = "抽取限制（每日）：上限 30 次，剩余 20 次";
    p["prizes.0.limited"] = true;
    p["prizes.0.max_wins"] = 2;
    p["prizes.0.win_count"] = 1;
    p["prizes.0.remaining_wins"] = 1;
    p["prizes.0.rule"] = "最多 2 次 · 已获得 1 次";
    p["prizes.1.limited"] = true;
    p["prizes.1.exhausted"] = true;
    p["prizes.1.max_wins"] = 2;
    p["prizes.1.win_count"] = 2;
    p["prizes.1.remaining_wins"] = 0;
    p["prizes.1.rule"] = "最多 2 次 · 已获得 2 次";
    p["prizes.1.chance"] = "0";
  }
  if (template === "lottery_history") {
    p.detail = true;
    p.range = "抽取结果 1–4 / 10";
    p.orders = 1;
    p["orders.0.heading"] = "资源补给奖池 · 10 抽 · 已结算";
    p["orders.0.time"] = "2026-11-16T12:00:00Z";
    p["orders.0.id"] = "00000000-0000-0000-0000-000000000001";
    p["orders.0.rolls"] = 4;
    for (let i = 0; i < 4; i++) {
      p[`orders.0.rolls.${i}.label`] = i === 3 ? "钻石补给" : "食物补给";
      p[`orders.0.rolls.${i}.contents`] = i === 3 ? "钻石 × 1" : "面包 × 8";
      p[`orders.0.rolls.${i}.guaranteed`] = i === 3;
    }
    p.result_actions = [{ id: "back", label: "返回记录列表", placement: "body" }];
    p["actions.0.pager"] = true;
  }
  if (template === "workshop_controls") {
    p.title = "工坊";
    p.description = "主手拿一件装备，先报价，再确认维修或分解。";
    p.result_actions = [];
  }
  if (template.startsWith("salvage_")) {
    p.item = preview("minecraft:iron_sword");
    p.item_name = "铁剑";
    p.rule = "基础材料回收";
    p.rule_id = "system:iron_sword";
    p.order_id = "00000000-0000-0000-0000-000000000001";
    p.state = "已完成";
    p.price = "免费";
    contents("", [{ name: "铁粒", icon: { id: "minecraft:iron_nugget" }, amount: "9", label: "铁粒 × 9" }]);
    p.result_actions = template === "salvage_quote"
      ? [{ id: "confirm", label: "确认分解", value: "system:salvage_confirm?quote=example" }, { id: "cancel", label: "取消", value: "system:salvage_cancel?quote=example" }]
      : [{ id: "space", label: "查看系统空间", value: "system:inbox" }, { id: "history", label: "分解记录", value: "system:salvage_history" }];
    p.result_actions.forEach((_, i) => (p[`actions.${i}.footer`] = true));
    if (template === "salvage_history") {
      p.orders = 1;
      p["orders.0.id"] = p.order_id;
      p["orders.0.item"] = p.item;
      p["orders.0.name"] = "铁剑";
      p["orders.0.state"] = "已完成";
      p["orders.0.outputs"] = "铁粒 × 9";
      p.page = "第 1 / 1 页";
      p.result_actions = [{ id: "previous", label: "上一页", value: "system:salvage_history?offset=0" }, { id: "next", label: "下一页", value: "system:salvage_history?offset=10" }];
      p.result_actions.forEach((_, i) => { p[`actions.${i}.footer`] = false; p[`actions.${i}.disabled`] = true; });
    }
  }
  p.actions = p.result_actions.length;
  p.result_actions.forEach((entry, i) =>
    Object.assign(p, {
      [`actions.${i}.label`]: entry.label,
      [`actions.${i}.value`]: entry.value ?? "system:quest_detail?quest=" + i,
      [`actions.${i}.click_type`]: entry.click ?? "return-val",
      [`actions.${i}.behavior`]: "push",
      [`actions.${i}.pin`]: `actions.${i}.pin`,
      [`actions.${i}.action_icon`]: {
        quest: String(i),
        title: p[`actions.${i}.action_title`],
      },
    }),
  );
  p.pin = "pin";
  return {
    parameters: p,
    states: {
      show_card_title: 1,
      ...(template === "quest_pin" ? { hud: 1 } : {}),
    },
    callbacks: template === "workshop_controls" ? ["repair", "salvage", "history"] : template === "module_controls" ? ["submit"] : ["example"],
  };
}
