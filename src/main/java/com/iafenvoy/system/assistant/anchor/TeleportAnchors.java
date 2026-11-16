package com.iafenvoy.system.assistant.anchor;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.input.AnchorDeleteInput;
import com.iafenvoy.system.data.input.AnchorSaveInput;
import com.iafenvoy.system.data.input.AnchorTeleportInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.AnchorSystemModule;
import com.iafenvoy.system.module.builtin.storage.AnchorDataStorage;
import com.iafenvoy.system.registry.SystemTicketTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Worker-side storage transactions and game-thread validation; chunk loading never blocks a game tick.
 */
public final class TeleportAnchors {
    private record Saved(List<TeleportAnchor> anchors, long nextTeleport) {
    }

    private record Destination(TeleportAnchor anchor, int slot, long nextTeleport) {
    }

    private record Capture(TeleportAnchor anchor, int limit) {
    }

    private record Landing(Destination destination, Vec3 position, int limit, long time) {
    }

    private record Reservation(Destination destination, long previous, long reserved) {
    }

    private final AgentEngine engine;
    private final MinecraftTools game;
    private final AnchorSystemModule settings;

    public TeleportAnchors(AgentEngine engine, MinecraftTools game, AnchorSystemModule settings) {
        this.engine = engine;
        this.game = game;
        this.settings = settings;
    }

    private int limit(ServerPlayer player) {
        double value = this.settings.limit().evaluate(FormulaContext.of(player));
        return Double.isFinite(value) ? (int) Math.clamp(Math.floor(value), 0, AnchorDataStorage.MAX_ANCHORS) : 0;
    }

    public CompletionStage<AnchorListOutput> query(ExecutionContext context) {
        return this.snapshot(context, Component.empty());
    }

    private CompletionStage<AnchorListOutput> snapshot(ExecutionContext context, Component feedback) {
        Saved saved = context.store().read(context.player(), data -> {
            AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
            return new Saved(storage.getAnchors(), storage.getNextTeleport());
        });
        return this.game.onPlayer(context, player -> new AnchorListOutput(saved.anchors(), this.limit(player),
                (int) Math.min(86400, Math.ceilDiv(Math.max(0, saved.nextTeleport() - this.time()), 20)), feedback));
    }

