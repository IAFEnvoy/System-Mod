package com.iafenvoy.system.module.lottery;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.agent.builtin.output.LotteryPoolOutput;
import com.iafenvoy.system.agent.builtin.output.LotteryPrizeOutput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.screen.card.CardContext;
import com.iafenvoy.system.util.MiscUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.math.MathContext;

import static com.iafenvoy.system.screen.gui.AssistantView.priceParameters;
import static com.iafenvoy.system.screen.gui.AssistantView.rewardParameters;
import static com.iafenvoy.system.speech.tts.SpeechText.primary;

public final class LotteryClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return LotterySystemModule.ID;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.workspace("lottery", "nav_lottery", workspace -> {
            workspace.labels("nav_lottery", "lottery_search", "lottery_refresh", "lottery_text");
            workspace.input("lottery_query", "system.ui.lottery_placeholder", "lottery_search");
            workspace.click("nav_lottery", () -> {
                workspace.show("lottery");
                AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput("", 0)), "");
            });
            workspace.click("lottery_refresh", () -> AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput("", 0)), ""));
            workspace.click("lottery_search", () -> AssistantClient.send(ResultTypes.LOTTERY_ACTION.of(new LotteryQueryInput(workspace.query("lottery_query"), 0)), ""));
        });
        module.speech("system.result.lottery_drawn", LotteryClientModule::lottery);
        module.cards().register(LotteryPoolOutput.class, (builder, pool) -> {
            builder.put("label", pool.label()).put("id", pool.id().toString()).put("prize_count", pool.prizeCount())
                    .put("preview_count", pool.prizes().size()).put("payment_available", pool.paymentAvailable())
                    .put("free", pool.price().isEmpty()).list("prizes", pool.prizes(), (prefix, prize) -> prizeParameters(builder, prefix, prize));
            pool.price().ifPresent(price -> priceParameters(builder, price));
        });
        module.cards().register(LotteryPrizeOutput.class, (builder, prize) -> prizeParameters(builder, "", prize));
    }

    private static void prizeParameters(CardContext.Builder builder, String prefix, LotteryPrizeOutput prize) {
        rewardParameters(builder, prefix, prize.reward()).put(prefix + "chance", chance(prize));
    }

    private static String chance(LotteryPrizeOutput prize) {
        return BigDecimal.valueOf(prize.weight()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(prize.totalWeight()), new MathContext(4)).stripTrailingZeros().toPlainString();
    }

    private static String lottery(AgentResult result) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof PurchaseReceiptOutput receipt)
                return Component.translatable("system.tts.message.lottery_drawn", MiscUtil.clip(receipt.reward().label(), 128)).getString();
        return primary(result);
    }
}
