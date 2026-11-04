package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.util.PersistHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record CreditReceiptOutput(String entitlementId, PersistHolder<Reward> reward,
                                  String state) implements CardData {
    public static final MapCodec<CreditReceiptOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(CreditReceiptOutput::entitlementId),
            PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(CreditReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(CreditReceiptOutput::state)
    ).apply(i, CreditReceiptOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "entitlement");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.reward").summary("system.result.reward");
        builder.reward("", this.reward())
                .put("entitlement_id", this.entitlementId()).put("state", this.state())
                .enable("delivered", "DELIVERED".equals(this.state()));
        if (this.state.equals("PENDING"))
            builder.serverButton("deliver", "system.action.deliver", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "deliver"),
                    Map.of("entitlement", this.entitlementId(), "kind", "credit"));
        return builder;
    }

    @Override
    public MapCodec<CreditReceiptOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(OutputFacts.reward(this.entitlementId(), this.state(), this.reward()));
    }

}
