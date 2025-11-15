package com.spellarchives.client;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spellarchives.SpellArchives;
import com.spellarchives.gui.GuiStyle;


/**
 * Client configuration using Forge's Configuration (1.12).
 * Supports automatic reloads when the in-game config GUI saves, and also
 * starts a small file-watcher to detect external edits and reload automatically.
 */
public final class ClientConfig {
    private static Configuration config;
    private static final File CONFIG_FILE = new File(Loader.instance().getConfigDir(), "spellarchives-client.cfg");
    private static long lastModified = 0L;

    private static Thread watcherThread;
    // Track whether we've already warned about a config load failure, to avoid spamming the log
    private static boolean configWarned = false;

    private ClientConfig() {}

    // Track which properties are stored as percent
    private static final Set<String> PERCENT_FLOAT_KEYS = new HashSet<>();

    // Remember original ranges to clamp values when reading
    private static final Map<String, Double> FLOAT_MIN = new HashMap<>();
    private static final Map<String, Double> FLOAT_MAX = new HashMap<>();

    // Remember int ranges for clamping
    private static final Map<String, Integer> INT_MIN = new HashMap<>();
    private static final Map<String, Integer> INT_MAX = new HashMap<>();

    // Keep track of keys defined for the gui category so we can control display order
    private static final List<String> GUI_KEYS = new ArrayList<>();

    public static synchronized void init() {
        if (config != null) return;

        config = new Configuration(CONFIG_FILE);
        load();

        // Register to receive config changed events from the ModConfig GUI
        MinecraftForge.EVENT_BUS.register(new ClientConfig());

        // Start a background watcher to detect external edits and reload without restart
        startWatcher();
    }

