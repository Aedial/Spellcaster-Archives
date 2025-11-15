package com.spellarchives.gui;

import java.util.HashMap;
import java.util.Map;

import com.spellarchives.SpellArchives;
import com.spellarchives.client.ClientConfig;


/**
 * Centralized GUI style constants used throughout the Spellcaster's Archives screens.
 * Every constant documents exactly what it controls and where it is applied.
 */
public final class GuiStyle {
    private GuiStyle() {}

    // Incremented each time reloadFromConfig() runs so GUIs can invalidate caches
    public static int CONFIG_REVISION = 0;

    // -- Backing fields (mutable via config reload). Public getters are used by code.
    private static int DEFAULT_GUI_WIDTH_v = 248;           // Base width if screen ratio calc is smaller
    private static int DEFAULT_GUI_HEIGHT_v = 220;          // Base height if screen ratio calc is smaller
    private static float WIDTH_RATIO_v = 0.5f;              // Archives window width = 50% of screen width
    private static float HEIGHT_RATIO_v = 0.75f;            // Archives window height = 75% of screen height
    private static int MIN_WIDTH_v = 220;                   // Minimum archive window width
    private static int MIN_HEIGHT_v = 180;                  // Minimum archive window height

    // Panels (applies in GuiSpellArchive when laying out left/right panels)
    private static int MARGIN_v = 10;                       // Outer margin around the entire window
    private static int PANEL_RADIUS_v = 6;                  // Rounded corner radius for the main window background
    private static int RIGHT_PANEL_MIN_WIDTH_v = 120;       // Minimum width of the right details panel
    private static float RIGHT_PANEL_RATIO_v = 0.38f;       // Right panel width as a fraction of total window width
    private static int RIGHT_PANEL_RADIUS_v = 4;            // Corner radius for right panel background

    // Colors (applies across backgrounds and accents in GuiSpellArchive)
    // Default palette: left panel defaults to a warm wood look; right
    // panel defaults to a neutral black background for maximum contrast.
    private static int BACKGROUND_FILL_v = 0xFF8B5A2B;      // Warm wood main background fill
    private static int BACKGROUND_BORDER_v = 0xFF6F4520;    // Darker wood border
    private static int RIGHT_PANEL_FILL_v = 0xFF000000;     // Default right panel: black
    private static int RIGHT_PANEL_BORDER_v = 0xFF111111;   // Right panel border (near-black)
    private static int DETAIL_TEXT_v = 0xFFAAAAAA;          // Light detail text on dark right panel
    private static int HOVER_BORDER_v = 0x90FFFFFF;         // Hover outline around spines on the left

    // Grid and grooves (applies to left panel flow/grid and shelf grooves)
    private static int GRID_INNER_PADDING_v = 8;            // Padding between left panel edge and content grid (x & y)
    private static int BOTTOM_BAR_HEIGHT_v = 28;            // Reserved height at bottom for navigation controls
    private static int LEFT_PANEL_BOTTOM_PAD_v = 12;        // Extra bottom padding under grid (inside left panel)
    private static int CELL_W_v = 10;                       // Spine width (book spine tile width)
    private static int CELL_H_v = 18;                       // Spine height (book spine tile height)
    private static int ROW_GAP_v = 3;                       // Vertical spacing between spine rows
    private static int GROOVE_BASE_v = 0xFF6B3F1F;          // Shelf groove (wood) fill color
    private static int GROOVE_HL_v = 0xFFD9B38C;            // Shelf top highlight (lighter wood)
    private static int GROOVE_SH_v = 0xFF4A2A14;            // Shelf bottom shadow (darker)

    // Headers / tabs (applies to tier labels above each groove)
    private static int HEADER_EXTRA_v = 2;                  // Extra pixels added to font height for header row height
    private static int TAB_RADIUS_v = 4;                    // Corner radius of the tier header tab above grooves
    private static int TAB_PADDING_X_v = 6;                 // Horizontal padding inside the tier header tab
    private static int TAB_OFFSET_X_v = 0;                  // X offset from groove left where the tab starts
    private static int HEADER_TEXT_OFFSET_Y_v = 2;          // Additional Y offset for header text within the tab

