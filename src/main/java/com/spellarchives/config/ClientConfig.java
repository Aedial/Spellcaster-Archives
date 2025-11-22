package com.spellarchives.config;

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
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spellarchives.SpellArchives;

/**
 * Client configuration using Forge's Configuration (1.12).
 * Supports automatic reloads when the in-game config GUI saves, and also
 * starts a small file-watcher to detect external edits and reload automatically.
 * <p>
 * Also contains centralized GUI style constants used throughout the Spellcaster's Archives screens.
 */
public final class ClientConfig extends BaseConfig {
    private static ClientConfig INSTANCE;
    private static long lastModified = 0L;

    private static Thread watcherThread;
    // Track whether we've already warned about a config load failure, to avoid spamming the log
    private static boolean configWarned = false;

    // Incremented each time load() runs so GUIs can invalidate caches
    public static int CONFIG_REVISION = 0;

    // -- Configurable Fields (initialized with defaults) --

    // Window sizing
    public static float WIDTH_RATIO = 0.75f;             // Archives window width = 75% of screen width
    public static float HEIGHT_RATIO = 0.75f;            // Archives window height = 75% of screen height
    public static int MIN_WIDTH = 220;                   // Minimum archive window width
    public static int MIN_HEIGHT = 180;                  // Minimum archive window height

    // Panels
    public static int MARGIN = 10;                       // Outer margin around the entire window
    public static int PANEL_RADIUS = 6;                  // Rounded corner radius for the main window background
    public static int RIGHT_PANEL_MIN_WIDTH = 120;       // Minimum width of the right details panel
    public static float RIGHT_PANEL_RATIO = 0.38f;       // Right panel width as a fraction of total window width
    public static int RIGHT_PANEL_RADIUS = 4;            // Corner radius for right panel background
    public static boolean EASY_LAYOUT_ENABLED = true;

    // Colors
    public static int BACKGROUND_FILL = 0xFF8B5A2B;      // Warm wood main background fill
    public static int BACKGROUND_BORDER = 0xFF6F4520;    // Darker wood border
    public static int RIGHT_PANEL_FILL = 0xFF000000;     // Default right panel: black
    public static int RIGHT_PANEL_BORDER = 0xFF111111;   // Right panel border (near-black)
    public static int DETAIL_TEXT = 0xFFAAAAAA;          // Light detail text on dark right panel
    public static int HOVER_BORDER = 0x90FFFFFF;         // Hover outline around spines on the left

    // Grid and grooves
    public static int GRID_INNER_PADDING = 8;            // Padding between left panel edge and content grid (x & y)
    public static int BOTTOM_BAR_HEIGHT = 28;            // Reserved height at bottom for navigation controls
    public static int LEFT_PANEL_BOTTOM_PAD = 12;        // Extra bottom padding under grid (inside left panel)
    public static int CELL_W = 10;                       // Spine width (book spine tile width)
    public static int CELL_H = 18;                       // Spine height (book spine tile height)
    public static int ROW_GAP = 3;                       // Vertical spacing between spine rows
    public static int GROOVE_BASE = 0xFF6B3F1F;          // Shelf groove (wood) fill color
    public static int GROOVE_HL = 0xFFD9B38C;            // Shelf top highlight (lighter wood)
    public static int GROOVE_SH = 0xFF4A2A14;            // Shelf bottom shadow (darker)

    // Headers / tabs
    public static int HEADER_EXTRA = 2;                  // Extra pixels added to font height for header row height
    public static int TAB_RADIUS = 4;                    // Corner radius of the tier header tab above grooves
    public static int TAB_PADDING_X = 6;                 // Horizontal padding inside the tier header tab
    public static int TAB_OFFSET_X = 0;                  // X offset from groove left where the tab starts
    public static int HEADER_TEXT_OFFSET_Y = 2;          // Additional Y offset for header text within the tab

