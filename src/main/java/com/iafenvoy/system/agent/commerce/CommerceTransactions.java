package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.module.CommerceDataStorage;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.inbox.InboxSystemModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Payment touches inventory on the game thread; durable order and inbox writes run off it.
 */
public final class CommerceTransactions {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<PurchaseType, ModuleStorage<CommerceDataStorage>> storages = new EnumMap<>(PurchaseType.class);

    public CommerceTransactions(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
    }

    private ModuleStorage<CommerceDataStorage> storage(PurchaseType type) {
        return Objects.requireNonNull(this.storages.get(type), "Unregistered commerce module " + type);
    }

    /** Each commerce module restores only its own journal; disabling a module never discards orders. */
    public PlayerModule runtime(PurchaseType type, ModuleStorage<CommerceDataStorage> storage) {
        if (this.storages.putIfAbsent(type, storage) != null)
            throw new IllegalArgumentException("Duplicate commerce module " + type);
        return new PlayerModule() {
            @Override
            public Session login(net.minecraft.server.level.ServerPlayer player) {
                UUID uuid = player.getUUID();
                return data -> {
                    for (PlayerData.Purchase purchase : ModuleData.get(data, storage).purchases.values())
                        if ("PREPARED".equals(purchase.state) && !CommerceTransactions.this.active(uuid, purchase.id)) {
                            purchase.state = "PAYMENT_UNKNOWN";
                            purchase.error = "EFFECT_UNKNOWN";
                            PlayerData.Delivery delivery = ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(purchase.order.type().entitlement(purchase.id));
                            if (delivery != null) delivery.state = "PAYMENT_UNKNOWN";
                        }
                };
            }
        };
    }

    public boolean active(UUID player, String order) {
        return this.active.contains(player + ":" + order);
    }

