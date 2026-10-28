package com.iafenvoy.system.agent.builtin.inbox;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.output.StateOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.module.inbox.InboxSystemModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers a credited entitlement; inventory mutation and durable finalization keep their original threads.
 */
public final class DeliverTool implements ToolRegistry.Handler<EntitlementInput, StateOutput> {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Map<UUID, Object> deliveryLocks = new ConcurrentHashMap<>();

    public DeliverTool(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
    }

    public ToolRegistry.Tool<EntitlementInput, StateOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.DELIVER, true, true, false, this);
    }

    @Override
    public CompletionStage<StateOutput> execute(ExecutionContext context, EntitlementInput input) {
        String id = input.entitlementId();
        Object lock = this.deliveryLocks.computeIfAbsent(context.player(), ignored -> new Object());
        synchronized (lock) {
            PlayerData.Delivery delivery = context.store().read(context.player(), data -> ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(id));
            if (delivery == null) throw new AgentException("NOT_AUTHORIZED");
            if ("DELIVERED".equals(delivery.state))
                return CompletableFuture.completedFuture(new StateOutput("DELIVERED"));
            if (!"PENDING".equals(delivery.state)) throw new AgentException("EFFECT_UNKNOWN");
            context.store().update(context.player(), data -> {
                ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(id).state = "DELIVERING";
                return null;
            });
            CompletableFuture<StateOutput> future = new CompletableFuture<>();
            this.game.onPlayer(context, player -> {
                List<ItemStack> stacks = new ArrayList<>();
                // create() validates bound components and the actual stack limit on the game thread.
                for (ItemStackTemplate item : delivery.reward.items()) {
                    ItemStack stack = item.create();
                    if (stack.isEmpty()) throw new AgentException("INVALID_REWARD");
                    stacks.add(stack);
                }
                int free = 0;
                for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++)
                    if (player.getInventory().getItem(slot).isEmpty()) free++;
                if (free < stacks.size()) throw new AgentException("INVENTORY_FULL");
                context.check();
                for (ItemStack stack : stacks) {
                    if (!player.getInventory().add(stack) || !stack.isEmpty())
                        throw new AgentException("EFFECT_UNKNOWN");
                }
                return new StateOutput("DELIVERED");
            }).whenComplete((result, error) -> {
                // This callback runs on the game thread: persistence is delegated below.
                this.engine.maintenance(() -> {
                    String code = error == null ? "" : AgentEngine.errorCode(error);
                    boolean untouched = Set.of("INVENTORY_FULL", "PLAYER_OFFLINE", "INVALID_REWARD", "EXPIRED", "CANCELLED", "BUSY").contains(code);
                    context.store().update(context.player(), data -> {
                        ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.get(id).state = error == null ? "DELIVERED" : untouched ? "PENDING" : "DELIVERY_UNKNOWN";
                        return null;
                    });
                    if (error == null) future.complete(result);
                    else future.completeExceptionally(new AgentException(code));
                    return null;
                }).exceptionally(failure -> {
                    future.completeExceptionally(failure);
                    return null;
                });
            });
            return future;
        }
    }
}
