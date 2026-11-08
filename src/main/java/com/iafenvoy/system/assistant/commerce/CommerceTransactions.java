package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentLifecycle;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.output.PurchaseReceiptOutput;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Purchase;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.module.builtin.storage.InboxDataStorage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
    private final CommerceCatalogs catalogs;
    private final CommerceQuotes quotes = new CommerceQuotes();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<PurchaseType, ModuleStorage<CommerceDataStorage>> storages = new EnumMap<>(PurchaseType.class);

    public CommerceTransactions(AgentEngine engine, MinecraftTools game, CommerceCatalogs catalogs) {
        this.engine = engine;
        this.game = game;
        this.catalogs = catalogs;
        engine.lifecycle(new AgentLifecycle() {
            @Override
            public void revoked(UUID player) {
                CommerceTransactions.this.quotes.clear(player);
            }

            @Override
            public void accessChanged(UUID player) {
                CommerceTransactions.this.quotes.clear(player);
            }
        });
    }

    private ModuleStorage<CommerceDataStorage> storage(PurchaseType type) {
        return Objects.requireNonNull(this.storages.get(type), "Unregistered commerce module " + type);
    }

    /**
     * Each commerce module restores only its own journal; disabling a module never discards orders.
     */
    public PlayerModule runtime(PurchaseType type, ModuleStorage<CommerceDataStorage> storage) {
        ModuleStorage<CommerceDataStorage> previous = this.storages.putIfAbsent(type, storage);
        if (previous != null && previous != storage)
            throw new IllegalArgumentException("Duplicate commerce module " + type);
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                UUID uuid = player.getUUID();
                return data -> {
                    CommerceDataStorage commerce = ModuleData.get(data, storage);
                    commerce.streamPurchases()
                            .filter(purchase -> "PREPARED".equals(purchase.state) && !CommerceTransactions.this.active(uuid, purchase.id))
                            .forEach(purchase -> {
                                commerce.setPurchaseState(purchase.id, "PAYMENT_UNKNOWN", "EFFECT_UNKNOWN");
                                InboxDataStorage inbox = ModuleData.get(data, InboxSystemModule.STORAGE);
                                String entitlement = purchase.order.type().entitlement(purchase.id);
                                if (inbox.getDelivery(entitlement) != null)
                                    inbox.setDeliveryState(entitlement, "PAYMENT_UNKNOWN");
                            });
                };
            }
        };
    }

    public boolean active(UUID player, String order) {
        return this.active.contains(player + ":" + order);
    }

    public CompletionStage<List<CommercePreviewOutput>> preview(ExecutionContext context, CommercePreviewInput input) {
        return this.game.onPlayer(context, player -> {
            CommerceCatalog catalog = this.catalogs.available(context.player());
            if (!catalog.revision().equals(input.revision())) throw new AgentException("STALE_QUOTE");
            List<CommercePreviewOutput> previews = new ArrayList<>();
            for (Identifier id : input.offers()) {
                Holder<ShopOfferEntry> offer = catalog.offers().stream().filter(value -> HolderHelper.id(value).equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException("STALE_QUOTE"));
                CommerceCosts.Prepared payment = CommerceCosts.prepare(offer.value().costs(), player, PurchaseType.SHOP, id);
                Optional<String> reference = input.quote() && payment.isSuccess()
                        ? Optional.of(this.quotes.create(context.player(), catalog, offer, payment).id()) : Optional.empty();
                previews.add(new CommercePreviewOutput(id, payment.getData(), payment.isSuccess(), reference));
            }
            for (Identifier id : input.lotteries()) {
                Holder<LotteryPool> pool = catalog.lotteries().stream().filter(value -> HolderHelper.id(value).equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException("STALE_QUOTE"));
                CommerceCosts.Prepared payment = CommerceCosts.prepare(pool.value().costs(), player, PurchaseType.LOTTERY, id);
                Optional<String> reference = input.quote() && payment.isSuccess()
                        ? Optional.of(this.quotes.createPool(context.player(), catalog, pool, payment).id()) : Optional.empty();
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
        Purchase previous = context.store().read(context.player(), data -> ModuleData.get(data, storage).getPurchase(id));
        if (previous != null) {
            if (previous.order.type() != type) throw new AgentException("NOT_AUTHORIZED");
            if (previous.state.equals("COMPLETED")) return CompletableFuture.completedFuture(receipt(previous));
            throw new AgentException(previous.state.equals("FAILED") ? previous.error : "EFFECT_UNKNOWN");
        }
        CommerceQuotes.Quote quote = this.quotes.require(context.player(), id, this.catalogs.available(context.player()), type);
        String key = context.player() + ":" + id;
        if (!this.active.add(key)) throw new AgentException("BUSY");
        try {
            context.ensureMemoryCurrent();
            context.store().update(context.player(), data -> {
                CommerceDataStorage commerce = ModuleData.get(data, storage);
                if (commerce.getPurchase(id) != null) throw new AgentException("EFFECT_UNKNOWN");
                commerce.assertPurchaseCount();
                InboxDataStorage inbox = ModuleData.get(data, InboxSystemModule.STORAGE);
                inbox.assertDeliveryCount();
                Purchase purchase = new Purchase();
                purchase.id = id;
                purchase.order = quote.select();
                purchase.created = System.currentTimeMillis();
                commerce.putPurchase(id, purchase);
                // Reserve the reward before payment; delivery becomes available only after payment is recorded.
                Delivery held = new Delivery(type.entitlement(id), purchase.order.reward());
                held.state = "PAYMENT_PENDING";
                inbox.putDelivery(held.id, held);
                return null;
            });
        } catch (RuntimeException e) {
            this.active.remove(key);
            throw e;
        }

        // Once payment may have changed external state, failures become UNKNOWN and must not auto-retry.
        AtomicBoolean touched = new AtomicBoolean();
        CompletionStage<Boolean> payment = this.game.onPlayer(context, player -> {
            this.quotes.require(context.player(), id, this.catalogs.available(context.player()), type);
            // Reject the whole pool before payment if any prize cannot be created; failed draws cannot bias its odds.
            for (LotteryPool.Entry entry : quote.entries())
                for (ItemStackTemplate item : entry.reward().value().items())
                    if (item.create().isEmpty()) throw new AgentException("INVALID_REWARD");
            context.check();
            quote.payment().pay(player, touched);
            return true;
        });

        CompletableFuture<PurchaseReceiptOutput> result = new CompletableFuture<>();
        payment.whenComplete((paid, error) -> this.engine.maintenance(() -> {
            String code = error == null ? "" : touched.get() ? "EFFECT_UNKNOWN" : AgentEngine.errorCode(error);
            PurchaseReceiptOutput committed = context.store().update(context.player(), data -> {
                CommerceDataStorage commerce = ModuleData.get(data, storage);
                Purchase order = commerce.getPurchase(id);
                InboxDataStorage inbox = ModuleData.get(data, InboxSystemModule.STORAGE);
                if (error == null) {
                    String entitlement = type.entitlement(id);
                    inbox.setDeliveryState(entitlement, "PENDING");
                    commerce.setPurchaseState(id, "COMPLETED", order.error);
                } else {
                    commerce.setPurchaseState(id, touched.get() ? "PAYMENT_UNKNOWN" : "FAILED", code);
                    if (touched.get())
                        inbox.setDeliveryState(type.entitlement(id), "PAYMENT_UNKNOWN");
                    else inbox.removeDelivery(type.entitlement(id));
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

    private static PurchaseReceiptOutput receipt(Purchase purchase) {
        return new PurchaseReceiptOutput(purchase.id, purchase.order.type().entitlement(purchase.id), purchase.order.reward(), purchase.state,
                purchase.order.type() == PurchaseType.LOTTERY ? Optional.of(purchase.order.source()) : Optional.empty());
    }

}