    public CompletionStage<List<CommercePreviewOutput>> preview(ExecutionContext context, CommercePreviewInput input) {
        return this.game.onPlayer(context, player -> {
            CommerceCatalog catalog = this.engine.catalog();
            if (!catalog.revision().equals(input.revision())) throw new AgentException("STALE_QUOTE");
            List<CommercePreviewOutput> previews = new ArrayList<>();
            for (Identifier id : input.offers()) {
                CommerceCatalog.Offer offer = catalog.offers().stream().filter(value -> value.id().equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException("STALE_QUOTE"));
                CommerceCosts.Prepared payment = CommerceCosts.prepare(offer.costs(), player, PurchaseType.SHOP, id);
                Optional<String> reference = input.quote() && payment.isSuccess()
                        ? Optional.of(this.engine.commerceQuotes().create(context.player(), catalog, offer, payment).id()) : Optional.empty();
                previews.add(new CommercePreviewOutput(id, payment.getData(), payment.isSuccess(), reference));
            }
            for (Identifier id : input.lotteries()) {
                LotteryPool pool = catalog.lotteries().stream().filter(value -> value.id().equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException("STALE_QUOTE"));
                CommerceCosts.Prepared payment = CommerceCosts.prepare(pool.costs(), player, PurchaseType.LOTTERY, id);
                Optional<String> reference = input.quote() && payment.isSuccess()
                        ? Optional.of(this.engine.commerceQuotes().create(context.player(), catalog, pool, payment).id()) : Optional.empty();
                previews.add(new CommercePreviewOutput(id, payment.getData(), payment.isSuccess(), reference));
            }
            return List.copyOf(previews);
        });
    }

    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, String id, PurchaseType type) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new AgentException("INVALID_INPUT");
        }
        ModuleStorage<CommerceDataStorage> storage = this.storage(type);
        PlayerData.Purchase previous = context.store().read(context.player(), data -> ModuleData.get(data, storage).purchases.get(id));
        if (previous != null) {
            if (previous.order.type() != type) throw new AgentException("NOT_AUTHORIZED");
            if (previous.state.equals("COMPLETED")) return CompletableFuture.completedFuture(receipt(previous));
            throw new AgentException(previous.state.equals("FAILED") ? previous.error : "EFFECT_UNKNOWN");
        }
        CommerceQuotes.Quote quote = this.engine.commerceQuotes().require(context.player(), id, this.engine.catalog(), type);
        String key = context.player() + ":" + id;
        if (!this.active.add(key)) throw new AgentException("BUSY");
        try {
            context.ensureMemoryCurrent();
            context.store().update(context.player(), data -> {
                if (ModuleData.get(data, storage).purchases.containsKey(id)) throw new AgentException("EFFECT_UNKNOWN");
                if (ModuleData.get(data, storage).purchases.size() >= 4096 || ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.size() >= 4096)
                    throw new AgentException("STORAGE_FULL");
                PlayerData.Purchase purchase = new PlayerData.Purchase();
                purchase.id = id;
                purchase.order = quote.select();
                purchase.created = System.currentTimeMillis();
                ModuleData.get(data, storage).purchases.put(id, purchase);
                // Reserve the reward before payment; delivery becomes available only after payment is recorded.
                PlayerData.Delivery held = new PlayerData.Delivery(type.entitlement(id), purchase.order.reward());
                held.state = "PAYMENT_PENDING";
                ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.put(held.id, held);
                return null;
            });
        } catch (RuntimeException e) {
            this.active.remove(key);
            throw e;
        }

        // Once payment may have changed external state, failures become UNKNOWN and must not auto-retry.
        AtomicBoolean touched = new AtomicBoolean();
        CompletionStage<Boolean> payment = this.game.onPlayer(context, player -> {
            this.engine.commerceQuotes().require(context.player(), id, this.engine.catalog(), type);
            // Reject the whole pool before payment if any prize cannot be created; failed draws cannot bias its odds.
            for (LotteryPool.Entry entry : quote.entries())
                for (ItemStackTemplate item : entry.reward().items())
                    if (item.create().isEmpty()) throw new AgentException("INVALID_REWARD");
            context.check();
            quote.payment().pay(player, touched);
            return true;
        });

        CompletableFuture<PurchaseReceiptOutput> result = new CompletableFuture<>();
        payment.whenComplete((paid, error) -> this.engine.maintenance(() -> {
            String code = error == null ? "" : touched.get() ? "EFFECT_UNKNOWN" : AgentEngine.errorCode(error);
            PurchaseReceiptOutput committed = context.store().update(context.player(), data -> {
                PlayerData.Purchase order = ModuleData.get(data, storage).purchases.get(id);
                if (error == null) {
                    String entitlement = type.entitlement(id);
                    ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(entitlement).state = "PENDING";
                    order.state = "COMPLETED";
                } else {
                    order.state = touched.get() ? "PAYMENT_UNKNOWN" : "FAILED";
                    order.error = code;
                    if (touched.get()) ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(type.entitlement(id)).state = "PAYMENT_UNKNOWN";
                    else ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.remove(type.entitlement(id));
                }
                return receipt(order);
            });
            if (error == null) result.complete(committed);
            else result.completeExceptionally(new AgentException(code));
            return null;
        }).exceptionally(failure -> {
            if (AgentEngine.errorCode(failure).equals("CANCELLED")) {
                result.completeExceptionally(new AgentException("CANCELLED"));
                return null;
            }
            SystemMod.LOGGER.error("Commerce order finalization failed for player {} order {}", context.player(), id, failure);
            result.completeExceptionally(new AgentException(touched.get() ? "EFFECT_UNKNOWN" : "STORAGE_UNAVAILABLE"));
            return null;
        }));
        return result.whenComplete((ignored, error) -> this.active.remove(key));
    }

    private static PurchaseReceiptOutput receipt(PlayerData.Purchase purchase) {
        return new PurchaseReceiptOutput(purchase.id, purchase.order.type().entitlement(purchase.id), purchase.order.reward(), purchase.state);
    }

}
