package com.iafenvoy.system.screen.gui;

import com.google.gson.JsonElement;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.module.client.ClientModules;
import com.iafenvoy.system.screen.card.CardContext;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.function.Consumer;

/**
 * Shared text formatting and typed card rendering; dynamic text never becomes HTML.
 */
public final class AssistantView {
    private AssistantView() {
    }

    public static String actionLabel(AgentResult.Card card, AgentResult.Action action) {
        return ClientModules.registry().cards().label(card, action);
    }

    public static String formatCard(AgentResult.Card card) {
        return formatCard(card, true);
    }

    public static String formatCard(AgentResult.Card card, boolean textItems) {
        return ClientModules.registry().cards().format(card, textItems);
    }

    public static Element renderCard(Document document, AgentResult.Card card, String messageKey) {
        return ClientModules.registry().cards().presentation(card, messageKey, CardContext.Mode.FULL, 0).render(document);
    }

    public static String formatPrice(CommerceCatalog.Price price, boolean textItems) {
        List<String> lines = new ArrayList<>(price.descriptions().stream().map(Component::getString).toList());
        if (textItems)
            for (ItemPreviewOutput item : price.items())
                lines.add(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount()).getString());
        return lines.isEmpty() && price.items().isEmpty() ? tr("system.ui.lottery_no_cost") : String.join("\n", lines);
    }

    public static String formatItems(List<ItemStackTemplate> items) {
        List<String> names = new ArrayList<>();
        for (ItemStackTemplate template : items) {
            ItemStack stack = template.create();
            String name = stack.isEmpty() ? BuiltInRegistries.ITEM.getKey(template.item().value()).toString()
                    : stack.getHoverName().getString();
            names.add(Component.translatable("system.ui.item_amount", name, template.count()).getString());
        }
        return String.join("\n", names);
    }

    public static CardContext.Builder rewardParameters(CardContext.Builder builder, PlayerData.Reward reward) {
        return rewardParameters(builder, "", reward);
    }

    public static CardContext.Builder rewardParameters(CardContext.Builder builder, String prefix, PlayerData.Reward reward) {
        return builder.put(prefix + "reward_label", reward.label()).put(prefix + "reward_items", formatItems(reward.items()))
                .put(prefix + "result_items", reward.items().stream().map(ItemPreviewOutput::of).toList());
    }

    public static CardContext.Builder priceParameters(CardContext.Builder builder, CommerceCatalog.Price price) {
        return builder.put("price", formatPrice(price, false)).put("price_text", formatPrice(price, true))
                .put("price_items", price.items());
    }

    public static String format(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth > 5) return "";
        if (value.isJsonPrimitive()) {
            String text = value.getAsString();
            if (Set.of("PENDING", "DELIVERING", "DELIVERED", "DELIVERY_UNKNOWN", "CLAIM_EXECUTED", "CLAIMED", "CAN_CLAIM", "CANT_CLAIM", "ALREADY_CLAIMED",
                    "COMPLETED", "PREPARED", "PAYMENT_PENDING", "PAYMENT_UNKNOWN").contains(text))
                return tr("system.state." + text.toLowerCase(Locale.ROOT));
            return text.equals("true") ? tr("system.ui.yes") : text.equals("false") ? tr("system.ui.no") : text;
        }
        List<String> lines = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) lines.add(format(element, depth + 1));
        } else for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            if (Set.of("claimRef", "quoteRef", "catalogRevision").contains(field.getKey())) continue;
            if (field.getKey().equals("items") && field.getValue().isJsonArray() && field.getValue().getAsJsonArray().isEmpty())
                continue;
            if (field.getKey().equals("error") && field.getValue().isJsonPrimitive()) {
                String error = field.getValue().getAsString();
                if (!error.isBlank()) lines.add(tr("system.error." + error.toLowerCase(Locale.ROOT)));
                continue;
            }
            String key = "system.field." + field.getKey().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            lines.add(tr(key) + ": " + format(field.getValue(), depth + 1));
        }
        return String.join("\n", lines);
    }

    public static Element element(Document document, String tag, String css, String value) {
        Element result = document.createElement(tag);
        result.setClassName(css);
        text(result, value);
        return result;
    }

    public static void bind(Document document, String id, Runnable action) {
        document.getElementById(id).addEventListener("click", event -> action.run());
    }

    /**
     * Button-backed switches use one state attribute for appearance, accessibility and form reads.
     */
    public static void bindSwitch(Document document, String id, Consumer<Boolean> changed) {
        Element control = document.getElementById(id);
        control.addEventListener("click", event -> {
            if (control.isDisabled()) return;
            boolean checked = !switchChecked(control);
            switchChecked(control, checked);
            changed.accept(checked);
        });
    }

    public static boolean switchChecked(Element control) {
        return "true".equals(control.getAttribute("aria-checked"));
    }

    public static void switchChecked(Element control, boolean checked) {
        if (switchChecked(control) != checked) control.setAttribute("aria-checked", Boolean.toString(checked));
    }

    public static void text(Element element, String value) {
        AssistantText.set(element, value);
    }

    public static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
