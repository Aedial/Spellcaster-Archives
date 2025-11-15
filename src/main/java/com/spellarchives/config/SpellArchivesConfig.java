package com.spellarchives.config;

import java.io.File;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;

import com.spellarchives.SpellArchives;

/**
 * Common (gameplay) configuration for Spellcaster's Archives.
 * Provides a toggle to allow/disallow the use of identification scrolls
 * inside the Archives GUI to identify spells directly.
 */
public final class SpellArchivesConfig {
    private static Configuration config;
    private static final String CATEGORY = "gameplay";
    private static final String KEY_SCROLL_RESERVE_ENABLED = "scroll_reserve_enabled";
    private static final String KEY_SCROLL_RESERVE_MAX = "scroll_reserve_max";

    // Backing value with a sensible default (enabled by default)
    private static boolean scrollReserveEnabled = true;
    private static int scrollReserveMax = 2048;

    private SpellArchivesConfig() {}

    /**
     * Initialize the common configuration. Safe to call multiple times.
     */
    public static synchronized void init() {
        if (config != null) return;

        File cfgDir = Loader.instance().getConfigDir();
        File cfgFile = new File(cfgDir, "spellarchives.cfg");
        config = new Configuration(cfgFile);

        // Load from disk then sync field values
        loadFromDisk();
        syncFromConfig();
        if (config.hasChanged()) config.save();

        // Listen for in-game config save events
        MinecraftForge.EVENT_BUS.register(new SpellArchivesConfig());
    }

    private static void loadFromDisk() {
        try {
            config.load();
        } catch (RuntimeException e) {
            SpellArchives.LOGGER.warn("Failed to load SpellArchives common config: " + e.getMessage());
        }
    }

    /**
     * Synchronize backing fields from the current in-memory Configuration object without reloading from disk.
     * When the in-game config GUI commits changes, the GUI mutates the in-memory config, fires a ConfigChangedEvent,
     * and expects us to read the updated values before saving.
     * Reloading from disk at that point would discard the player's changes.
     */
    private static void syncFromConfig() {
        if (config == null) return;

        scrollReserveEnabled = config
            .get(CATEGORY, KEY_SCROLL_RESERVE_ENABLED, true, I18n.format("config.spellarchives." + KEY_SCROLL_RESERVE_ENABLED))
            .setLanguageKey("config.spellarchives.scroll_reserve_enabled")
            .getBoolean(true);

        scrollReserveMax = config
            .get(CATEGORY, KEY_SCROLL_RESERVE_MAX, 2048, I18n.format("config.spellarchives." + KEY_SCROLL_RESERVE_MAX))
            .setLanguageKey("config.spellarchives.scroll_reserve_max")
            .setMinValue(0)
            .setMaxValue(Integer.MAX_VALUE)
            .getInt(2048);
    }

    /**
     * Forge callback when an in-game config GUI saves changes for this mod.
     */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!SpellArchives.MODID.equals(event.getModID())) return;

        // Read updated values
        syncFromConfig();
        if (config != null && config.hasChanged()) config.save();
    }

    /**
     * Returns whether discovering spells via right-click in the GUI is enabled.
     */
    public static boolean isScrollReserveEnabled() {
        // If init wasn't called yet, warn once via logger
        if (config == null) SpellArchives.LOGGER.warn("SpellArchivesConfig not initialized; using default for spell reserve.");

        return scrollReserveEnabled;
    }

    /**
     * Returns the server-side max capacity for identification scrolls.
     */
    public static int getScrollReserveMax() {
        return scrollReserveMax;
    }

    /**
     * Expose the underlying Forge Configuration so the mod config GUI can show
     * global (server/gameplay) settings alongside client GUI options.
     */
    public static Configuration getConfiguration() {
        return config;
    }
}

