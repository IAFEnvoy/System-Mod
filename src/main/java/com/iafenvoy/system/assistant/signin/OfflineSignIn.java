package com.iafenvoy.system.assistant.signin;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.progression.PlayerProgression;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.progression.ProgressionEstimate;
import com.iafenvoy.system.data.progression.ProgressionSettings;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Fully known package totals only; opaque effects must declare an explicit offline value.
 */
public final class OfflineSignIn {
    private final CommerceCatalogs catalogs;
    private final ProgressionSettings settings;

    public OfflineSignIn(CommerceCatalogs catalogs, ProgressionSettings settings) {
        this.catalogs = catalogs;
        this.settings = settings;
    }

    public Selection candidates(ExecutionContext context) {
        return this.catalogs.access().read(context, this::candidates);
    }

    private Selection candidates(ServerPlayer player) {
        CommerceCatalogs.View view = this.catalogs.view(player);
        ProgressionEstimate progression = PlayerProgression.estimate(player, this.settings);
        List<Priced> priced = new ArrayList<>();
        for (Holder<SignInRewardEntry> holder : view.catalog().rewards()) {
            if (view.lockedRewards().contains(HolderHelper.id(holder))) continue;
            OptionalDouble value = value(player, holder.value());
            if (value.isPresent() && value.getAsDouble() >= progression.minimum() && value.getAsDouble() <= progression.upper())
                priced.add(new Priced(holder.value().reward(HolderHelper.id(holder)), value.getAsDouble()));
        }
        List<Priced> selected;
        if (progression.value() <= 0) {
            // Empty backpacks and recipe books bootstrap from the cheapest configured package, never the pool maximum.
            double minimum = priced.stream().mapToDouble(Priced::value).filter(value -> value >= progression.lower()).min().orElse(-1);
            selected = priced.stream().filter(entry -> entry.value == minimum).toList();
        } else {
            selected = priced.stream().filter(entry -> entry.value >= progression.lower()).toList();
            if (selected.isEmpty()) {
                // Sparse reward tables may provide a smaller supply; never widen the upper ceiling.
                double nearest = priced.stream().mapToDouble(Priced::value).max().orElse(-1);
                selected = priced.stream().filter(entry -> entry.value == nearest).toList();
            }
        }
        return new Selection(view.catalog().revision(), progression, selected.stream().map(Priced::reward).toList());
    }

    private static OptionalDouble value(ServerPlayer player, SignInRewardEntry reward) {
        if (reward.offlineValue().isPresent()) return OptionalDouble.of(reward.offlineValue().get());
        double total = 0;
        for (ItemStackTemplate item : reward.items()) {
            OptionalDouble value = value(player, item.create(), item.count());
            if (value.isEmpty()) return OptionalDouble.empty();
            total += value.getAsDouble();
        }
        for (SpaceStack stack : reward.contents()) {
            if (!(stack.content() instanceof ItemSpaceContent(ItemStackTemplate item1))) return OptionalDouble.empty();
            OptionalDouble value = value(player, item1.create(), stack.amount());
            if (value.isEmpty()) return OptionalDouble.empty();
            total += value.getAsDouble();
        }
        return Double.isFinite(total) && total > 0 ? OptionalDouble.of(total) : OptionalDouble.empty();
    }

    private static OptionalDouble value(ServerPlayer player, ItemStack stack, long count) {
        OptionalDouble unit = ItemValues.get(player.level().getServer(), stack);
        if (unit.isEmpty()) return OptionalDouble.empty();
        double total = unit.getAsDouble() * count;
        return Double.isFinite(total) && total > 0 ? OptionalDouble.of(total) : OptionalDouble.empty();
    }

    private record Priced(PersistHolder<Reward> reward, double value) {
    }

    public record Selection(String revision, ProgressionEstimate progression, List<PersistHolder<Reward>> candidates) {
        public Selection {
            candidates = List.copyOf(candidates);
        }
    }
}
