package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.event.SystemModuleRegistrationEvent;
import com.iafenvoy.system.module.advancement.AdvancementSystemModule;
import com.iafenvoy.system.module.binding.BindingSystemModule;
import com.iafenvoy.system.module.core.CoreSystemModule;
import com.iafenvoy.system.module.inbox.InboxSystemModule;
import com.iafenvoy.system.module.item.ItemSystemModule;
import com.iafenvoy.system.module.lottery.LotterySystemModule;
import com.iafenvoy.system.module.player.PlayerSystemModule;
import com.iafenvoy.system.module.quest.QuestSystemModule;
import com.iafenvoy.system.module.shop.ShopSystemModule;
import com.iafenvoy.system.module.signin.SignInSystemModule;
import com.iafenvoy.system.module.usage.UsageSystemModule;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * One ordered feature catalog supplies server installation and game command assembly.
 */
public final class SystemModules {
    private SystemModules() {
    }

    private static final class Holder {
        private static final SystemModuleRegistry REGISTRY = create();
    }

    private static SystemModuleRegistry create() {
        SystemModuleRegistry registry = new SystemModuleRegistry();
        for (SystemModule module : List.of(new CoreSystemModule(), new BindingSystemModule(), new UsageSystemModule(),
                new PlayerSystemModule(), new ItemSystemModule(), new SignInSystemModule(), new InboxSystemModule(),
                new ShopSystemModule(), new LotterySystemModule(), new AdvancementSystemModule()))
            registry.register(module);
        if (ModList.get().isLoaded("ftbquests")) registry.register(new QuestSystemModule());
        NeoForge.EVENT_BUS.post(new SystemModuleRegistrationEvent(registry));
        registry.freeze();
        return registry;
    }

    public static List<SystemModule> values() {
        return Holder.REGISTRY.values();
    }

    public static List<Identifier> install(ModuleContext context) {
        for (SystemModule module : values()) {
            for (AgentModule<?> workflow : module.workflows(context)) context.engine().registerModule(workflow);
            module.installed(context);
        }
        return values().stream().map(SystemModule::id).toList();
    }
}
