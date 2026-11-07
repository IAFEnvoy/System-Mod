export function sampleContext(template = "quest_list") {
  const preview = (id, amount = "1") => ({ item: { id }, amount });
  const p = {
    summary: "叮！检测到宿主的新进展，请选择下一步操作。",
    card_title: "系统消息",
    title: "收集基础资源",
    notice_title: "任务完成",
    id: "5A710001",
    chapter: "资源仓库",
    description: "收集材料，为接下来的冒险做好准备。",
    progress: 65,
    answer:
      "## 获取基础资源\n先收集 **木材**，再制作工作台。\n\n- 木板可以合成木棍\n- 使用石镐获取铁矿\n\n> 完成准备后即可继续探索。",
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
      [`actions.${i}.action_progress`]: (i * 13) % 101,
      [`actions.${i}.action_quest_id`]: `quest_${i}`,
      [`actions.${i}.action_chapter_id`]: i < 19 ? "resources" : "welcome",
      [`actions.${i}.action_chapter`]: i < 19 ? "资源仓库" : "初来乍到",
    }),
  );
  p.offers = Array.from({ length: 23 }, (_, i) => ({
    id: `offer_${i}`,
    label: `资源补给 ${i + 1}`,
    items: [
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
    payment_available: true,
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
  p.actions = p.result_actions.length;
  p.result_actions.forEach((entry, i) =>
    Object.assign(p, {
      [`actions.${i}.label`]: entry.label,
      [`actions.${i}.value`]: "system:quest_detail?quest=" + i,
      [`actions.${i}.click_type`]: "return-val",
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
    callbacks: ["example"],
  };
}