    public CompletionStage<AnchorListOutput> save(ExecutionContext context, AnchorSaveInput input) {
        String name = input.name().strip();
        if (name.codePointCount(0, name.length()) > 48 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == '§'))
            throw new AgentException(AgentFailure.INVALID_INPUT);
        return this.game.onPlayer(context, player -> {
            requireReady(player);
            if (!safe(player.level(), player, player.position())) throw new AgentException(AgentFailure.ANCHOR_UNSAFE);
            return new Capture(new TeleportAnchor(UUID.randomUUID().toString(), name, player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot()), this.limit(player));
        }).thenCompose(capture -> this.engine.maintenance(() -> {
            TeleportAnchor saved = context.store().update(context.player(), data -> {
                context.check();
                AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                TeleportAnchor anchor = capture.anchor();
                if (name.isBlank()) {
                    int number = 1;
                    Set<String> used = storage.getAnchors().stream().map(TeleportAnchor::name).collect(Collectors.toSet());
                    while (used.contains("#" + number)) number++;
                    anchor = new TeleportAnchor(anchor.id(), "#" + number, anchor.dimension(), anchor.position(), anchor.yaw(), anchor.pitch());
                }
                storage.add(anchor, capture.limit());
                return anchor;
            });
            return this.snapshot(context, Component.translatable("system.anchor.saved", saved.name()));
        })).thenCompose(stage -> stage);
    }

    public CompletionStage<AnchorListOutput> delete(ExecutionContext context, AnchorDeleteInput input) {
        TeleportAnchor removed = context.store().update(context.player(), data -> {
            context.check();
            AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
            TeleportAnchor anchor = storage.find(input.anchor());
            storage.remove(anchor.id());
            return anchor;
        });
        return this.snapshot(context, Component.translatable("system.anchor.deleted", removed.name()));
    }

    private long time() {
        return this.game.server().overworld().getGameTime();
    }

    public CompletionStage<AnchorListOutput> teleport(ExecutionContext context, AnchorTeleportInput input) {
        Destination destination = context.store().read(context.player(), data -> {
            AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
            TeleportAnchor anchor = storage.find(input.anchor());
            return new Destination(anchor, storage.getAnchors().indexOf(anchor), storage.getNextTeleport());
        });
        // The shared ticket expires automatically on failure, timeout or disconnect and is never persisted.
        return this.game.onPlayer(context, player -> {
            ServerLevel level = this.validate(player, destination);
            if (destination.nextTeleport() > this.time()) throw new AgentException(AgentFailure.ANCHOR_COOLDOWN);
            return level.getChunkSource().addTicketAndLoadWithRadius(SystemTicketTypes.ANCHOR.get(),
                    ChunkPos.containing(BlockPos.containing(destination.anchor().position())), 1);
        }).thenCompose(stage -> stage.toCompletableFuture().orTimeout(8, TimeUnit.SECONDS).handle((result, error) -> {
            if (error != null) throw new AgentException(AgentFailure.ANCHOR_LOADING_FAILED, error);
            if (!(result instanceof ChunkResult<?> loaded) || !loaded.isSuccess())
                throw new AgentException(AgentFailure.ANCHOR_LOADING_FAILED);
            return true;
        })).thenCompose(_ -> this.game.onPlayer(context, player -> {
            ServerLevel level = this.validate(player, destination);
            return new Landing(destination, this.landing(level, player, destination.anchor().position()), this.limit(player), this.time());
        })).thenCompose(landing -> this.engine.maintenance(() -> {
            Reservation reservation = this.reserve(context, landing);
            return this.perform(context, landing.position(), reservation);
        })).thenCompose(stage -> stage);
    }

    private Reservation reserve(ExecutionContext context, Landing landing) {
        return context.store().update(context.player(), data -> {
            context.check();
            AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
            TeleportAnchor anchor = storage.find(landing.destination().anchor().id());
            if (!anchor.equals(landing.destination().anchor())) throw new AgentException(AgentFailure.STALE_REFERENCE);
            int slot = storage.getAnchors().indexOf(anchor);
            if (slot >= landing.limit()) throw new AgentException(AgentFailure.ANCHOR_LOCKED);
            if (storage.getNextTeleport() > landing.time()) throw new AgentException(AgentFailure.ANCHOR_COOLDOWN);
            long ready = landing.time() + this.settings.cooldown() * 20L;
            Reservation reservation = new Reservation(new Destination(anchor, slot, ready), storage.getNextTeleport(), ready);
            storage.setNextTeleport(ready);
            return reservation;
        });
    }

    private CompletionStage<AnchorListOutput> perform(ExecutionContext context, Vec3 pos, Reservation reservation) {
        AtomicBoolean attempted = new AtomicBoolean();
        return this.game.onPlayer(context, player -> {
            ServerLevel level = this.validate(player, reservation.destination());
            if (!safe(level, player, pos)) throw new AgentException(AgentFailure.ANCHOR_UNSAFE);
            context.check();
            TeleportAnchor anchor = reservation.destination().anchor();
            attempted.set(true);
            if (!player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), anchor.yaw(), anchor.pitch(), true)) {
                attempted.set(false);
                throw new AgentException(AgentFailure.ANCHOR_REJECTED);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            return true;
        }).handle((_, error) -> this.engine.maintenance(() -> {
            if (error != null) {
                // An exception from a travel hook may follow an actual teleport. Never refund an unknown effect.
                if (attempted.get()) throw new AgentException(AgentFailure.EFFECT_UNKNOWN, error);
                context.store().update(context.player(), data -> {
                    AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                    if (storage.getNextTeleport() == reservation.reserved())
                        storage.setNextTeleport(reservation.previous());
                    return null;
                });
                throw new AgentException(AgentException.failure(error), error);
            }
            return this.snapshot(context, Component.translatable("system.anchor.arrived", reservation.destination().anchor().name()));
        })).thenCompose(stage -> stage).thenCompose(stage -> stage);
    }

    private ServerLevel validate(ServerPlayer player, Destination destination) {
        requireReady(player);
        if (destination.slot() >= this.limit(player)) throw new AgentException(AgentFailure.ANCHOR_LOCKED);
        ServerLevel level = this.game.server().getLevel(destination.anchor().dimension());
        if (level == null) throw new AgentException(AgentFailure.ANCHOR_DIMENSION_UNAVAILABLE);
        if (!this.settings.crossDimension() && player.level() != level)
            throw new AgentException(AgentFailure.ANCHOR_CROSS_DIMENSION);
        if (!player.canTeleport(player.level(), level)) throw new AgentException(AgentFailure.ANCHOR_REJECTED);
        Vec3 pos = destination.anchor().position();
        if (!pos.isFinite() || !level.getWorldBorder().isWithinBounds(pos.x, pos.z)
                || pos.y < level.getMinY() || pos.y >= level.getMaxY())
            throw new AgentException(AgentFailure.ANCHOR_UNSAFE);
        return level;
    }

    private static void requireReady(ServerPlayer player) {
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.isVehicle())
            throw new AgentException(AgentFailure.ANCHOR_PLAYER_STATE);
    }

    private Vec3 landing(ServerLevel level, ServerPlayer player, Vec3 saved) {
        if (safe(level, player, saved)) return saved;
        BlockPos origin = BlockPos.containing(saved);
        for (int radius = 0; radius <= 2; radius++)
            for (int dy : new int[]{0, 1, -1, 2, -2})
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        Vec3 candidate = new Vec3(origin.getX() + dx + 0.5, origin.getY() + dy, origin.getZ() + dz + 0.5);
                        if (safe(level, player, candidate)) return candidate;
                    }
        throw new AgentException(AgentFailure.ANCHOR_UNSAFE);
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, Vec3 pos) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        // Bound scans and check resident chunks before collision queries, which otherwise may load synchronously.
        if (!pos.isFinite() || box.getXsize() > 8 || box.getZsize() > 8 || box.getYsize() > 16) return false;
        for (int x = Math.floorDiv((int) Math.floor(box.minX) - 1, 16); x <= Math.floorDiv((int) Math.floor(box.maxX) + 1, 16); x++)
            for (int z = Math.floorDiv((int) Math.floor(box.minZ) - 1, 16); z <= Math.floorDiv((int) Math.floor(box.maxZ) + 1, 16); z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)
                || !level.noCollision(player, box) || level.containsAnyLiquid(box)
                || level.noBlockCollision(player, box.move(0, -0.1, 0))) return false;
        // Reject obvious hazards including those with no collision shape (fire and portals).
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.1, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(block);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                    || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) return false;
        }
        return true;
    }
}
