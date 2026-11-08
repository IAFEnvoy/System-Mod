package com.iafenvoy.system.data.store;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.chat.AiAccess;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * World-owned administrator policy; workers read immutable snapshots, only the server thread edits them.
 */
public final class AiAccessData extends SavedData {
    private static final Codec<AiAccessData> CODEC = AiAccess.CODEC.xmap(AiAccessData::new, AiAccessData::get);
    public static final SavedDataType<AiAccessData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "ai_access"), AiAccessData::new, CODEC);
    private volatile AiAccess value;

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
