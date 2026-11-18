package com.iafenvoy.system.assistant.anchor;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.commerce.CommerceCosts;
import com.iafenvoy.system.data.catalog.TeleportDestination;
import com.iafenvoy.system.data.input.AnchorDeleteInput;
import com.iafenvoy.system.data.input.AnchorRenameInput;
import com.iafenvoy.system.data.input.AnchorSaveInput;
import com.iafenvoy.system.data.input.AnchorTeleportInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.data.output.PaymentQuote;
import com.iafenvoy.system.data.store.AnchorTravel;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.AnchorSystemModule;
import com.iafenvoy.system.module.builtin.storage.AnchorDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.iafenvoy.system.registry.SystemTicketTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Worker-side storage transactions and game-thread validation; chunk loading never blocks a game tick.
 */
public final class TeleportAnchors {
    private record Saved(List<TeleportAnchor> anchors, long nextTeleport) {
    }

    private record Destination(TeleportAnchor anchor, int slot, long nextTeleport, Component label) {
        private Destination(TeleportAnchor anchor, int slot, long nextTeleport) {
            this(anchor, slot, nextTeleport, Component.literal(anchor.name()));
        }
    }

    private record Capture(TeleportAnchor anchor, int limit) {
    }

    private record Landing(Destination destination, Vec3 position, int limit, long time, CommerceCosts.Prepared payment,
                           String id) {
    }

    private record Reservation(Destination destination, long previous, long reserved, CommerceCosts.Prepared payment,
                               String id) {
    }

