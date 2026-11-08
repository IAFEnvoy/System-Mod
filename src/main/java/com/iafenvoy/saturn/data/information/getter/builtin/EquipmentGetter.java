package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public enum EquipmentGetter implements EntityValueGetter<Map<String, PlayerViewItem>> {
    INSTANCE;

    public static final MapCodec<EquipmentGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EquipmentGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Map<String, PlayerViewItem>> valueCodec() {
        return Codec.unboundedMap(Codec.STRING, PlayerViewItem.CODEC);
    }

    @Override
    public ViewValue<Map<String, PlayerViewItem>> capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        Map<String, PlayerViewItem> equipment = new LinkedHashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getItemBySlot(slot);
            if (!stack.isEmpty())
                equipment.put(slot.getSerializedName(), PlayerViewItem.capture(stack, living.registryAccess()));
        }
        return ViewValue.known(equipment, ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<Map<String, PlayerViewItem>> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "equipment", data -> {
            CompoundTag items = data.getCompound("equipment");
            Map<String, PlayerViewItem> equipment = new LinkedHashMap<>();
            for (String slot : items.getAllKeys())
                equipment.put(slot, PlayerViewItem.saved(items.getCompound(slot), context.registries()).orElseThrow());
            if (data.contains("Inventory") && data.contains("SelectedItemSlot")) {
                String selected = Integer.toString(data.getInt("SelectedItemSlot"));
                PlayerViewItem main = GetterSupport.savedItems(data.getList("Inventory", Tag.TAG_COMPOUND), context).get(selected);
                if (main != null) equipment.put("mainhand", main);
            }
            return equipment;
        });
    }
}