    // Right panel content (applies inside the right details panel)
    private static int RIGHT_PANEL_INNER_MARGIN_v = 10;     // Inner margin from right panel edges to content
    private static int RIGHT_PANEL_TEXT_SIDE_PAD_v = 10;    // Side padding used for description wrap width
    private static int RIGHT_TITLE_ICON_SIZE_v = 16;        // Size of the top-left icon in the right panel header
    private static int RIGHT_TITLE_TEXT_GAP_v = 24;         // X gap from left content edge to header text start
    private static int RIGHT_AFTER_HEADER_GAP_v = 22;       // Vertical gap after header row before next line
    private static int RIGHT_LINE_GAP_SMALL_v = 12;         // Vertical spacing between property lines
    private static int RIGHT_LINE_GAP_MEDIUM_v = 18;        // Vertical spacing for tier/element lines
    private static int RIGHT_SECTION_GAP_v = 12;            // Extra space before description section
    private static int RIGHT_ELEMENT_ICON_SIZE_v = 8;       // Size of the element icon in the right panel
    private static int RIGHT_DESC_LINE_HEIGHT_v = 10;       // Line advance for wrapped description text
    private static int RIGHT_BOTTOM_CLAMP_MARGIN_v = 14;    // Bottom margin to stop drawing near panel edge

    // Navigation (applies to page arrows layout)
    private static int NAV_BUTTON_SIZE_v = 20;              // Size of prev/next navigation buttons

    // Scroll slot styling (identification scroll reserve)
    public static final int SCROLL_SLOT_SIDE_GAP = 4;
    public static final int SCROLL_SLOT_RADIUS = 3;
    public static final int SCROLL_SLOT_BG = 0xAA222222;
    public static final int SCROLL_SLOT_BORDER = 0xFF000000;
    public static final int SCROLL_SLOT_BG_DISABLED = 0x55222222;
    public static final int SCROLL_SLOT_BORDER_DISABLED = 0x66000000;
    public static int SCROLL_SLOT_MAX_SIZE = 20;

    private static int ARROWS_Y_OFFSET_v = 6;               // Additional Y offset for buttons row vs grid end

    // Left spine rendering (book spines in the left panel)
    private static int SPINE_LEFT_BORDER_v = 1;             // Left spacing before spine fill
    private static int SPINE_TOP_BORDER_v = 1;              // Top spacing inside groove
    private static int SPINE_BOTTOM_BORDER_v = 1;           // Bottom spacing inside groove

    // Spine icon (small element icon drawn on each spine)
    private static int SPINE_ICON_SIZE_v = 8;               // Size of the element icon on the spine
    private static int SPINE_ICON_BOTTOM_MARGIN_v = 1;      // Bottom margin below the icon
    private static int SPINE_ICON_Y_OFFSET_v = 0;           // Additional Y offset applied to icon position

    // Spine shading toggles and parameters
    private static boolean SPINE_ENABLE_CURVATURE_v = true; // Enable curved brightness from center to edges
    private static boolean SPINE_ENABLE_TILT_v = true;      // Asymmetric tilt for one side
    private static boolean SPINE_ENABLE_NOISE_v = true;     // Subtle per-pixel noise on the spine
    private static boolean SPINE_ENABLE_BANDS_v = true;     // Horizontal decorative bands
    private static boolean SPINE_EMBED_ICON_v = true;       // Bake element icon into the spine texture instead of overlay

    private static float SPINE_CENTER_BRIGHTEN_v = 1.07f;   // Brightness multiplier at center of spine
    private static float SPINE_EDGE_FACTOR_v = 0.25f;       // How fast brightness falls to edges (larger = darker edges)
    private static float SPINE_VSHADE_BASE_v = 0.96f;       // Vertical shading: base factor near top/bottom
    private static float SPINE_VSHADE_RANGE_v = 0.08f;      // Vertical shading: range added toward center
    private static float SPINE_NOISE_AMPLITUDE_v = 0.03f;   // Amplitude of noise (+/- percentage)

    // Bands: two lines near the top of available spine area (space, line, space, line)
    private static int SPINE_BAND_THICKNESS_v = 2;          // Thickness in pixels of each band
    private static int SPINE_BAND_GAP_v = 1;                // Gap between the two bands
    private static int SPINE_BAND_TOP_SPACE_v = 1;          // Space from top of available area to first band
    private static float SPINE_BAND1_DARKEN_v = 0.8f;       // Darkening factor for first band
    private static float SPINE_BAND2_DARKEN_v = 0.85f;      // Darkening factor for second band