    // Right panel content
    public static int RIGHT_PANEL_INNER_MARGIN = 10;     // Inner margin from right panel edges to content
    public static int RIGHT_PANEL_TEXT_SIDE_PAD = 10;    // Side padding used for description wrap width
    public static int RIGHT_TITLE_ICON_SIZE = 16;        // Size of the top-left icon in the right panel header
    public static int RIGHT_TITLE_TEXT_GAP = 24;         // X gap from left content edge to header text start
    public static int RIGHT_AFTER_HEADER_GAP = 22;       // Vertical gap after header row before next line
    public static int RIGHT_LINE_GAP_SMALL = 12;         // Vertical spacing between property lines
    public static int RIGHT_LINE_GAP_MEDIUM = 18;        // Vertical spacing for tier/element lines
    public static int RIGHT_SECTION_GAP = 12;            // Extra space before description section
    public static int RIGHT_ELEMENT_ICON_SIZE = 8;       // Size of the element icon in the right panel
    public static int RIGHT_DESC_LINE_HEIGHT = 10;       // Line advance for wrapped description text
    public static int RIGHT_BOTTOM_CLAMP_MARGIN = 14;    // Bottom margin to stop drawing near panel edge

    // Navigation
    public static int NAV_BUTTON_SIZE = 20;              // Size of prev/next navigation buttons
    public static int ARROWS_Y_OFFSET = 6;               // Additional Y offset for buttons row vs grid end

    // Scroll slot styling (identification scroll reserve)
    public static final int SCROLL_SLOT_SIDE_GAP = 4;
    public static final int SCROLL_SLOT_RADIUS = 3;
    public static final int SCROLL_SLOT_BG = 0xAA222222;
    public static final int SCROLL_SLOT_BORDER = 0xFF000000;
    public static final int SCROLL_SLOT_BG_DISABLED = 0x55222222;
    public static final int SCROLL_SLOT_BORDER_DISABLED = 0x66000000;
    public static int SCROLL_SLOT_MAX_SIZE = 20;

    // Left spine rendering
    public static int SPINE_LEFT_BORDER = 1;             // Left spacing before spine fill
    public static int SPINE_TOP_BORDER = 1;              // Top spacing inside groove
    public static int SPINE_BOTTOM_BORDER = 1;           // Bottom spacing inside groove

    // Spine icon
    public static int SPINE_ICON_SIZE = 8;               // Size of the element icon on the spine
    public static int SPINE_ICON_BOTTOM_MARGIN = 1;      // Bottom margin below the icon
    public static int SPINE_ICON_Y_OFFSET = 0;           // Additional Y offset applied to icon position

    // Spine shading toggles and parameters
    public static boolean SPINE_ENABLE_CURVATURE = true; // Enable curved brightness from center to edges
    public static boolean SPINE_ENABLE_TILT = true;      // Asymmetric tilt for one side
    public static boolean SPINE_ENABLE_NOISE = true;     // Subtle per-pixel noise on the spine
    public static boolean SPINE_ENABLE_BANDS = true;     // Horizontal decorative bands
    public static boolean SPINE_EMBED_ICON = true;       // Bake element icon into the spine texture instead of overlay

    public static float SPINE_CENTER_BRIGHTEN = 1.07f;   // Brightness multiplier at center of spine
    public static float SPINE_EDGE_FACTOR = 0.25f;       // How fast brightness falls to edges (larger = darker edges)
    public static float SPINE_VSHADE_BASE = 0.96f;       // Vertical shading: base factor near top/bottom
    public static float SPINE_VSHADE_RANGE = 0.08f;      // Vertical shading: range added toward center
    public static float SPINE_NOISE_AMPLITUDE = 0.03f;   // Amplitude of noise (+/- percentage)

    // Bands
    public static int SPINE_BAND_THICKNESS = 2;          // Thickness in pixels of each band
    public static int SPINE_BAND_GAP = 1;                // Gap between the two bands
    public static int SPINE_BAND_TOP_SPACE = 1;          // Space from top of available area to first band
    public static float SPINE_BAND1_DARKEN = 0.8f;       // Darkening factor for first band
    public static float SPINE_BAND2_DARKEN = 0.85f;      // Darkening factor for second band

    // Screen darken factor when GUI is open (0.0 = no darken, 1.0 = fully black)
    public static float SCREEN_DARKEN_FACTOR = 0.55f;

