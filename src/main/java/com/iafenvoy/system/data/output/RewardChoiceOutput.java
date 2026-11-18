package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.space.ChoiceReceipt;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Map;

public record RewardChoiceOutput(String entry, long ticket, Component name, List<Reward> options, int selected,
                                 boolean settled) implements CardData {
    public static final MapCodec<RewardChoiceOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entry").forGetter(RewardChoiceOutput::entry),
            Codec.LONG.fieldOf("ticket").forGetter(RewardChoiceOutput::ticket),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(RewardChoiceOutput::name),
            Reward.SNAPSHOT_CODEC.codec().listOf(1, 36).fieldOf("options").forGetter(RewardChoiceOutput::options),
            Codec.INT.fieldOf("selected").forGetter(RewardChoiceOutput::selected),
            Codec.BOOL.fieldOf("settled").forGetter(RewardChoiceOutput::settled)
    ).apply(i, RewardChoiceOutput::new));

    public RewardChoiceOutput {
        name = name.copy();
        options = List.copyOf(options);
    }

    public RewardChoiceOutput(ChoiceReceipt receipt) {
        // Replays need only the selected output, not another copy of every discarded candidate.
        this(receipt.entry(), receipt.ticket(), receipt.name(), List.of(receipt.reward()), 0, true);
    }

    @Override
    public MapCodec<RewardChoiceOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "reward_choice"))
                .title("system.choice.title").summary(this.settled ? "system.choice.settled" : "system.choice.hint")
                .put("name", this.name).enable("settled", this.settled).enable("preview", this.selected >= 0);
        if (this.selected >= 0) builder.put("selected", this.options.get(this.selected).label())
                .put("selected_contents", CardBuilder.join(this.options.get(this.selected).stacks().stream().map(SpaceStack::label).toList(), " · "));
        builder.list("options", this.options, (prefix, reward) -> builder.put(prefix + "label", reward.label())
                .put(prefix + "icon", reward.stacks().getFirst().icon())
                .put(prefix + "contents", CardBuilder.join(reward.stacks().stream().map(SpaceStack::label).toList(), " · ")));
        Identifier action = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "reward_choice");
        for (int index = 0; !this.settled && index < this.options.size(); index++)
            builder.serverButton("option." + index, "system.choice.preview", action,
                    Map.of("entry", this.entry, "ticket", Long.toString(this.ticket), "option", Integer.toString(index)),
                    CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("label", Component.translatable("system.choice.option", this.options.get(index).label(),
                            CardBuilder.join(this.options.get(index).stacks().stream().map(SpaceStack::label).toList(), " · ")));
        if (!this.settled && this.selected >= 0)
            builder.serverButton("confirm", "system.choice.confirm", action, Map.of("entry", this.entry, "ticket", Long.toString(this.ticket),
                    "option", Integer.toString(this.selected), "confirm", "true")).actionParameter("footer", true);
        return builder.serverButton("space", "system.ui.nav_inbox", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), Map.of()).actionParameter("footer", true);
    }
}