    // Screen darken factor when GUI is open (0.0 = no darken, 1.0 = fully black)
    private static float SCREEN_DARKEN_FACTOR_v = 0.55f;
    // Panel theming visual parameters (theme-agnostic; used by multiple themes)
    private static float PANEL_GRADIENT_v = 0.6f;           // strength of vertical gradient applied to themed panel backgrounds
    private static float OUTER_SHADOW_STRENGTH_v = 0.4f;    // strength of subtle inner/outer shadow when panel theming is used

    // -- Public accessors
    public static int DEFAULT_GUI_WIDTH = DEFAULT_GUI_WIDTH_v;
    public static int DEFAULT_GUI_HEIGHT = DEFAULT_GUI_HEIGHT_v;
    public static float WIDTH_RATIO = WIDTH_RATIO_v;
    public static float HEIGHT_RATIO = HEIGHT_RATIO_v;
    public static int MIN_WIDTH = MIN_WIDTH_v;
    public static int MIN_HEIGHT = MIN_HEIGHT_v;

    public static int MARGIN = MARGIN_v;
    public static int PANEL_RADIUS = PANEL_RADIUS_v;
    public static int RIGHT_PANEL_MIN_WIDTH = RIGHT_PANEL_MIN_WIDTH_v;
    public static float RIGHT_PANEL_RATIO = RIGHT_PANEL_RATIO_v;
    public static int RIGHT_PANEL_RADIUS = RIGHT_PANEL_RADIUS_v;

    public static int BACKGROUND_FILL = BACKGROUND_FILL_v;
    public static int BACKGROUND_BORDER = BACKGROUND_BORDER_v;
    public static int RIGHT_PANEL_FILL = RIGHT_PANEL_FILL_v;
    public static int RIGHT_PANEL_BORDER = RIGHT_PANEL_BORDER_v;
    public static int DETAIL_TEXT = DETAIL_TEXT_v;
    public static int HOVER_BORDER = HOVER_BORDER_v;

    public static int GRID_INNER_PADDING = GRID_INNER_PADDING_v;
    public static int BOTTOM_BAR_HEIGHT = BOTTOM_BAR_HEIGHT_v;
    public static int LEFT_PANEL_BOTTOM_PAD = LEFT_PANEL_BOTTOM_PAD_v;
    public static int CELL_W = CELL_W_v;
    public static int CELL_H = CELL_H_v;
    public static int ROW_GAP = ROW_GAP_v;
    public static int GROOVE_BASE = GROOVE_BASE_v;
    public static int GROOVE_HL = GROOVE_HL_v;
    public static int GROOVE_SH = GROOVE_SH_v;

    public static int HEADER_EXTRA = HEADER_EXTRA_v;
    public static int TAB_RADIUS = TAB_RADIUS_v;
    public static int TAB_PADDING_X = TAB_PADDING_X_v;
    public static int TAB_OFFSET_X = TAB_OFFSET_X_v;
    public static int HEADER_TEXT_OFFSET_Y = HEADER_TEXT_OFFSET_Y_v;

    public static int RIGHT_PANEL_INNER_MARGIN = RIGHT_PANEL_INNER_MARGIN_v;
    public static int RIGHT_PANEL_TEXT_SIDE_PAD = RIGHT_PANEL_TEXT_SIDE_PAD_v;
    public static int RIGHT_TITLE_ICON_SIZE = RIGHT_TITLE_ICON_SIZE_v;
    public static int RIGHT_TITLE_TEXT_GAP = RIGHT_TITLE_TEXT_GAP_v;
    public static int RIGHT_AFTER_HEADER_GAP = RIGHT_AFTER_HEADER_GAP_v;
    public static int RIGHT_LINE_GAP_SMALL = RIGHT_LINE_GAP_SMALL_v;
    public static int RIGHT_LINE_GAP_MEDIUM = RIGHT_LINE_GAP_MEDIUM_v;
    public static int RIGHT_SECTION_GAP = RIGHT_SECTION_GAP_v;
    public static int RIGHT_ELEMENT_ICON_SIZE = RIGHT_ELEMENT_ICON_SIZE_v;
    public static int RIGHT_DESC_LINE_HEIGHT = RIGHT_DESC_LINE_HEIGHT_v;
    public static int RIGHT_BOTTOM_CLAMP_MARGIN = RIGHT_BOTTOM_CLAMP_MARGIN_v;

    public static int NAV_BUTTON_SIZE = NAV_BUTTON_SIZE_v;
    public static int ARROWS_Y_OFFSET = ARROWS_Y_OFFSET_v;