    // Panel theming visual parameters (theme-agnostic; used by multiple themes)
    public static float PANEL_GRADIENT = 0.6f;           // strength of vertical gradient applied to themed panel backgrounds
    public static float OUTER_SHADOW_STRENGTH = 0.4f;    // strength of subtle inner/outer shadow when panel theming is used

    private ClientConfig() {
        super(new File(Loader.instance().getConfigDir(), "spellarchives-client.cfg"));
    }

    // Track which properties are stored as percent
    private final Set<String> PERCENT_FLOAT_KEYS = new HashSet<>();

    // Remember original ranges to clamp values when reading
    private final Map<String, Double> FLOAT_MIN = new HashMap<>();
    private final Map<String, Double> FLOAT_MAX = new HashMap<>();

    // Remember int ranges for clamping
    private final Map<String, Integer> INT_MIN = new HashMap<>();
    private final Map<String, Integer> INT_MAX = new HashMap<>();

    // Keep track of keys defined for the gui category so we can control display order
    private final List<String> GUI_KEYS = new ArrayList<>();

    public static synchronized void init() {
        if (INSTANCE != null) return;

        INSTANCE = new ClientConfig();
        INSTANCE.initialize();

        // Start a background watcher to detect external edits and reload automatically
        startWatcher();
    }

    /**
     * Reloads configurable values from the client config. Safe to call on client pre-init.
     */
    public static void reloadFromConfig() {
        if (INSTANCE != null) INSTANCE.load();
    }

