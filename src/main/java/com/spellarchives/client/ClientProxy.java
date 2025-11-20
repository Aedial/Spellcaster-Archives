package com.spellarchives.client;

import net.minecraftforge.common.MinecraftForge;

import com.spellarchives.CommonProxy;
import com.spellarchives.config.ClientConfig;
import com.spellarchives.registry.ClientModels;


/**
 * Client-side proxy extending the common proxy to register client-only event handlers,
 * configuration, and GUI styling overrides.
 */
public class ClientProxy extends CommonProxy {
    /**
     * Registers client models, initializes client config, and reloads GUI theme settings.
     */
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new ClientModels());

        // Initialize client-side config and apply GUI style overrides
        ClientConfig.init();
    }
}