    private static void load() {
        try {
            config.load();
        } catch (RuntimeException e) {
            if (!configWarned) {
                SpellArchives.LOGGER.warn("Failed to load SpellArchives client config: " + e.getMessage());
                configWarned = true;
            }

            return;
        }

        // Ensure categories and default values exist by reading each key with a default

        // Window sizing
        definePercent("WIDTH_RATIO", GuiStyle.WIDTH_RATIO, 0f, 1f, "config.spellarchives.width_ratio_desc");
        definePercent("HEIGHT_RATIO", GuiStyle.HEIGHT_RATIO, 0f, 1f, "config.spellarchives.height_ratio_desc");
        defineInt("MIN_WIDTH", GuiStyle.MIN_WIDTH, 64, 2000, "config.spellarchives.min_width_desc");
        defineInt("MIN_HEIGHT", GuiStyle.MIN_HEIGHT, 48, 2000, "config.spellarchives.min_height_desc");
        defineInt("DEFAULT_GUI_WIDTH", GuiStyle.DEFAULT_GUI_WIDTH, 100, 2000, "config.spellarchives.default_gui_width");
        defineInt("DEFAULT_GUI_HEIGHT", GuiStyle.DEFAULT_GUI_HEIGHT, 80, 2000, "config.spellarchives.default_gui_height");

        // Layout
        defineInt("MARGIN", GuiStyle.MARGIN, 0, 200, "config.spellarchives.margin_desc");
        defineInt("PANEL_RADIUS", GuiStyle.PANEL_RADIUS, 0, 64, "config.spellarchives.panel_radius_desc");
        defineInt("RIGHT_PANEL_MIN_WIDTH", GuiStyle.RIGHT_PANEL_MIN_WIDTH, 40, 2000, "config.spellarchives.right_panel_min_width_desc");
        definePercent("RIGHT_PANEL_RATIO", GuiStyle.RIGHT_PANEL_RATIO, 0f, 1f, "config.spellarchives.right_panel_ratio_desc");
        defineInt("RIGHT_PANEL_RADIUS", GuiStyle.RIGHT_PANEL_RADIUS, 0, 64, "config.spellarchives.right_panel_radius_desc");

        defineInt("GRID_INNER_PADDING", GuiStyle.GRID_INNER_PADDING, 0, 512, "config.spellarchives.grid_inner_padding_desc");
        defineInt("BOTTOM_BAR_HEIGHT", GuiStyle.BOTTOM_BAR_HEIGHT, 0, 512, "config.spellarchives.bottom_bar_height_desc");
        defineInt("LEFT_PANEL_BOTTOM_PAD", GuiStyle.LEFT_PANEL_BOTTOM_PAD, 0, 512, "config.spellarchives.left_panel_bottom_pad_desc");
        defineInt("CELL_W", GuiStyle.CELL_W, 1, 512, "config.spellarchives.cell_w_desc");
        defineInt("CELL_H", GuiStyle.CELL_H, 1, 1024, "config.spellarchives.cell_h_desc");
        defineInt("ROW_GAP", GuiStyle.ROW_GAP, 0, 256, "config.spellarchives.row_gap_desc");

        // Spine toggles
        defineBool("SPINE_ENABLE_CURVATURE", GuiStyle.SPINE_ENABLE_CURVATURE, "config.spellarchives.spine_enable_curvature_desc");
        defineBool("SPINE_ENABLE_TILT", GuiStyle.SPINE_ENABLE_TILT, "config.spellarchives.spine_enable_tilt_desc");
        defineBool("SPINE_ENABLE_NOISE", GuiStyle.SPINE_ENABLE_NOISE, "config.spellarchives.spine_enable_noise_desc");
        defineBool("SPINE_ENABLE_BANDS", GuiStyle.SPINE_ENABLE_BANDS, "config.spellarchives.spine_enable_bands_desc");
        defineBool("SPINE_EMBED_ICON", GuiStyle.SPINE_EMBED_ICON, "config.spellarchives.spine_embed_icon_desc");

        // Screen darken when GUI is open (0.0 - 1.0)
        definePercent("SCREEN_DARKEN_FACTOR", GuiStyle.SCREEN_DARKEN_FACTOR, 0f, 1f, "config.spellarchives.screen_darken_desc");

        // Colors (hex string 0xAARRGGBB)
        defineColorHex("LEFT_PANEL_FILL", GuiStyle.BACKGROUND_FILL, "config.spellarchives.left_panel_fill_desc");
        defineColorHex("LEFT_PANEL_BORDER", GuiStyle.BACKGROUND_BORDER, "config.spellarchives.left_panel_border_desc");
        defineColorHex("RIGHT_PANEL_FILL", GuiStyle.RIGHT_PANEL_FILL, "config.spellarchives.right_panel_fill_desc");
        defineColorHex("RIGHT_PANEL_BORDER", GuiStyle.RIGHT_PANEL_BORDER, "config.spellarchives.right_panel_border_desc");
        defineColorHex("DETAIL_TEXT", GuiStyle.DETAIL_TEXT, "config.spellarchives.detail_text_desc");
        defineColorHex("HOVER_BORDER", GuiStyle.HOVER_BORDER, "config.spellarchives.hover_border_desc");

        // Groove/shelf colors
        defineColorHex("GROOVE_BASE", GuiStyle.GROOVE_BASE, "config.spellarchives.groove_base_desc");
        defineColorHex("GROOVE_HL", GuiStyle.GROOVE_HL, "config.spellarchives.groove_hl_desc");
        defineColorHex("GROOVE_SH", GuiStyle.GROOVE_SH, "config.spellarchives.groove_sh_desc");

        // Additional theme-agnostic parameters that themes may expose
        definePercent("PANEL_GRADIENT", GuiStyle.PANEL_GRADIENT, 0f, 1f, "config.spellarchives.panel_gradient_desc");
        definePercent("OUTER_SHADOW_STRENGTH", GuiStyle.OUTER_SHADOW_STRENGTH, 0f, 1f, "config.spellarchives.outer_shadow_desc");

        // Navigation
        defineInt("NAV_BUTTON_SIZE", GuiStyle.NAV_BUTTON_SIZE, 4, 256, "config.spellarchives.nav_button_size_desc");
        defineInt("ARROWS_Y_OFFSET", GuiStyle.ARROWS_Y_OFFSET, -200, 200, "config.spellarchives.arrows_y_offset_desc");

        // Spine bands (decorative lines)
        defineInt("SPINE_BAND_THICKNESS", GuiStyle.SPINE_BAND_THICKNESS, 0, 20, "config.spellarchives.spine_band_thickness_desc");
        defineInt("SPINE_BAND_GAP", GuiStyle.SPINE_BAND_GAP, 0, 100, "config.spellarchives.spine_band_gap_desc");
        defineInt("SPINE_BAND_TOP_SPACE", GuiStyle.SPINE_BAND_TOP_SPACE, 0, 100, "config.spellarchives.spine_band_top_space_desc");

        // Scroll slot max size (pixels)
        defineInt("SCROLL_SLOT_MAX_SIZE", GuiStyle.SCROLL_SLOT_MAX_SIZE, 8, 64, "config.spellarchives.scroll_slot_max_size_desc");

        // Localize category title/header
        config.getCategory("gui").setLanguageKey("gui.spellarchives.config_title");

        // Compute a display order for properties in the GUI category based on their display name
        // TODO: order by declaration order
        List<String> ordered = new ArrayList<>(GUI_KEYS);
        Collections.sort(ordered, (a, b) -> {
            String ta = I18n.format("config.spellarchives.gui." + a.toLowerCase(Locale.ROOT));
            String tb = I18n.format("config.spellarchives.gui." + b.toLowerCase(Locale.ROOT));
            return ta.compareToIgnoreCase(tb);
        });
        config.getCategory("gui").setPropertyOrder(ordered);

        if (config.hasChanged()) config.save();

        configWarned = false;
        lastModified = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : 0L;
    }