    public static int SPINE_LEFT_BORDER = SPINE_LEFT_BORDER_v;
    public static int SPINE_TOP_BORDER = SPINE_TOP_BORDER_v;
    public static int SPINE_BOTTOM_BORDER = SPINE_BOTTOM_BORDER_v;

    public static int SPINE_ICON_SIZE = SPINE_ICON_SIZE_v;
    public static int SPINE_ICON_BOTTOM_MARGIN = SPINE_ICON_BOTTOM_MARGIN_v;
    public static int SPINE_ICON_Y_OFFSET = SPINE_ICON_Y_OFFSET_v;

    public static boolean SPINE_ENABLE_CURVATURE = SPINE_ENABLE_CURVATURE_v;
    public static boolean SPINE_ENABLE_TILT = SPINE_ENABLE_TILT_v;
    public static boolean SPINE_ENABLE_NOISE = SPINE_ENABLE_NOISE_v;
    public static boolean SPINE_ENABLE_BANDS = SPINE_ENABLE_BANDS_v;
    public static boolean SPINE_EMBED_ICON = SPINE_EMBED_ICON_v;

    public static float SPINE_CENTER_BRIGHTEN = SPINE_CENTER_BRIGHTEN_v;
    public static float SPINE_EDGE_FACTOR = SPINE_EDGE_FACTOR_v;
    public static float SPINE_VSHADE_BASE = SPINE_VSHADE_BASE_v;
    public static float SPINE_VSHADE_RANGE = SPINE_VSHADE_RANGE_v;
    public static float SPINE_NOISE_AMPLITUDE = SPINE_NOISE_AMPLITUDE_v;

    public static int SPINE_BAND_THICKNESS = SPINE_BAND_THICKNESS_v;
    public static int SPINE_BAND_GAP = SPINE_BAND_GAP_v;
    public static int SPINE_BAND_TOP_SPACE = SPINE_BAND_TOP_SPACE_v;
    public static float SPINE_BAND1_DARKEN = SPINE_BAND1_DARKEN_v;
    public static float SPINE_BAND2_DARKEN = SPINE_BAND2_DARKEN_v;

    public static float SCREEN_DARKEN_FACTOR = SCREEN_DARKEN_FACTOR_v;
    public static float PANEL_GRADIENT = PANEL_GRADIENT_v;
    public static float OUTER_SHADOW_STRENGTH = OUTER_SHADOW_STRENGTH_v;

    /**
     * Returns true when panel theming visuals should be applied. This is driven by
     * explicit theming parameters (gradient/shadow) rather than a separate boolean flag.
     */
    public static boolean isPanelThemingEnabled() {
        return PANEL_GRADIENT > 0f || OUTER_SHADOW_STRENGTH > 0f;
    }