    @Override
    protected void sync() {
        // Clear keys list to rebuild it in order
        GUI_KEYS.clear();

        // Ensure categories and default values exist by reading each key with a default

        // Window sizing
        WIDTH_RATIO = definePercent("WIDTH_RATIO", WIDTH_RATIO, 0f, 1f, "config.spellarchives.width_ratio_desc");
        HEIGHT_RATIO = definePercent("HEIGHT_RATIO", HEIGHT_RATIO, 0f, 1f, "config.spellarchives.height_ratio_desc");
        EASY_LAYOUT_ENABLED = defineBool("EASY_LAYOUT_ENABLED", EASY_LAYOUT_ENABLED, "config.spellarchives.easy_layout_enabled_desc");
        MIN_WIDTH = defineInt("MIN_WIDTH", MIN_WIDTH, 64, 2000, "config.spellarchives.min_width_desc");
        MIN_HEIGHT = defineInt("MIN_HEIGHT", MIN_HEIGHT, 48, 2000, "config.spellarchives.min_height_desc");

        // Layout
        MARGIN = defineInt("MARGIN", MARGIN, 0, 200, "config.spellarchives.margin_desc");
        PANEL_RADIUS = defineInt("PANEL_RADIUS", PANEL_RADIUS, 0, 64, "config.spellarchives.panel_radius_desc");
        RIGHT_PANEL_MIN_WIDTH = defineInt("RIGHT_PANEL_MIN_WIDTH", RIGHT_PANEL_MIN_WIDTH, 40, 2000, "config.spellarchives.right_panel_min_width_desc");
        RIGHT_PANEL_RATIO = definePercent("RIGHT_PANEL_RATIO", RIGHT_PANEL_RATIO, 0f, 1f, "config.spellarchives.right_panel_ratio_desc");
        RIGHT_PANEL_RADIUS = defineInt("RIGHT_PANEL_RADIUS", RIGHT_PANEL_RADIUS, 0, 64, "config.spellarchives.right_panel_radius_desc");

        GRID_INNER_PADDING = defineInt("GRID_INNER_PADDING", GRID_INNER_PADDING, 0, 512, "config.spellarchives.grid_inner_padding_desc");
        BOTTOM_BAR_HEIGHT = defineInt("BOTTOM_BAR_HEIGHT", BOTTOM_BAR_HEIGHT, 0, 512, "config.spellarchives.bottom_bar_height_desc");
        LEFT_PANEL_BOTTOM_PAD = defineInt("LEFT_PANEL_BOTTOM_PAD", LEFT_PANEL_BOTTOM_PAD, 0, 512, "config.spellarchives.left_panel_bottom_pad_desc");
        CELL_W = defineInt("CELL_W", CELL_W, 1, 512, "config.spellarchives.cell_w_desc");
        CELL_H = defineInt("CELL_H", CELL_H, 1, 1024, "config.spellarchives.cell_h_desc");
        ROW_GAP = defineInt("ROW_GAP", ROW_GAP, 0, 256, "config.spellarchives.row_gap_desc");

        // Spine toggles
        SPINE_ENABLE_CURVATURE = defineBool("SPINE_ENABLE_CURVATURE", SPINE_ENABLE_CURVATURE, "config.spellarchives.spine_enable_curvature_desc");
        SPINE_ENABLE_TILT = defineBool("SPINE_ENABLE_TILT", SPINE_ENABLE_TILT, "config.spellarchives.spine_enable_tilt_desc");
        SPINE_ENABLE_NOISE = defineBool("SPINE_ENABLE_NOISE", SPINE_ENABLE_NOISE, "config.spellarchives.spine_enable_noise_desc");
        SPINE_ENABLE_BANDS = defineBool("SPINE_ENABLE_BANDS", SPINE_ENABLE_BANDS, "config.spellarchives.spine_enable_bands_desc");
        SPINE_EMBED_ICON = defineBool("SPINE_EMBED_ICON", SPINE_EMBED_ICON, "config.spellarchives.spine_embed_icon_desc");

        // Screen darken when GUI is open (0.0 - 1.0)
        SCREEN_DARKEN_FACTOR = definePercent("SCREEN_DARKEN_FACTOR", SCREEN_DARKEN_FACTOR, 0f, 1f, "config.spellarchives.screen_darken_desc");

        // Colors (hex string 0xAARRGGBB)
        BACKGROUND_FILL = defineColorHex("LEFT_PANEL_FILL", BACKGROUND_FILL, "config.spellarchives.left_panel_fill_desc");
        BACKGROUND_BORDER = defineColorHex("LEFT_PANEL_BORDER", BACKGROUND_BORDER, "config.spellarchives.left_panel_border_desc");
        RIGHT_PANEL_FILL = defineColorHex("RIGHT_PANEL_FILL", RIGHT_PANEL_FILL, "config.spellarchives.right_panel_fill_desc");
        RIGHT_PANEL_BORDER = defineColorHex("RIGHT_PANEL_BORDER", RIGHT_PANEL_BORDER, "config.spellarchives.right_panel_border_desc");
        DETAIL_TEXT = defineColorHex("DETAIL_TEXT", DETAIL_TEXT, "config.spellarchives.detail_text_desc");
        HOVER_BORDER = defineColorHex("HOVER_BORDER", HOVER_BORDER, "config.spellarchives.hover_border_desc");

        // Groove/shelf colors
        GROOVE_BASE = defineColorHex("GROOVE_BASE", GROOVE_BASE, "config.spellarchives.groove_base_desc");
        GROOVE_HL = defineColorHex("GROOVE_HL", GROOVE_HL, "config.spellarchives.groove_hl_desc");
        GROOVE_SH = defineColorHex("GROOVE_SH", GROOVE_SH, "config.spellarchives.groove_sh_desc");

        // Additional theme-agnostic parameters that themes may expose
        PANEL_GRADIENT = definePercent("PANEL_GRADIENT", PANEL_GRADIENT, 0f, 1f, "config.spellarchives.panel_gradient_desc");
        OUTER_SHADOW_STRENGTH = definePercent("OUTER_SHADOW_STRENGTH", OUTER_SHADOW_STRENGTH, 0f, 1f, "config.spellarchives.outer_shadow_desc");

        // Navigation
        NAV_BUTTON_SIZE = defineInt("NAV_BUTTON_SIZE", NAV_BUTTON_SIZE, 4, 256, "config.spellarchives.nav_button_size_desc");
        ARROWS_Y_OFFSET = defineInt("ARROWS_Y_OFFSET", ARROWS_Y_OFFSET, -200, 200, "config.spellarchives.arrows_y_offset_desc");

        // Spine bands (decorative lines)
        SPINE_BAND_THICKNESS = defineInt("SPINE_BAND_THICKNESS", SPINE_BAND_THICKNESS, 0, 20, "config.spellarchives.spine_band_thickness_desc");
        SPINE_BAND_GAP = defineInt("SPINE_BAND_GAP", SPINE_BAND_GAP, 0, 100, "config.spellarchives.spine_band_gap_desc");
        SPINE_BAND_TOP_SPACE = defineInt("SPINE_BAND_TOP_SPACE", SPINE_BAND_TOP_SPACE, 0, 100, "config.spellarchives.spine_band_top_space_desc");

        // Scroll slot max size (pixels)
        SCROLL_SLOT_MAX_SIZE = defineInt("SCROLL_SLOT_MAX_SIZE", SCROLL_SLOT_MAX_SIZE, 8, 64, "config.spellarchives.scroll_slot_max_size_desc");

        // Localize category title/header
        config.getCategory("gui").setLanguageKey("gui.spellarchives.config_title");

        // Set property order by declaration order
        config.getCategory("gui").setPropertyOrder(GUI_KEYS);

        configWarned = false;
        lastModified = configFile.exists() ? configFile.lastModified() : 0L;

        // bump revision so GUIs can clear cached resources that depend on config
        CONFIG_REVISION++;
    }

