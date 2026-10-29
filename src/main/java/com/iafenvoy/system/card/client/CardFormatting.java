package com.iafenvoy.system.card.client;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.card.CardText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.iafenvoy.system.util.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The caller supplies text resolution, so shared card data never needs to load client UI classes.
 */
@FunctionalInterface
public interface CardFormatting {
    String text(Component component);

    default String tr(String key) {
        return this.text(Component.translatable(key));
    }

    default String items(List<ItemStackTemplate> items) {
        List<String> names = new ArrayList<>();
        for (ItemStackTemplate template : items) {
            ItemStack stack = template.create();
            String name = stack.isEmpty() ? BuiltInRegistries.ITEM.getKey(template.item().value()).toString()
                    : this.text(stack.getHoverName());
            names.add(this.text(Component.translatable("system.ui.item_amount", name, template.count())));
        }
        return String.join("\n", names);
    }

    default String price(CommerceCatalog.Price price, boolean textItems) {
        List<String> lines = new ArrayList<>(price.descriptions().stream().map(this::text).toList());
        if (textItems)
            for (ItemPreviewOutput item : price.items())
                lines.add(this.text(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount())));
        return lines.isEmpty() && price.items().isEmpty() ? this.tr("system.ui.lottery_no_cost") : String.join("\n", lines);
    }

    default String value(Object value) {
        if (value instanceof Component component) return this.text(component);
        if (value instanceof CardText.Items(List<ItemPreviewOutput> items1)) {
            return String.join("\n", items1.stream().map(item -> this.text(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount()))).toList());
        }
        if (value instanceof CardText.Price(
                CommerceCatalog.Price price1, boolean includeItems
        )) return this.price(price1, includeItems);
        return value == null ? "" : value.toString();
    }
}
