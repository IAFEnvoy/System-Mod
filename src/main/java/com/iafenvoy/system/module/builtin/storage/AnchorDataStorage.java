package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Insertion order defines unlocked slots; losing capacity never deletes destinations.
 */
public final class AnchorDataStorage extends DataStorage {
    public static final int MAX_ANCHORS = 256;
    public static final MapCodec<AnchorDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TeleportAnchor.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(AnchorDataStorage::getAnchors),
            Codec.LONG.optionalFieldOf("next_teleport", 0L).forGetter(AnchorDataStorage::getNextTeleport)
    ).apply(i, AnchorDataStorage::new));
    private final List<TeleportAnchor> anchors;
    private long nextTeleport;

    public AnchorDataStorage() {
        this(List.of(), 0);
    }

    private AnchorDataStorage(List<TeleportAnchor> anchors, long nextTeleport) {
        this.anchors = new ArrayList<>(anchors);
        this.nextTeleport = nextTeleport;
    }

    public List<TeleportAnchor> getAnchors() {
        return List.copyOf(this.anchors);
    }

    public long getNextTeleport() {
        return this.nextTeleport;
    }

    public TeleportAnchor find(String selector) {
        return this.anchors.stream().filter(anchor -> anchor.id().equals(selector)).findFirst()
                .or(() -> this.anchors.stream().filter(anchor -> anchor.name().equalsIgnoreCase(selector.strip())).findFirst())
                .orElseThrow(() -> new AgentException(AgentFailure.ANCHOR_NOT_FOUND));
    }

    public void add(TeleportAnchor anchor, int limit) {
        if (this.anchors.size() >= Math.min(limit, MAX_ANCHORS))
            throw new AgentException(AgentFailure.ANCHOR_LIMIT_REACHED);
        if (this.anchors.stream().anyMatch(existing -> existing.id().equals(anchor.id()) || existing.name().equalsIgnoreCase(anchor.name())))
            throw new AgentException(AgentFailure.ANCHOR_NAME_EXISTS);
        this.anchors.add(anchor);
        this.markDirty();
    }

    public void remove(String id) {
        if (!this.anchors.removeIf(anchor -> anchor.id().equals(id)))
            throw new AgentException(AgentFailure.ANCHOR_NOT_FOUND);
        this.markDirty();
    }

    public void setNextTeleport(long time) {
        if (this.nextTeleport == time) return;
        this.nextTeleport = time;
        this.markDirty();
    }

    @Override
    public MapCodec<AnchorDataStorage> codec() {
        return CODEC;
    }

    @Override
    public AnchorDataStorage copy() {
        return new AnchorDataStorage(this.anchors, this.nextTeleport);
    }
}