    private record Quote(String id, Destination destination, CommerceCosts.Prepared payment, long expires) {
    }

    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
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
        return this.game.onPlayer(context, player -> {
            List<AnchorListOutput.PublicDestination> destinations = player.registryAccess().lookupOrThrow(SystemResourceKeys.TELEPORT_DESTINATION).listElements()
                    .filter(holder -> this.settings.allows(player.registryAccess(), holder.key().identifier()) && CatalogAccess.visible(player, holder.value()))
                    .sorted(Comparator.<Holder.Reference<TeleportDestination>>comparingInt(holder -> holder.value().order()).reversed().thenComparing(holder -> holder.key().identifier()))
                    .limit(256).map(holder -> new AnchorListOutput.PublicDestination(holder.value().anchor(holder.key().identifier()), holder.value().label(),
                            holder.value().description(), !CatalogAccess.usable(player, holder.value()))).toList();
            return new AnchorListOutput(saved.anchors(), this.limit(player),
                    (int) Math.min(86400, Math.ceilDiv(Math.max(0, saved.nextTeleport() - this.time()), 20)), feedback, destinations, Optional.empty(), "");
        });
    }

    public CompletionStage<AnchorListOutput> save(ExecutionContext context, AnchorSaveInput input) {
        String name = input.name().strip();
        if (!name.isEmpty() && !TeleportAnchor.validName(name))
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
                    anchor = anchor.withName("#" + number);
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

    public CompletionStage<AnchorListOutput> rename(ExecutionContext context, AnchorRenameInput input) {
        TeleportAnchor renamed = context.store().update(context.player(), data -> {
            context.check();
            return ModuleData.get(data, AnchorSystemModule.STORAGE).rename(input.anchor(), input.name());
        });
        return this.snapshot(context, Component.translatable("system.anchor.renamed", renamed.name()));
    }

    private long time() {
        return this.game.server().overworld().getGameTime();
    }

    public CompletionStage<AnchorListOutput> teleport(ExecutionContext context, AnchorTeleportInput input) {
        if (context.store().read(context.player(), data -> ModuleData.get(data, AnchorSystemModule.STORAGE).getTravel().filter(AnchorTravel::blocked).isPresent()))
            throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
        if (input.quote().isPresent()) {
            context.requirePlayerAction(input);
            Quote quote = this.quotes.get(context.player());
            if (quote == null || !quote.id().equals(input.quote().get()) || !quote.destination().anchor().id().equals(input.anchor()) || quote.expires() < System.currentTimeMillis())
                throw new AgentException(AgentFailure.STALE_QUOTE);
            return this.travel(context, quote);
        }
        long next = context.store().read(context.player(), data -> ModuleData.get(data, AnchorSystemModule.STORAGE).getNextTeleport());
        CompletionStage<Destination> resolved;
        if (input.anchor().startsWith("public:")) {
            Identifier id = Identifier.tryParse(input.anchor().substring(7));
            if (id == null) throw new AgentException(AgentFailure.INVALID_INPUT);
            resolved = this.game.onPlayer(context, player -> {
                TeleportDestination destination = this.publicDestination(player, id);
                return new Destination(destination.anchor(id), -1, next, destination.label());
            });
        } else {
            Destination destination = context.store().read(context.player(), data -> {
                AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                TeleportAnchor anchor = storage.find(input.anchor());
                return new Destination(anchor, storage.getAnchors().indexOf(anchor), next);
            });
            resolved = CompletableFuture.completedFuture(destination);
        }
        return resolved.thenCompose(destination -> this.game.onPlayer(context, player -> {
            this.validate(player, destination);
            if (destination.nextTeleport() > this.time()) throw new AgentException(AgentFailure.ANCHOR_COOLDOWN);
            List<Cost> costs = new ArrayList<>(this.settings.costs());
            if (destination.slot() < 0)
                costs.addAll(this.publicDestination(player, Identifier.parse(destination.anchor().id().substring(7))).costs());
            CommerceCosts.Prepared payment = CommerceCosts.prepare(costs, player, CostContext.of(player, "system:anchor").with("system:destination", destination.anchor().id()));
            if (!payment.payable()) throw new AgentException(AgentFailure.INSUFFICIENT_COST);
            Quote quote = new Quote(UUID.randomUUID().toString(), destination, payment, System.currentTimeMillis() + 120000);
            this.quotes.entrySet().removeIf(entry -> entry.getValue().expires() < System.currentTimeMillis());
            if (this.quotes.size() >= 1024 && !this.quotes.containsKey(context.player()))
                throw new AgentException(AgentFailure.BUSY);
            this.quotes.put(context.player(), quote);
            return new AbstractMap.SimpleImmutableEntry<>(quote, !costs.isEmpty());
        })).thenCompose(value -> {
            Quote quote = value.getKey();
            if (!value.getValue()) return this.travel(context, quote);
            // snapshot() reads player storage, so continue on a worker rather than on the game thread.
            return this.engine.maintenance(() -> this.snapshot(context, Component.translatable("system.anchor.confirm_cost")))
                    .thenCompose(stage -> stage).thenApply(output -> new AnchorListOutput(output.anchors(), output.limit(), output.cooldown(), output.feedback(), output.destinations(),
                            Optional.of(new PaymentQuote(quote.id(), quote.destination().label(), quote.payment().getData())), quote.destination().anchor().id()));
        });
    }

    private CompletionStage<AnchorListOutput> travel(ExecutionContext context, Quote quote) {
        // Consume the quote before scheduling effects, including when cooldown is configured as zero.
        if (!this.quotes.remove(context.player(), quote)) throw new AgentException(AgentFailure.STALE_QUOTE);
        Destination destination = quote.destination();
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
            return new Landing(destination, this.landing(level, player, destination.anchor().position()), this.limit(player), this.time(), quote.payment(), quote.id());
        })).thenCompose(landing -> this.engine.maintenance(() -> {
            Reservation reservation = this.reserve(context, landing);
            return this.perform(context, landing.position(), reservation);
        })).thenCompose(stage -> stage);
    }

    private Reservation reserve(ExecutionContext context, Landing landing) {
        return context.store().update(context.player(), data -> {
            context.check();
            AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
            if (storage.getTravel().filter(AnchorTravel::blocked).isPresent())
                throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
            TeleportAnchor anchor = landing.destination().anchor();
            int slot = landing.destination().slot();
            if (slot >= 0) {
                TeleportAnchor current = storage.find(anchor.id());
                if (!current.equals(anchor)) throw new AgentException(AgentFailure.STALE_REFERENCE);
                slot = storage.getAnchors().indexOf(anchor);
                if (slot >= landing.limit()) throw new AgentException(AgentFailure.ANCHOR_LOCKED);
            }
            if (storage.getNextTeleport() > landing.time()) throw new AgentException(AgentFailure.ANCHOR_COOLDOWN);
            long ready = landing.time() + this.settings.cooldown() * 20L;
            Reservation reservation = new Reservation(new Destination(anchor, slot, ready, landing.destination().label()), storage.getNextTeleport(), ready, landing.payment(), landing.id());
            storage.setNextTeleport(ready);
            storage.setTravel(Optional.of(new AnchorTravel(landing.id(), anchor, landing.payment().getData(), AnchorTravel.State.PREPARED)));
            return reservation;
        });
    }

    private CompletionStage<AnchorListOutput> perform(ExecutionContext context, Vec3 pos, Reservation reservation) {
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean touched = new AtomicBoolean();
        return this.game.onPlayer(context, player -> {
            ServerLevel level = this.validate(player, reservation.destination());
            if (!safe(level, player, pos)) throw new AgentException(AgentFailure.ANCHOR_UNSAFE);
            context.check();
            TeleportAnchor anchor = reservation.destination().anchor();
            reservation.payment().pay(player, touched);
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
                context.store().update(context.player(), data -> {
                    AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                    if (storage.getTravel().filter(value -> value.id().equals(reservation.id())).isPresent()) {
                        if (touched.get() || attempted.get())
                            storage.setTravel(storage.getTravel().map(value -> value.state(AnchorTravel.State.UNKNOWN)));
                        else {
                            storage.setTravel(Optional.empty());
                            if (storage.getNextTeleport() == reservation.reserved())
                                storage.setNextTeleport(reservation.previous());
                        }
                    }
                    return null;
                });
                throw new AgentException(touched.get() || attempted.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(error), error);
            }
            context.store().update(context.player(), data -> {
                AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                storage.getTravel().filter(value -> value.id().equals(reservation.id())).ifPresent(value -> storage.setTravel(Optional.of(value.state(AnchorTravel.State.COMPLETED))));
                return null;
            });
            this.quotes.computeIfPresent(context.player(), (_, quote) -> quote.id().equals(reservation.id()) ? null : quote);
            return this.snapshot(context, Component.translatable("system.anchor.arrived", reservation.destination().label()));
        })).thenCompose(stage -> stage).thenCompose(stage -> stage);
    }

    private ServerLevel validate(ServerPlayer player, Destination destination) {
        requireReady(player);
        if (destination.slot() < 0) {
            Identifier id = Identifier.parse(destination.anchor().id().substring(7));
            if (!this.publicDestination(player, id).anchor(id).equals(destination.anchor()))
                throw new AgentException(AgentFailure.STALE_REFERENCE);
        }
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

    private TeleportDestination publicDestination(ServerPlayer player, Identifier id) {
        if (!this.settings.allows(player.registryAccess(), id)) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        TeleportDestination value = player.registryAccess().lookupOrThrow(SystemResourceKeys.TELEPORT_DESTINATION).getValue(id);
        if (value == null) throw new AgentException(AgentFailure.ANCHOR_NOT_FOUND);
        CatalogAccess.require(player, value);
        return value;
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
