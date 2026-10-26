package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ShopPurchaseInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.ShopQuotes;
import com.iafenvoy.system.agent.commerce.SystemCoinPayment;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.runtime.reload.CatalogReloadListener;
import com.iafenvoy.system.runtime.server.MinecraftTools;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Payment touches inventory on the game thread; durable order and inbox writes run off it.
 */
public final class ShopPurchaseTool implements ToolRegistry.Handler<ShopPurchaseInput, PurchaseReceiptOutput> {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public ShopPurchaseTool(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
    }

    public boolean active(UUID player, String order) {
        return this.active.contains(player + ":" + order);
    }

    public ToolRegistry.Tool<ShopPurchaseInput, PurchaseReceiptOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.SHOP_PURCHASE, true, true, false, this);
    }

    @Override
    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, ShopPurchaseInput input) {
        String id = input.quoteRef();
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new AgentException("INVALID_INPUT");
        }
        PlayerData.Purchase previous = context.store().read(context.player(), data -> data.purchases.get(id));
        if (previous != null) {
            if (previous.state.equals("COMPLETED")) return CompletableFuture.completedFuture(receipt(previous));
            throw new AgentException(previous.state.equals("FAILED") ? previous.error : "EFFECT_UNKNOWN");
        }
        ShopQuotes.Quote quote = this.engine.shopQuotes().require(context.player(), id, this.engine.catalog());
        CommerceCatalog.Offer offer = quote.offer();
        SystemCoinPayment coins = this.engine.coinPayment();
        boolean useCoins = offer.price().type().equals("SYSTEM_COIN");
        if (useCoins && !coins.available()) throw new AgentException("CURRENCY_UNAVAILABLE");
        String key = context.player() + ":" + id;
        if (!this.active.add(key)) throw new AgentException("BUSY");
        try {
            context.ensureMemoryCurrent();
            context.store().update(context.player(), data -> {
                if (data.purchases.containsKey(id)) throw new AgentException("EFFECT_UNKNOWN");
                if (data.purchases.size() >= 4096 || data.inbox.size() >= 4096)
                    throw new AgentException("STORAGE_FULL");
                PlayerData.Purchase purchase = new PlayerData.Purchase();
                purchase.id = id;
                purchase.offer = offer;
                purchase.created = System.currentTimeMillis();
                data.purchases.put(id, purchase);
                // Reserve the reward before payment; delivery becomes available only after payment is recorded.
                PlayerData.Delivery held = new PlayerData.Delivery("shop:" + id, offer.reward());
                held.state = "PAYMENT_PENDING";
                data.inbox.put(held.id, held);
                return null;
            });
        } catch (RuntimeException e) {
            this.active.remove(key);
            throw e;
        }

        // Once payment may have changed external state, failures become UNKNOWN and must not auto-retry.
        AtomicBoolean touched = new AtomicBoolean();
        CompletionStage<Boolean> payment = this.game.onPlayer(context, player -> {
            this.engine.shopQuotes().require(context.player(), id, this.engine.catalog());
            try {
                CatalogReloadListener.validateItems(offer.items(), true);
            } catch (IllegalArgumentException e) {
                throw new AgentException("INVALID_REWARD");
            }
            if (!useCoins) debitItems(player, offer.price().items(), context, touched);
            return !useCoins;
        });
        if (useCoins) payment = payment.thenCompose(ignored -> this.engine.maintenance(() -> {
            context.ensureMemoryCurrent();
            this.engine.shopQuotes().require(context.player(), id, this.engine.catalog());
            if (!coins.available()) throw new AgentException("CURRENCY_UNAVAILABLE");
            touched.set(true);
            return coins.debit(context.player(), offer.price().coins(), "shop:" + context.player() + ":" + id);
        }).thenCompose(stage -> stage).thenApply(paid -> {
            if (Boolean.FALSE.equals(paid)) {
                touched.set(false);
                throw new AgentException("INSUFFICIENT_FUNDS");
            }
            if (!Boolean.TRUE.equals(paid)) throw new AgentException("EFFECT_UNKNOWN");
            return true;
        }));

        CompletableFuture<PurchaseReceiptOutput> result = new CompletableFuture<>();
        payment.whenComplete((paid, error) -> this.engine.maintenance(() -> {
            String code = error == null ? "" : touched.get() ? "EFFECT_UNKNOWN" : AgentEngine.errorCode(error);
            PurchaseReceiptOutput committed = context.store().update(context.player(), data -> {
                PlayerData.Purchase order = data.purchases.get(id);
                if (error == null) {
                    String entitlement = "shop:" + id;
                    data.inbox.get(entitlement).state = "PENDING";
                    order.state = "COMPLETED";
                } else {
                    order.state = touched.get() ? "PAYMENT_UNKNOWN" : "FAILED";
                    order.error = code;
                    if (touched.get()) data.inbox.get("shop:" + id).state = "PAYMENT_UNKNOWN";
                    else data.inbox.remove("shop:" + id);
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
            SystemMod.LOGGER.error("Shop order finalization failed for player {} order {}", context.player(), id, failure);
            result.completeExceptionally(new AgentException(touched.get() ? "EFFECT_UNKNOWN" : "STORAGE_UNAVAILABLE"));
            return null;
        }));
        return result.whenComplete((ignored, error) -> this.active.remove(key));
    }

    private static void debitItems(ServerPlayer player, List<PlayerData.Item> costs, ExecutionContext context, AtomicBoolean touched) {
        Map<String, Integer> required = new LinkedHashMap<>();
        costs.forEach(item -> required.merge(item.id(), item.count(), Integer::sum));
        List<Removal> plan = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new AgentException("INVALID_REWARD");
            ItemStack template = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            int remaining = entry.getValue();
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) continue;
                int count = Math.min(remaining, stack.getCount());
                plan.add(new Removal(stack, count));
                remaining -= count;
            }
            if (remaining != 0) throw new AgentException("INSUFFICIENT_ITEMS");
        }
        context.check();
        // Validation and mutation happen in one callback, with no yield between them.
        touched.set(true);
        for (Removal removal : plan) removal.stack().shrink(removal.count());
        player.getInventory().setChanged();
    }

    private static PurchaseReceiptOutput receipt(PlayerData.Purchase purchase) {
        return new PurchaseReceiptOutput(purchase.id, "shop:" + purchase.id, purchase.offer.reward(), purchase.state);
    }

    private record Removal(ItemStack stack, int count) {
    }
}
