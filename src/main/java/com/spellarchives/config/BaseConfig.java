package com.spellarchives.config;

import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import com.spellarchives.SpellArchives;

/**
 * Abstract base class for configuration handlers.
 * Encapsulates common logic for loading, saving, and syncing Forge configurations.
 */
public abstract class BaseConfig {
    protected Configuration config;
    protected final File configFile;

    protected BaseConfig(File configFile) {
        this.configFile = configFile;
    }

    protected void initialize() {
        if (config != null) return;

        config = new Configuration(configFile);
        load();

        // Register for config changed events
        MinecraftForge.EVENT_BUS.register(this);
    }

    protected void load() {
        try {
            config.load();
            sync();
            if (config.hasChanged()) config.save();
        } catch (RuntimeException e) {
            SpellArchives.LOGGER.warn("Failed to load config: " + e.getMessage());
        }
    }

    /**
     * Called when the config is loaded from disk or when a config changed event is received.
     * Implementations should read values from the 'config' object into static fields here.
     */
    protected abstract void sync();

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(SpellArchives.MODID)) {
            sync();
            if (config.hasChanged()) config.save();
        }
    }

    public Configuration getConfig() {
        return config;
    }
}
