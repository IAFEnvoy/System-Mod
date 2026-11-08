package com.iafenvoy.system.data.store;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.chat.AiAccess;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * World-owned administrator policy; workers read immutable snapshots, only the server thread edits them.
 */
public final class AiAccessData extends SavedData {
    private static final Codec<AiAccessData> CODEC = AiAccess.CODEC.xmap(AiAccessData::new, AiAccessData::get);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .ifPresent(tag::merge);
        return tag;
    }

    public static final String ID = SystemMod.MOD_ID + ":ai_access";
    public static final SavedData.Factory<AiAccessData> TYPE = new SavedData.Factory<>(AiAccessData::new, AiAccessData::load, null);
    private volatile AiAccess value;

    private static AiAccessData load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(AiAccessData::new);
    }

    public AiAccessData() {
        this(AiAccess.DEFAULT);
    }

    private AiAccessData(AiAccess value) {
        this.value = value;
    }

    public AiAccess get() {
        return this.value;
    }

    public void update(UnaryOperator<AiAccess> change) {
        AiAccess next = Objects.requireNonNull(change.apply(this.value));
        if (next.equals(this.value)) return;
        this.value = next;
        this.setDirty();
    }
}
