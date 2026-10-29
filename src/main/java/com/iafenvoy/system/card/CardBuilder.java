package com.iafenvoy.system.card;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.LegacyText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One pure builder owns the complete card description and takes immutable snapshots on build.
 */
public final class CardBuilder {
    private final Identifier template;
    private final CardContext.Builder context = CardContext.builder();
    private final List<CardButton> buttons = new ArrayList<>();
    private String titleKey = "";
    private Component summary = Component.empty();
    private SpeechContent speech;

    private CardBuilder(Identifier template) {
        this.template = template;
    }

    public static CardBuilder template(Identifier template) {
        return new CardBuilder(template);
    }

    public CardBuilder title(String key) {
        this.titleKey = key;
        return this;
    }

    public CardBuilder summary(Component text) {
        this.summary = LegacyText.snapshot(text);
        return this;
    }

    public CardBuilder summary(String key, Object... arguments) {
        return this.summary(Component.translatable(key, arguments));
    }

    public CardBuilder speech(SpeechContent content) {
        this.speech = content;
        return this;
    }

    public CardBuilder speech(Component content) {
        return this.speech(SpeechContent.of(content));
    }

    public CardBuilder put(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    public CardBuilder enable(String key, boolean value) {
        this.context.enable(key, value);
        return this;
    }

    public <T> CardBuilder list(String key, List<T> values, BiConsumer<String, T> fill) {
        this.context.list(key, values, fill);
        return this;
    }

    public CardBuilder reward(String prefix, PlayerData.Reward reward) {
        List<ItemPreviewOutput> items = reward.items().stream().map(ItemPreviewOutput::of).toList();
        return this.put(prefix + "reward_label", reward.label()).put(prefix + "reward_items", new CardText.Items(items))
                .put(prefix + "result_items", items);
    }

    public CardBuilder price(CommerceCatalog.Price price) {
        return this.put("price", new CardText.Price(price, false)).put("price_text", new CardText.Price(price, true))
                .put("price_items", price.items());
    }

    public CardBuilder button(CardButton button) {
        if (this.buttons.stream().anyMatch(existing -> existing.id().equals(button.id())))
            throw new IllegalArgumentException("Duplicate card button " + button.id());
        this.buttons.add(button);
        return this;
    }

    public CardBuilder serverButton(String id, String label, Identifier action, Map<String, String> arguments) {
        return this.serverButton(id, label, action, arguments, CardButton.Placement.FOOTER, CardButton.Behavior.REPLACE);
    }

    public CardBuilder serverButton(String id, String label, Identifier action, Map<String, String> arguments,
                                    CardButton.Placement placement, CardButton.Behavior behavior) {
        return this.button(new CardButton(id, label, new CardAction.Server(action, arguments), placement, behavior));
    }

    public CardBuilder localButton(String id, String label, Identifier action, Map<String, String> arguments) {
        return this.button(new CardButton(id, label, new CardAction.Local(action, arguments),
                CardButton.Placement.FOOTER, CardButton.Behavior.CLOSE));
    }

    public CardBuilder actionParameter(String key, Object value) {
        if (this.buttons.isEmpty()) throw new IllegalStateException("No button to decorate");
        return this.put("actions." + (this.buttons.size() - 1) + "." + key, value);
    }

    public static Component join(List<Component> parts, String separator) {
        MutableComponent result = Component.empty();
        for (Component part : parts) {
            if (!result.getSiblings().isEmpty()) result.append(separator);
            result.append(LegacyText.snapshot(part));
        }
        return result;
    }

    public CardView build() {
        return new CardView(this.template, this.titleKey, this.summary, this.context.build(), this.buttons,
                this.speech == null ? SpeechContent.of(this.summary) : this.speech);
    }
}
