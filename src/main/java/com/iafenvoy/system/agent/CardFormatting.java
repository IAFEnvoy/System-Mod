package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;

/** The caller supplies text resolution, so shared card data never needs to load client UI classes. */
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

    default CardContext.Builder rewardParameters(CardContext.Builder builder, String prefix, PlayerData.Reward reward) {
        return builder.put(prefix + "reward_label", reward.label()).put(prefix + "reward_items", this.items(reward.items()))
                .put(prefix + "result_items", reward.items().stream().map(ItemPreviewOutput::of).toList());
    }

    default CardContext.Builder priceParameters(CardContext.Builder builder, CommerceCatalog.Price price) {
        return builder.put("price", this.price(price, false)).put("price_text", this.price(price, true))
                .put("price_items", price.items());
    }
}
