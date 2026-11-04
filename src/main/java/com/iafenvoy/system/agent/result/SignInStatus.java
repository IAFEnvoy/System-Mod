package com.iafenvoy.system.agent.result;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.util.PersistHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Daily sign-in outcome with today's candidate rewards.
 */
public record SignInStatus(String date, boolean signedIn, List<PersistHolder<Reward>> candidates, boolean historySaved,
                           boolean externalModel) implements CardData {
    public static final MapCodec<SignInStatus> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("date").forGetter(SignInStatus::date),
            Codec.BOOL.fieldOf("signedIn").forGetter(SignInStatus::signedIn),
            PersistHolder.codec(Reward.CODEC).listOf().fieldOf("candidates").forGetter(SignInStatus::candidates),
            Codec.BOOL.fieldOf("historySaved").forGetter(SignInStatus::historySaved),
            Codec.BOOL.fieldOf("externalModel").forGetter(SignInStatus::externalModel)
    ).apply(i, SignInStatus::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "sign_in_status");

    public SignInStatus {
        candidates = List.copyOf(candidates);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.result.sign_in_status");
        builder.put("date", this.date()).put("signed_in", this.signedIn())
                .put("history_saved", this.historySaved()).put("external_model", this.externalModel())
                .list("candidates", this.candidates(), builder::reward);
        return builder;
    }

    @Override
    public MapCodec<SignInStatus> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("sign_in", "", "", Boolean.toString(this.signedIn()), List.of()));
    }

}