    private static void startWatcher() {
        if (watcherThread != null) return;

        watcherThread = new Thread(() -> {
            try {
                while (true) {
                    if (INSTANCE != null && INSTANCE.configFile.exists()) {
                        long lm = INSTANCE.configFile.lastModified();
                        if (lm != lastModified) {
                            lastModified = lm;
                            INSTANCE.load();
                        }
                    }
                    Thread.sleep(2000L);
                }
            } catch (InterruptedException ignored) {}
        }, "SpellArchives-ClientConfig-Watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }



    public static void warnIfUninitialized(String key) {
        if (INSTANCE == null && !configWarned) {
            SpellArchives.LOGGER.warn("ClientConfig not initialized; returning fallback for key: " + key);
            configWarned = true;
        }
    }

    // Get an int property, clamped to defined ranges if any.
    public static int getInt(String key, int fallback) {
        warnIfUninitialized(key);
        if (INSTANCE == null || INSTANCE.config == null) return fallback;

        if (INSTANCE.config.hasCategory("gui") && INSTANCE.config.getCategory("gui").containsKey(key)) {
            Property p = INSTANCE.config.getCategory("gui").get(key);

            if (p.getType() == Property.Type.STRING) {
                // Attempt to parse hex color strings like 0xAARRGGBB or #RRGGBB/#AARRGGBB
                String s = p.getString();
                int parsed = parseColorHex(s, fallback);

                // Ensure color channels / alpha are normalized as a last-resort clamp.
                parsed = clampColor(parsed);

                // Also apply int-range clamping if this key was defined with ranges.
                Integer minI = INSTANCE.INT_MIN.get(key);
                Integer maxI = INSTANCE.INT_MAX.get(key);
                if (minI != null && maxI != null) return Math.max(minI, Math.min(maxI, parsed));

                return parsed;
            }

            int val = p.getInt();
            Integer min = INSTANCE.INT_MIN.get(key);
            Integer max = INSTANCE.INT_MAX.get(key);
            if (min != null && max != null) return Math.max(min, Math.min(max, val));

            return val;
        }

        int cfgVal = INSTANCE.config.get("gui", key, fallback).getInt();
        Integer min = INSTANCE.INT_MIN.get(key);
        Integer max = INSTANCE.INT_MAX.get(key);
        if (min != null && max != null) return Math.max(min, Math.min(max, cfgVal));

        return cfgVal;
    }

    // Get a float property, clamped to defined ranges if any.
    public static float getFloat(String key, float fallback) {
        warnIfUninitialized(key);
        if (INSTANCE == null || INSTANCE.config == null) return fallback;

        if (INSTANCE.config.hasCategory("gui") && INSTANCE.config.getCategory("gui").containsKey(key)) {
            Property p = INSTANCE.config.getCategory("gui").get(key);
            double val = p.getDouble(p.getDouble());

            // If this key is percent-backed, stored is 0..100. Normalize.
            if (INSTANCE.PERCENT_FLOAT_KEYS.contains(key)) val = val / 100.0;

            // Otherwise, treat stored as the actual value and clamp if ranges are known.
            Double min = INSTANCE.FLOAT_MIN.get(key);
            Double max = INSTANCE.FLOAT_MAX.get(key);
            if (min != null && max != null) return (float) Math.max(min, Math.min(max, val));

            return (float) val;
        }

        // No explicit property present: apply clamping to fallback if possible
        Double min = INSTANCE.FLOAT_MIN.get(key);
        Double max = INSTANCE.FLOAT_MAX.get(key);
        if (min != null && max != null) return (float) Math.max(min, Math.min(max, fallback));

        return fallback;
    }

    // Return the raw percent value stored for this float property (0..100).
    public static float getPercent(String key, float fallbackPercent) {
        warnIfUninitialized(key);
        if (INSTANCE == null || INSTANCE.config == null) return fallbackPercent;

        if (INSTANCE.config.hasCategory("gui") && INSTANCE.config.getCategory("gui").containsKey(key)) {
            Property p = INSTANCE.config.getCategory("gui").get(key);
            double stored = p.getDouble(p.getDouble());

            // Normalize percent -> 0..1 and clamp according to original float ranges
            double val = stored / 100.0;
            Double min = INSTANCE.FLOAT_MIN.get(key);
            Double max = INSTANCE.FLOAT_MAX.get(key);

            if (min != null && max != null) return (float) Math.max(min, Math.min(max, val));

            return (float) val;
        }

        // Normalize fallback percent (assumed 0..100) to 0..1 and clamp if we know ranges
        double fallbackNorm = fallbackPercent / 100.0;
        Double min = INSTANCE.FLOAT_MIN.get(key);
        Double max = INSTANCE.FLOAT_MAX.get(key);
        if (min != null && max != null) return (float) Math.max(min, Math.min(max, fallbackNorm));

        return (float) fallbackNorm;
    }

    public static boolean getBoolean(String key, boolean fallback) {
        warnIfUninitialized(key);
        if (INSTANCE == null || INSTANCE.config == null) return fallback;

        return INSTANCE.config.get("gui", key, fallback).getBoolean();
    }

    public static String getString(String key, String fallback) {
        warnIfUninitialized(key);
        if (INSTANCE == null || INSTANCE.config == null) return fallback;

        return INSTANCE.config.get("gui", key, fallback).getString();
    }

    // ---- Helpers for defining properties with less boilerplate ----
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^(?:#|0x)?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    // Define a float property stored as percent (0..100) in the config file.
    private float definePercent(String key, float def, float min, float max, String descKey) {
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

        // Return the current value (normalized 0..1)
        double val = p.getDouble();
        return (float) (val / 100.0);
    }

    // Define a standard int property with min/max clamping.
    private int defineInt(String key, int def, int min, int max, String descKey) {
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

        return clamped;
    }

    // Define a standard boolean property.
    private boolean defineBool(String key, boolean def, String descKey) {
        Property p = config.get("gui", key, def, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p.getBoolean(def);
    }

    // Define a standard string property, optionally with valid values.
    private String defineStringOption(String key, String def, String[] valid, String descKey) {
        Property p = config.get("gui", key, def, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        if (valid != null) p.setValidValues(valid);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return p.getString();
    }

    // Define a color property stored as hex string 0xAARRGGBB.
    private int defineColorHex(String key, int defArgb, String descKey) {
        String defHex = toHex(defArgb);
        Property p = config.get("gui", key, defHex, I18n.format(descKey));
        p.setLanguageKey("config.spellarchives.gui." + key.toLowerCase(Locale.ROOT));
        p.setValidationPattern(HEX_COLOR_PATTERN);

        // Normalize to uppercase 0xAARRGGBB and clamp alpha
        int parsed = parseColorHex(p.getString(), defArgb);
        int norm = clampColor(parsed);
        String normHex = toHex(norm);
        if (!normHex.equalsIgnoreCase(p.getString())) p.set(normHex);

        if (!GUI_KEYS.contains(key)) GUI_KEYS.add(key);

        return norm;
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
        return INSTANCE != null ? INSTANCE.config : null;
    }

    /**
     * Returns true when panel theming visuals should be applied. This is driven by
     * explicit theming parameters (gradient/shadow) rather than a separate boolean flag.
     */
    public static boolean isPanelThemingEnabled() {
        return PANEL_GRADIENT > 0f || OUTER_SHADOW_STRENGTH > 0f;
    }

    /**
     * Normalize a packed ARGB color. Ensures channels are within 0-255 and enforces
     * a minimum alpha so fully/near-transparent colors are not accidentally used.
     */
    public static int clampColor(int v) {
        // Enforce a sensible minimum alpha to avoid invisible UI elements.
        if (((v >> 24) & 0xFF) < 0x30) v |= 0xFF000000;

        return v;
    }

    // Non-mutating helper that computes theme color presets for a single panel (left or right)
    // Returns a map of config-key -> ARGB color int
    public static Map<String, Integer> computeThemePresetColors(String theme, boolean leftPanel) {
        Map<String, Integer> m = new HashMap<>();
        if (theme == null) return m;

        switch (theme.toLowerCase()) {
            case "bookshelf":
                if (leftPanel) {
                    m.put("LEFT_PANEL_FILL", 0xFF8B5A2B);
                    m.put("LEFT_PANEL_BORDER", 0xFF6F4520);
                    m.put("GROOVE_BASE", 0xFF6B3F1F);
                    m.put("GROOVE_HL", 0xFFD9B38C);
                    m.put("GROOVE_SH", 0xFF4A2A14);
                } else {
                    m.put("RIGHT_PANEL_FILL", 0xFF3E2A1A);
                    m.put("RIGHT_PANEL_BORDER", 0xFF2E1B10);
                    m.put("DETAIL_TEXT", 0xFFDCC5A3);
                }
                break;

            case "parchment":
                if (leftPanel) {
                    m.put("LEFT_PANEL_FILL", 0xFFFFF7E0);
                    m.put("LEFT_PANEL_BORDER", 0xFFDBC79A);
                    m.put("GROOVE_BASE", 0xFFF0E4C8);
                    m.put("GROOVE_HL", 0xFFFFFFFF);
                    m.put("GROOVE_SH", 0xFFD9CBA8);
                } else {
                    m.put("RIGHT_PANEL_FILL", 0xFFF8F1E0);
                    m.put("RIGHT_PANEL_BORDER", 0xFFE1D6C0);
                    m.put("DETAIL_TEXT", 0xFF5B4B3A);
                }
                break;

            case "dark":
                if (leftPanel) {
                    m.put("LEFT_PANEL_FILL", 0xFF2B2B2B);
                    m.put("LEFT_PANEL_BORDER", 0xFF1E1E1E);
                    m.put("GROOVE_BASE", 0xFF333333);
                    m.put("GROOVE_HL", 0xFF4A4A4A);
                    m.put("GROOVE_SH", 0xFF222222);
                } else {
                    m.put("RIGHT_PANEL_FILL", 0xFF1B1B1B);
                    m.put("RIGHT_PANEL_BORDER", 0xFF111111);
                    m.put("DETAIL_TEXT", 0xFFCCCCCC);
                }
                break;

            default:
                break;
        }

        return m;
    }

    /**
     * Compute theme-suggested panel-level parameters (float values) such as gradient and shadow.
     * Keys are the config keys that will be written to the config file.
     */
    public static Map<String, Float> computePanelThemeParams(String theme) {
        Map<String, Float> m = new HashMap<>();
        if (theme == null) return m;

        switch (theme.toLowerCase()) {
            case "bookshelf":
                m.put("PANEL_GRADIENT", 0.6f);
                m.put("OUTER_SHADOW_STRENGTH", 0.4f);
                break;

            case "parchment":
                m.put("PANEL_GRADIENT", 0.15f);
                m.put("OUTER_SHADOW_STRENGTH", 0.08f);
                break;

            case "dark":
                m.put("PANEL_GRADIENT", 0.05f);
                m.put("OUTER_SHADOW_STRENGTH", 0.25f);
                break;

            default:
                break;
        }

        return m;
    }
}
