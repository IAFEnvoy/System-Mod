package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.client.ClientModule;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

@SuppressWarnings("unused")
public final class SystemResourceKeys {
    public static final ResourceKey<Registry<ServerModule>> SERVER_MODULE = create("server_module");
    public static final ResourceKey<Registry<SystemDefinition>> SYSTEM = create("system");

    public static final class Client {
        public static final ResourceKey<Registry<ClientModule>> CLIENT_MODULE = create("client_module");

        private Client() {
        }
    }

    private static <T> ResourceKey<Registry<T>> create(String id) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, id));
    }
}
