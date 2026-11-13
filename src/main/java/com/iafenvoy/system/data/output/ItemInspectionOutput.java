package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A snapshot of the actual held stack, including its component patch and stored enchantments. */
public record ItemInspectionOutput(Identifier id, Component name, ItemStackTemplate item, int maxStack,
                                   int damage, int maxDamage, boolean unbreakable,
                                   List<Component> enchantments) implements CardData {
    public static final MapCodec<ItemInspectionOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ItemInspectionOutput::id),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ItemInspectionOutput::name),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemInspectionOutput::item),
            Codec.INT.fieldOf("maxStack").forGetter(ItemInspectionOutput::maxStack),
            Codec.INT.fieldOf("damage").forGetter(ItemInspectionOutput::damage),
            Codec.INT.fieldOf("maxDamage").forGetter(ItemInspectionOutput::maxDamage),
            Codec.BOOL.fieldOf("unbreakable").forGetter(ItemInspectionOutput::unbreakable),
            ComponentSerialization.CODEC.listOf().fieldOf("enchantments").forGetter(ItemInspectionOutput::enchantments)
    ).apply(i, ItemInspectionOutput::new));

    public ItemInspectionOutput {
        name = name.copy();
        enchantments = enchantments.stream().map(text -> (Component) text.copy()).toList();
    }

    public static ItemInspectionOutput of(ItemStack stack) {
        List<Component> enchantments = new ArrayList<>();
        stack.getTagEnchantments().entrySet().forEach(entry -> enchantments.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue())));
        stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
                .forEach(entry -> enchantments.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue())));
        return new ItemInspectionOutput(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getHoverName(),
                ItemStackTemplate.fromNonEmptyStack(stack), stack.getMaxStackSize(), stack.getDamageValue(),
                stack.getMaxDamage(), stack.has(DataComponents.UNBREAKABLE), enchantments);
    }

    @Override
    public MapCodec<ItemInspectionOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_inspection"))
                .title("system.result.item_inspection").summary("system.result.item_inspection")
                .put("item_name", this.name).put("item_id", this.id.toString())
                .put("item", ItemPreviewOutput.of(this.item))
                .put("stack_info", Component.translatable("system.inspection.stack", this.item.count(), this.maxStack))
                .enable("damageable", this.maxDamage > 0)
                .put("durability", Component.translatable("system.inspection.durability", this.maxDamage - this.damage, this.maxDamage))
                .enable("unbreakable", this.unbreakable)
                .put("enchantments", this.enchantments.isEmpty() ? Component.translatable("system.inspection.no_enchantments")
                        : CardBuilder.join(this.enchantments, "\n"))
                .serverButton("sources", "system.action.item_sources", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"),
                        Map.of("item_id", this.id.toString()));
    }
}
