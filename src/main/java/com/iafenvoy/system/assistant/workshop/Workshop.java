package com.iafenvoy.system.assistant.workshop;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.commerce.CommerceCosts;
import com.iafenvoy.system.data.output.ItemInspectionOutput;
import com.iafenvoy.system.data.output.RepairQuoteOutput;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.PlayerModule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Game-thread quote lifetime; the agent journal persists write intent before a confirmation reaches this service. */
public final class Workshop implements PlayerModule {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Map<UUID, Quote> quotes = new HashMap<>();

    public Workshop(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
    }

    public CompletionStage<RepairQuoteOutput> quote(ExecutionContext context, Identifier owner, List<Cost> costs) {
        return this.game.onPlayer(context, player -> {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) throw new AgentException(AgentFailure.EMPTY_HAND);
            if (!stack.isDamageableItem() || !stack.isDamaged() || stack.getCount() != 1)
                throw new AgentException(AgentFailure.ITEM_NOT_DAMAGED);
            int slot = player.getInventory().getSelectedSlot();
            CostContext paymentContext = CostContext.of(player, "system:repair").with("system:source", owner)
                    .withItemTarget(new RepairInventory(player.getInventory(), slot));
            CommerceCosts.Prepared payment = CommerceCosts.prepare(costs, player, paymentContext);
            if (payment.isFailure()) throw new AgentException(payment.failure().orElseThrow());
            ItemStackTemplate snapshot = ItemStackTemplate.fromNonEmptyStack(stack);
            Quote quote = new Quote(UUID.randomUUID().toString(), owner, slot, stack, snapshot, payment,
                    System.currentTimeMillis() + 120000);
            this.quotes.put(context.player(), quote);
            return new RepairQuoteOutput(quote.id(), stack.getHoverName().copy(), snapshot, stack.getDamageValue(), payment.getData());
        });
    }

    public CompletionStage<ItemInspectionOutput> confirm(ExecutionContext context, String id) {
        return this.game.onPlayer(context, player -> {
            Quote quote = this.quotes.get(context.player());
            if (quote == null || !quote.id().equals(id) || quote.expires() < System.currentTimeMillis()
                    || !this.engine.moduleEnabled(context.player(), quote.owner()))
                throw new AgentException(AgentFailure.STALE_QUOTE);
            ItemStack stack = player.getMainHandItem();
            if (quote.slot() != player.getInventory().getSelectedSlot() || stack != quote.target()
                    || !ItemStack.matches(stack, quote.snapshot()))
                throw new AgentException(AgentFailure.STALE_QUOTE);
            // Consume before charging: another request, cancellation or uncertain payment can never charge this quote twice.
            this.quotes.remove(context.player());
            AtomicBoolean touched = new AtomicBoolean();
            try {
                quote.payment().pay(player, touched);
                if (player.getMainHandItem() != stack || !ItemStack.matches(stack, quote.snapshot()))
                    throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
                stack.setDamageValue(0);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                return ItemInspectionOutput.of(stack);
            } catch (RuntimeException error) {
                if (touched.get()) throw new AgentException(AgentFailure.EFFECT_UNKNOWN, error);
                throw error;
            }
        });
    }

    @Override
    public Session login(ServerPlayer player) {
        return new Session() {
            @Override
            public void initialize(PlayerData data) {
            }

            @Override
            public void close() {
                Workshop.this.quotes.remove(player.getUUID());
            }
        };
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() < now);
    }

    @Override
    public void close() {
        this.quotes.clear();
    }

    private record Quote(String id, Identifier owner, int slot, ItemStack target, ItemStackTemplate snapshot,
                         CommerceCosts.Prepared payment, long expires) {
    }
}
