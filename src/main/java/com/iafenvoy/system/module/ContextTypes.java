package com.iafenvoy.system.module;

import com.iafenvoy.system.assistant.AssistantCardDisplays;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.database.AgentDatabases;
import com.iafenvoy.system.search.SearchGateway;
import com.iafenvoy.system.speech.quest.QuestNarration;

/**
 * Built-in service keys; extensions may declare their own keys without changing ModuleContext.
 */
public final class ContextTypes {
    public static final ContextType<MinecraftTools> GAME = new ContextType<>("game");
    public static final ContextType<CommerceTransactions> COMMERCE = new ContextType<>("commerce");
    public static final ContextType<CommerceCatalogs> CATALOGS = new ContextType<>("catalogs");
    public static final ContextType<AgentDatabases> DATABASES = new ContextType<>("databases");
    public static final ContextType<SearchGateway> SEARCH = new ContextType<>("search");
    public static final ContextType<QuestNarration> NARRATION = new ContextType<>("narration");
    public static final ContextType<AssistantCardDisplays> DISPLAYS = new ContextType<>("displays");

    private ContextTypes() {
    }
}
