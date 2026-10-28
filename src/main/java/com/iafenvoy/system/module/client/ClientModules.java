package com.iafenvoy.system.module.client;

import com.iafenvoy.system.event.ClientModuleRegistrationEvent;
import com.iafenvoy.system.module.binding.BindingClientModule;
import com.iafenvoy.system.module.conversation.ConversationClientModule;
import com.iafenvoy.system.module.core.CoreClientModule;
import com.iafenvoy.system.module.inbox.InboxClientModule;
import com.iafenvoy.system.module.item.ItemClientModule;
import com.iafenvoy.system.module.lottery.LotteryClientModule;
import com.iafenvoy.system.module.player.PlayerClientModule;
import com.iafenvoy.system.module.quest.QuestClientModule;
import com.iafenvoy.system.module.settings.SettingsClientModule;
import com.iafenvoy.system.module.shop.ShopClientModule;
import com.iafenvoy.system.module.signin.SignInClientModule;
import com.iafenvoy.system.module.speech.SpeechClientModule;
import com.iafenvoy.system.module.usage.UsageClientModule;
import com.iafenvoy.system.screen.card.CardNodeTypes;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class ClientModules {
    private ClientModules() {
    }

    private static final class Holder {
        private static final ClientModuleRegistry REGISTRY = create();
    }

    private static ClientModuleRegistry create() {
        ClientModuleRegistry registry = new ClientModuleRegistry();
        for (ClientModule module : List.of(new CoreClientModule(), new BindingClientModule(), new ConversationClientModule(),
                new SettingsClientModule(), new UsageClientModule(), new SpeechClientModule(), new PlayerClientModule(),
                new ItemClientModule(), new SignInClientModule(), new InboxClientModule(), new ShopClientModule(),
                new LotteryClientModule(), new QuestClientModule()))
            registry.register(module);
        NeoForge.EVENT_BUS.post(new ClientModuleRegistrationEvent(registry));
        CardNodeTypes.freeze();
        registry.freeze();
        return registry;
    }

    public static ClientModuleRegistry registry() {
        return Holder.REGISTRY;
    }
}
