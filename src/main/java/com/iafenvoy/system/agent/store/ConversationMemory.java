package com.iafenvoy.system.agent.store;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.util.MiscUtil;
import com.iafenvoy.system.util.TypeDispatch;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, bounded projections; history never grants permissions or invents facts.
 */
public final class ConversationMemory {
    private static final TypeDispatch<Void, Fact> FACTS = new TypeDispatch<Void, Fact>((_, _) -> null)
            .register(PlayerSnapshotOutput.class, (_, v) -> fact("player", "", v.name(), ""))
            .register(StateOutput.class, (_, v) -> fact("state", "", "", v.state()))
            .register(QuestClaimOutput.class, (_, v) -> fact("quest_claim", "", "", v.state()))
            .register(RewardOutput.class, (_, v) -> reward("", "", v.reward()))
            .register(CreditReceiptOutput.class, (_, v) -> reward(v.entitlementId(), v.state(), v.reward()))
            .register(PurchaseReceiptOutput.class, (_, v) -> reward(v.entitlementId(), v.state(), v.reward()))
            .register(ResultTypes.SignInStatus.class, (_, v) -> fact("sign_in", "", "", Boolean.toString(v.signedIn())))
            .register(ResultTypes.ShopOrder.class, (_, v) -> fact("order", "", "", v.state()))
            .register(ResultTypes.ShopOffer.class, (_, v) -> fact("offer", v.id().map(Object::toString).orElse(""), "", ""))
            .register(LotteryPoolOutput.class, (_, v) -> fact("lottery_pool", v.id().toString(), v.label(), ""))
            .register(LotteryPrizeOutput.class, (_, v) -> reward("", "", v.reward()))
            .register(ResultTypes.QuestNotice.class, (_, v) -> fact("quest_notice", "", v.title(), ""))
            .register(StarterGiftOutput.class, (_, v) -> fact("starter_gift", "", "", Integer.toString(v.gifts().size())))
            .register(QuestSubmitOutput.class, (_, v) -> fact("quest_submit", v.questId(), v.title(), v.completed() ? "COMPLETED" : "INCOMPLETE"))
            .register(QuestStartupOutput.class, (_, v) -> fact("quest_startup", "", "", Integer.toString(v.total())))
            .register(QuestOutput.class, (_, v) -> new Fact("quest", v.id(), MiscUtil.clip(v.title(), 96), "",
                    v.rewards().stream().filter(r -> r.claimRef().isPresent()).limit(2).map(r -> new Reference(
                            r.id(), MiscUtil.clip(r.title(), 64), r.claimRef().orElseThrow(),
                            r.choices().orElse(List.of()).stream().limit(3).map(c -> MiscUtil.clip(c.title(), 48)).toList(), r.choiceOffset())).toList()));

    private ConversationMemory() {
    }

    public record Reference(String id, String label, String claimRef, List<String> choices, int choiceOffset) {
        static final Codec<Reference> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Reference::id),
                Codec.STRING.fieldOf("label").forGetter(Reference::label),
                Codec.STRING.optionalFieldOf("claimRef", "").forGetter(Reference::claimRef),
                Codec.STRING.listOf().optionalFieldOf("choices", List.of()).forGetter(Reference::choices),
                Codec.INT.optionalFieldOf("choiceOffset", 0).forGetter(Reference::choiceOffset)
        ).apply(i, Reference::new));
    }

    public record Fact(String kind, String id, String label, String state, List<Reference> references) {
        static final Codec<Fact> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Fact::kind),
                Codec.STRING.optionalFieldOf("id", "").forGetter(Fact::id),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Fact::label),
                Codec.STRING.optionalFieldOf("state", "").forGetter(Fact::state),
                Reference.CODEC.listOf().optionalFieldOf("references", List.of()).forGetter(Fact::references)
        ).apply(i, Fact::new));
    }

    public record Entry(String source, String text, String status, List<Fact> facts) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("source").forGetter(Entry::source),
                Codec.STRING.optionalFieldOf("text", "").forGetter(Entry::text),
                Codec.STRING.optionalFieldOf("status", "").forGetter(Entry::status),
                Fact.CODEC.listOf().optionalFieldOf("facts", List.of()).forGetter(Entry::facts)
        ).apply(i, Entry::new));
    }

    public static List<Entry> recent(PlayerData data, String currentRequest, int byteBudget) {
        List<Entry> selected = new ArrayList<>();
        int bytes = 2;
        for (int i = data.conversation.history.size() - 1; i >= 0 && selected.size() < 6; i--) {
            PlayerData.Message message = data.conversation.history.get(i);
            if (message.requestId().equals(currentRequest)) continue;
            Entry entry;
            if (message.role().equals("user")) entry = new Entry("USER_STATEMENT", message.text(), "", List.of());
            else {
                try {
                    AgentResult result = CodecIO.parse(AgentResult.CODEC, message.text());
                    List<Fact> facts = new ArrayList<>(result.cards().stream().limit(2).map(ConversationMemory::fields).filter(Objects::nonNull).toList());
                    entry = new Entry("HISTORICAL_RESULT_REQUIRES_REVALIDATION", "", result.messageKey(), List.copyOf(facts));
                    while (!facts.isEmpty() && size(entry) > byteBudget * 3 / 4) {
                        facts.removeLast();
                        entry = new Entry(entry.source(), "", entry.status(), List.copyOf(facts));
                    }
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            int length = size(entry) + 1;
            if (bytes + length > byteBudget) continue;
            bytes += length;
            selected.add(entry);
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private static int size(Entry entry) {
        return CodecIO.write(Entry.CODEC, entry).getBytes(StandardCharsets.UTF_8).length;
    }

    private static Fact fields(AgentResult.Card card) {
        return FACTS.apply(null, card.data().value());
    }

    private static Fact fact(String kind, String id, String label, String state) {
        return new Fact(kind, MiscUtil.clip(id, 96), MiscUtil.clip(label, 96), MiscUtil.clip(state, 96), List.of());
    }

    private static Fact reward(String entitlementId, String state, PlayerData.Reward reward) {
        return new Fact("reward", MiscUtil.clip(entitlementId, 96), "", MiscUtil.clip(state, 96),
                List.of(new Reference(reward.id().toString(), MiscUtil.clip(reward.label(), 96), "", List.of(), 0)));
    }
}