    private static void startWatcher() {
        if (watcherThread != null) return;

        watcherThread = new Thread(() -> {
            try {
                while (true) {
                    long lm = CONFIG_FILE.exists() ? CONFIG_FILE.lastModified() : 0L;
                    if (lm != lastModified) {
                        lastModified = lm;

                        load();
                        GuiStyle.reloadFromConfig();
                    }

                    Thread.sleep(2000L);
                }
            } catch (InterruptedException ignored) {}
        }, "SpellArchives-ClientConfig-Watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!SpellArchives.MODID.equals(event.getModID())) return;

        GuiStyle.reloadFromConfig();
    }

    public static void warnIfUninitialized(String key) {
        if (config == null && !configWarned) {
            SpellArchives.LOGGER.warn("ClientConfig not initialized; returning fallback for key: " + key);
            configWarned = true;
        }
    }

    // Get an int property, clamped to defined ranges if any.
    public static int getInt(String key, int fallback) {
        warnIfUninitialized(key);
        if (config == null) return fallback;

        if (config.hasCategory("gui") && config.getCategory("gui").containsKey(key)) {
            Property p = config.getCategory("gui").get(key);

            if (p.getType() == Property.Type.STRING) {
                // Attempt to parse hex color strings like 0xAARRGGBB or #RRGGBB/#AARRGGBB
                String s = p.getString();
                int parsed = parseColorHex(s, fallback);

                // Ensure color channels / alpha are normalized as a last-resort clamp.
                parsed = GuiStyle.clampColor(parsed);

                // Also apply int-range clamping if this key was defined with ranges.
                Integer minI = INT_MIN.get(key);
                Integer maxI = INT_MAX.get(key);
                if (minI != null && maxI != null) return Math.max(minI, Math.min(maxI, parsed));

                return parsed;
            }

            int val = p.getInt();
            Integer min = INT_MIN.get(key);
            Integer max = INT_MAX.get(key);
            if (min != null && max != null) return Math.max(min, Math.min(max, val));

            return val;
        }

        int cfgVal = config.get("gui", key, fallback).getInt();
        Integer min = INT_MIN.get(key);
        Integer max = INT_MAX.get(key);
        if (min != null && max != null) return Math.max(min, Math.min(max, cfgVal));

        return cfgVal;
    }

    // Get a float property, clamped to defined ranges if any.
    public static float getFloat(String key, float fallback) {
        warnIfUninitialized(key);
        if (config == null) return fallback;

        if (config.hasCategory("gui") && config.getCategory("gui").containsKey(key)) {
            Property p = config.getCategory("gui").get(key);
            double val = p.getDouble(p.getDouble());

            // If this key is percent-backed, stored is 0..100. Normalize.
            if (PERCENT_FLOAT_KEYS.contains(key)) val = val / 100.0;

            // Otherwise, treat stored as the actual value and clamp if ranges are known.
            Double min = FLOAT_MIN.get(key);
            Double max = FLOAT_MAX.get(key);
            if (min != null && max != null) return (float) Math.max(min, Math.min(max, val));

            return (float) val;
        }

        // No explicit property present: apply clamping to fallback if possible
        Double min = FLOAT_MIN.get(key);
        Double max = FLOAT_MAX.get(key);
        if (min != null && max != null) return (float) Math.max(min, Math.min(max, fallback));

        return fallback;
    }

    // Return the raw percent value stored for this float property (0..100).
    public static float getPercent(String key, float fallbackPercent) {
        warnIfUninitialized(key);
        if (config == null) return fallbackPercent;

        if (config.hasCategory("gui") && config.getCategory("gui").containsKey(key)) {
            Property p = config.getCategory("gui").get(key);
            double stored = p.getDouble(p.getDouble());

            // Normalize percent -> 0..1 and clamp according to original float ranges
            double val = stored / 100.0;
            Double min = FLOAT_MIN.get(key);
            Double max = FLOAT_MAX.get(key);

            if (min != null && max != null) return (float) Math.max(min, Math.min(max, val));

            return (float) val;
        }

        // Normalize fallback percent (assumed 0..100) to 0..1 and clamp if we know ranges
        double fallbackNorm = fallbackPercent / 100.0;
        Double min = FLOAT_MIN.get(key);
        Double max = FLOAT_MAX.get(key);
        if (min != null && max != null) return (float) Math.max(min, Math.min(max, fallbackNorm));

        return (float) fallbackNorm;
    }

    public static boolean getBoolean(String key, boolean fallback) {
        warnIfUninitialized(key);
        if (config == null) return fallback;

        return config.get("gui", key, fallback).getBoolean();
    }

    public static String getString(String key, String fallback) {
        warnIfUninitialized(key);
        if (config == null) return fallback;

        return config.get("gui", key, fallback).getString();
    }

    // ---- Helpers for defining properties with less boilerplate ----
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^(?:#|0x)?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    // Define a float property stored as percent (0..100) in the config file.
    private static Property definePercent(String key, float def, float min, float max, String descKey) {
        // Store floats as percent in the config (0..100) to avoid binary float display artifacts.
        double defPercent = Math.round(def * 100.0 * 1000.0) / 1000.0;  // swallow binary artifacts
        double minPercent = min * 100.0;
        double maxPercent = max * 100.0;

        Property p = config.get("gui", key, defPercent, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        p.setComment(I18n.format(descKey));
        if (minPercent <= maxPercent) {
            p.setMinValue(minPercent);
            p.setMaxValue(maxPercent);
        }

        // Remember original ranges for clamping
        FLOAT_MIN.put(key, (double) min);
        FLOAT_MAX.put(key, (double) max);
        PERCENT_FLOAT_KEYS.add(key);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p;
    }

    // Define a standard int property with min/max clamping.
    private static Property defineInt(String key, int def, int min, int max, String descKey) {
        Property p = config.get("gui", key, def, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        p.setMinValue(min);
        p.setMaxValue(max);

        int cur = p.getInt(def);
        int clamped = Math.max(min, Math.min(max, cur));
        if (clamped != cur) p.set(clamped);

        // Record int ranges for clamping in getters and on-save normalization
        INT_MIN.put(key, min);
        INT_MAX.put(key, max);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p;
    }

    // Define a standard boolean property.
    private static Property defineBool(String key, boolean def, String descKey) {
        Property p = config.get("gui", key, def, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p;
    }

    // Define a standard string property, optionally with valid values.
    private static Property defineStringOption(String key, String def, String[] valid, String descKey) {
        Property p = config.get("gui", key, def, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        if (valid != null) p.setValidValues(valid);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p;
    }

    // Define a color property stored as hex string 0xAARRGGBB.
    private static Property defineColorHex(String key, int defArgb, String descKey) {
        String defHex = toHex(defArgb);
        Property p = config.get("gui", key, defHex, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        p.setValidationPattern(HEX_COLOR_PATTERN);

        // Normalize to uppercase 0xAARRGGBB and clamp alpha
        int parsed = parseColorHex(p.getString(), defArgb);
        int norm = GuiStyle.clampColor(parsed);
        String normHex = toHex(norm);
        if (!normHex.equalsIgnoreCase(p.getString())) p.set(normHex);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p;
    }

    // Convert an ARGB int to hex string 0xAARRGGBB.
    private static String toHex(int argb) {
        return String.format("0x%08X", argb);
    }

    // Parse a hex color string like 0xAARRGGBB or #RRGGBB/#AARRGGBB to an ARGB int.
    private static int parseColorHex(String s, int fallback) {
        if (s == null) return fallback;

        Matcher m = HEX_COLOR_PATTERN.matcher(s.trim());
        if (!m.matches()) return fallback;

        String hex = m.group(1);
        long val = Long.parseLong(hex, 16);
        if (hex.length() == 6) val |= 0xFF000000L;  // RRGGBB -> assume opaque

        return (int) val;
    }

    // Expose the underlying Forge Configuration for use by the config GUI factory.
    public static Configuration getConfiguration() {
        return config;
    }
}