    /**
     * Reloads configurable values from the client config. Safe to call on client pre-init.
     */
    public static void reloadFromConfig() {
        try {
            // ClientConfig should already be initialized during client preInit.
            // reloadFromConfig only reads values via ClientConfig accessors.

            DEFAULT_GUI_WIDTH = ClientConfig.getInt("DEFAULT_GUI_WIDTH", DEFAULT_GUI_WIDTH);
            DEFAULT_GUI_HEIGHT = ClientConfig.getInt("DEFAULT_GUI_HEIGHT", DEFAULT_GUI_HEIGHT);
            WIDTH_RATIO = ClientConfig.getFloat("WIDTH_RATIO", WIDTH_RATIO);
            HEIGHT_RATIO = ClientConfig.getFloat("HEIGHT_RATIO", HEIGHT_RATIO);
            MIN_WIDTH = ClientConfig.getInt("MIN_WIDTH", MIN_WIDTH);
            MIN_HEIGHT = ClientConfig.getInt("MIN_HEIGHT", MIN_HEIGHT);

            MARGIN = ClientConfig.getInt("MARGIN", MARGIN);
            PANEL_RADIUS = ClientConfig.getInt("PANEL_RADIUS", PANEL_RADIUS);
            RIGHT_PANEL_MIN_WIDTH = ClientConfig.getInt("RIGHT_PANEL_MIN_WIDTH", RIGHT_PANEL_MIN_WIDTH);
            RIGHT_PANEL_RATIO = ClientConfig.getFloat("RIGHT_PANEL_RATIO", RIGHT_PANEL_RATIO);
            RIGHT_PANEL_RADIUS = ClientConfig.getInt("RIGHT_PANEL_RADIUS", RIGHT_PANEL_RADIUS);

            BACKGROUND_FILL = ClientConfig.getInt("LEFT_PANEL_FILL", BACKGROUND_FILL);
            BACKGROUND_BORDER = ClientConfig.getInt("LEFT_PANEL_BORDER", BACKGROUND_BORDER);
            RIGHT_PANEL_FILL = ClientConfig.getInt("RIGHT_PANEL_FILL", RIGHT_PANEL_FILL);
            RIGHT_PANEL_BORDER = ClientConfig.getInt("RIGHT_PANEL_BORDER", RIGHT_PANEL_BORDER);

            // Note: themes are applied when the user explicitly chooses them in the config GUI.
            // At initial load we only read the explicit color fields so users can override values.
            DETAIL_TEXT = ClientConfig.getInt("DETAIL_TEXT", DETAIL_TEXT);
            HOVER_BORDER = ClientConfig.getInt("HOVER_BORDER", HOVER_BORDER);

            GRID_INNER_PADDING = ClientConfig.getInt("GRID_INNER_PADDING", GRID_INNER_PADDING);
            BOTTOM_BAR_HEIGHT = ClientConfig.getInt("BOTTOM_BAR_HEIGHT", BOTTOM_BAR_HEIGHT);
            LEFT_PANEL_BOTTOM_PAD = ClientConfig.getInt("LEFT_PANEL_BOTTOM_PAD", LEFT_PANEL_BOTTOM_PAD);
            CELL_W = ClientConfig.getInt("CELL_W", CELL_W);
            CELL_H = ClientConfig.getInt("CELL_H", CELL_H);
            ROW_GAP = ClientConfig.getInt("ROW_GAP", ROW_GAP);

            GROOVE_BASE = ClientConfig.getInt("GROOVE_BASE", GROOVE_BASE);
            GROOVE_HL = ClientConfig.getInt("GROOVE_HL", GROOVE_HL);
            GROOVE_SH = ClientConfig.getInt("GROOVE_SH", GROOVE_SH);

            // Nav/buttons
            NAV_BUTTON_SIZE = ClientConfig.getInt("NAV_BUTTON_SIZE", NAV_BUTTON_SIZE);
            ARROWS_Y_OFFSET = ClientConfig.getInt("ARROWS_Y_OFFSET", ARROWS_Y_OFFSET);
            SCROLL_SLOT_MAX_SIZE = ClientConfig.getInt("SCROLL_SLOT_MAX_SIZE", SCROLL_SLOT_MAX_SIZE);

            // Spine toggles
            SPINE_ENABLE_CURVATURE = ClientConfig.getBoolean("SPINE_ENABLE_CURVATURE", SPINE_ENABLE_CURVATURE);
            SPINE_ENABLE_TILT = ClientConfig.getBoolean("SPINE_ENABLE_TILT", SPINE_ENABLE_TILT);
            SPINE_ENABLE_NOISE = ClientConfig.getBoolean("SPINE_ENABLE_NOISE", SPINE_ENABLE_NOISE);
            SPINE_ENABLE_BANDS = ClientConfig.getBoolean("SPINE_ENABLE_BANDS", SPINE_ENABLE_BANDS);
            SPINE_EMBED_ICON = ClientConfig.getBoolean("SPINE_EMBED_ICON", SPINE_EMBED_ICON);

            SPINE_BAND_THICKNESS = ClientConfig.getInt("SPINE_BAND_THICKNESS", SPINE_BAND_THICKNESS);
            SPINE_BAND_GAP = ClientConfig.getInt("SPINE_BAND_GAP", SPINE_BAND_GAP);
            SPINE_BAND_TOP_SPACE = ClientConfig.getInt("SPINE_BAND_TOP_SPACE", SPINE_BAND_TOP_SPACE);

            // Screen darken
            SCREEN_DARKEN_FACTOR = ClientConfig.getFloat("SCREEN_DARKEN_FACTOR", SCREEN_DARKEN_FACTOR);

            PANEL_GRADIENT = ClientConfig.getFloat("PANEL_GRADIENT", PANEL_GRADIENT);
            OUTER_SHADOW_STRENGTH = ClientConfig.getFloat("OUTER_SHADOW_STRENGTH", OUTER_SHADOW_STRENGTH);

            // bump revision so GUIs can clear cached resources that depend on config
            CONFIG_REVISION++;

        } catch (RuntimeException e) {
            // Fail-safe: don't crash startup if config is unavailable on server-side or errors occur
            SpellArchives.LOGGER.warn("Failed to reload GuiStyle from client config: " + e.getMessage());
        }
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
