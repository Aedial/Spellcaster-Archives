package com.spellarchives.config;

import java.io.File;

import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;

import com.spellarchives.SpellArchives;

/**
 * Common (gameplay) configuration for Spellcaster's Archives.
 * Provides a toggle to allow/disallow the use of identification scrolls
 * inside the Archives GUI to identify spells directly.
 */
public final class SpellArchivesConfig extends BaseConfig {
    private static SpellArchivesConfig INSTANCE;
    private static final String CATEGORY = "gameplay";
    private static final String KEY_SCROLL_RESERVE_ENABLED = "scroll_reserve_enabled";
    private static final String KEY_SCROLL_RESERVE_MAX = "scroll_reserve_max";
    private static final String KEY_AUTO_PICKUP_ENABLED = "auto_pickup_enabled";

    // Backing value with a sensible default (enabled by default)
    private static boolean scrollReserveEnabled = true;
    private static int scrollReserveMax = 2048;
    private static boolean autoPickupEnabled = true;

    private SpellArchivesConfig() {
        super(new File(Loader.instance().getConfigDir(), "spellarchives.cfg"));
    }

    /**
     * Initialize the common configuration. Safe to call multiple times.
     */
    public static synchronized void init() {
        if (INSTANCE != null) return;

        INSTANCE = new SpellArchivesConfig();
        INSTANCE.initialize();
    }

    @Override
    protected void sync() {
        if (config == null) return;

        scrollReserveEnabled = config
            .get(CATEGORY, KEY_SCROLL_RESERVE_ENABLED, true, I18n.translateToLocal("config.spellarchives." + KEY_SCROLL_RESERVE_ENABLED))
            .setLanguageKey("config.spellarchives.scroll_reserve_enabled")
            .getBoolean(true);

        scrollReserveMax = config
            .get(CATEGORY, KEY_SCROLL_RESERVE_MAX, 2048, I18n.translateToLocal("config.spellarchives." + KEY_SCROLL_RESERVE_MAX))
            .setLanguageKey("config.spellarchives.scroll_reserve_max")
            .setMinValue(0)
            .setMaxValue(Integer.MAX_VALUE)
            .getInt(2048);

        autoPickupEnabled = config
            .get(CATEGORY, KEY_AUTO_PICKUP_ENABLED, true, I18n.translateToLocal("config.spellarchives." + KEY_AUTO_PICKUP_ENABLED))
            .setLanguageKey("config.spellarchives.auto_pickup_enabled")
            .getBoolean(true);
    }

    /**
     * Returns whether discovering spells via right-click in the GUI is enabled.
     */
    public static boolean isScrollReserveEnabled() {
        // If init wasn't called yet, warn once via logger
        if (INSTANCE == null) SpellArchives.LOGGER.warn("SpellArchivesConfig not initialized; using default for spell reserve.");

        return scrollReserveEnabled;
    }

    /**
     * Returns the server-side max capacity for identification scrolls.
     */
    public static int getScrollReserveMax() {
        return scrollReserveMax;
    }

    /**
     * Returns whether automatic pickup of spell books into the carried Archives item is enabled.
     */
    public static boolean isAutoPickupEnabled() {
        if (INSTANCE == null) SpellArchives.LOGGER.warn("SpellArchivesConfig not initialized; using default for auto pickup.");

        return autoPickupEnabled;
    }

    /**
     * Expose the underlying Forge Configuration so the mod config GUI can show
     * global (server/gameplay) settings alongside client GUI options.
     */
    public static Configuration getConfiguration() {
        return INSTANCE != null ? INSTANCE.config : null;
    }
}

