package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.PaymentType;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.TypeDispatch;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.iafenvoy.system.screen.gui.AssistantView.*;

/**
 * Typed item projections shared by cards, the current exchange and queued HUD messages.
 */
public final class AssistantItems {
    private static final TypeDispatch<Void, List<Group>> GROUPS = new TypeDispatch<Void, List<Group>>((_, _) -> List.of())
            .register(RewardOutput.class, (_, value) -> reward(value.reward()))
            .register(CreditReceiptOutput.class, (_, value) -> reward(value.reward()))
            .register(PurchaseReceiptOutput.class, (_, value) -> reward(value.reward()))
            .register(StarterGiftOutput.class, (_, value) -> List.of(new Group(tr("system.field.reward"), value.items().stream()
                    .map(item -> new Entry(item, tr(value.claimed() ? "system.state.claimed"
                            : value.claimRef().isPresent() ? "system.state.can_claim" : "system.state.cant_claim"))).toList())))
            .register(LotteryPrizeOutput.class, (_, value) -> reward(value.reward()))
            .register(LotteryPoolOutput.class, (_, value) -> {
                List<Group> groups = new ArrayList<>(value.price().map(price -> price(tr("system.ui.lottery_cost"), price)).orElse(List.of()));
                List<Entry> prizes = new ArrayList<>();
                for (LotteryPrizeOutput prize : value.prizes())
                    for (ItemStackTemplate item : prize.reward().items())
                        prizes.add(new Entry(ItemPreviewOutput.of(item), prize.reward().label() + "\n" + lotteryChance(prize)));
                groups.add(new Group(Component.translatable("system.ui.lottery_prize_preview", value.prizes().size(), value.prizeCount()).getString(), prizes));
                return groups;
            })
            .register(ResultTypes.ShopOffer.class, (_, value) -> {
                List<Group> groups = new ArrayList<>();
                groups.add(templates(tr("system.field.items"), value.items()));
                groups.addAll(price(value.price()));
                return groups;
            })
            .register(ResultTypes.SignInStatus.class, (_, value) -> {
                List<Entry> candidates = new ArrayList<>();
                for (PlayerData.Reward reward : value.candidates())
                    for (ItemStackTemplate item : reward.items())
                        candidates.add(new Entry(ItemPreviewOutput.of(item), reward.label()));
                return List.of(new Group(tr("system.field.candidates"), candidates));
            })
            .register(QuestOutput.class, (_, value) -> {
                List<Group> groups = new ArrayList<>();
                List<Entry> rewards = new ArrayList<>();
                for (QuestRewardOutput reward : value.rewards()) {
                    for (ItemPreviewOutput item : reward.items())
                        rewards.add(new Entry(item, tr("system.state." + reward.state().toLowerCase(Locale.ROOT))));
                    for (QuestChoiceOutput choice : reward.choices().orElse(List.of()))
                        groups.add(group(reward.title() + " · " + choice.title(), choice.items()));
                }
                groups.addFirst(new Group(tr("system.field.reward"), rewards));
                return groups;
            })
            .register(ResultTypes.QuestNotice.class, (_, value) -> List.of(group(tr("system.field.reward"), value.items())))
            .register(ItemPageOutput.class, (_, value) -> {
                List<ItemPreviewOutput> items = new ArrayList<>();
                for (ItemInfoOutput entry : value.items()) {
                    Identifier id = Identifier.tryParse(entry.id());
                    if (id == null) continue;
                    BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                        if (!new ItemStack(item).isEmpty())
                            items.add(ItemPreviewOutput.of(new ItemStackTemplate(item)));
                    });
                }
                return List.of(group(tr("system.field.items"), items));
            });

    public record Entry(ItemPreviewOutput preview, String description) {
    }

    public record Group(String label, List<Entry> items) {
        public Group {
            items = List.copyOf(items);
        }
    }

    private AssistantItems() {
    }

    public static List<Group> groups(AgentResult.Card card) {
        return GROUPS.apply(null, card.data().value()).stream().filter(group -> !group.items().isEmpty()).toList();
    }

    public static List<Group> groups(AgentResult result) {
        return result.cards().stream().flatMap(card -> groups(card).stream()).toList();
    }

    private static List<Group> reward(PlayerData.Reward reward) {
        return List.of(templates(tr("system.field.reward"), reward.items()));
    }

    private static Group templates(String label, List<ItemStackTemplate> items) {
        return group(label, items.stream().map(ItemPreviewOutput::of).toList());
    }

    private static Group group(String label, List<ItemPreviewOutput> items) {
        return new Group(label, items.stream().map(item -> new Entry(item, "")).toList());
    }

    private static List<Group> price(CommerceCatalog.Price price) {
        return price(tr("system.field.price"), price);
    }

    private static List<Group> price(String label, CommerceCatalog.Price price) {
        return price.type() == PaymentType.ITEMS
                ? List.of(templates(label, price.items())) : List.of();
    }

    public static void render(Document document, Element container, List<Group> groups, boolean compact) {
        if (container == null) return;
        container.setInnerHTML("");
        int count = 0;
        for (Group group : groups) {
            Element section = element(document, "div", "item_group", "");
            section.appendChild(element(document, "div", "item_group_label", group.label() + ":"));
            Element row = element(document, "div", "item_row", "");
            for (Entry value : group.items()) {
                ItemPreviewOutput preview = value.preview();
                ItemStack stack = preview.item().create();
                if (stack.isEmpty()) continue;
                Element entry = element(document, "div", "item_entry" + (compact ? " compact" : ""), "");
                Element frame = element(document, "div", "item_icon_frame", "");
                Item icon = new Item(document);
                icon.setClassName("item_icon");
                icon.setAttribute("interactive", "tooltip");
                icon.setDrivenState(stack, preview.amount().equals("1") ? "" : preview.amount(), false, false, Item.Source.INGREDIENT);
                frame.appendChild(icon);
                entry.appendChild(frame);
                if (!compact || !value.description().isBlank()) {
                    Element text = element(document, "div", "item_text", "");
                    if (!compact) text.appendChild(element(document, "div", "item_name",
                            Component.translatable("system.ui.item_amount", stack.getHoverName(), preview.amount()).getString()));
                    if (!value.description().isBlank())
                        text.appendChild(element(document, "div", "item_description", value.description()));
                    entry.appendChild(text);
                }
                row.appendChild(entry);
                count++;
            }
            if (row.children.isEmpty()) continue;
            section.appendChild(row);
            container.appendChild(section);
        }
        container.setInlineStyleProperty("display", count == 0 ? "none" : "flex");
    }

    /**
     * AUI owns hit testing and clipping; vanilla owns tooltip components, advanced details and mod hooks.
     */
    public static boolean tooltip(Document document, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (document == null || document.isDisposed()) return false;
        List<Element> elements = document.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (!(elements.get(index) instanceof Item item) || !item.isConnected() || !item.isVisible || !item.isHover)
                continue;
            ItemStack stack = item.getTooltipStack();
            if (stack.isEmpty()) continue;
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
