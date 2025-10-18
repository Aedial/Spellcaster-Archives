package com.spellarchives.gui;

/**
 * Centralized GUI style constants used throughout the Spell Archive screens.
 * Every constant documents exactly what it controls and where it is applied.
 */
public final class GuiStyle {
    private GuiStyle() {}

    // Window sizing (applies in GuiSpellArchive.initGui)
    public static final int DEFAULT_GUI_WIDTH = 248;        // Base width if screen ratio calc is smaller
    public static final int DEFAULT_GUI_HEIGHT = 220;       // Base height if screen ratio calc is smaller
    public static final float WIDTH_RATIO = 0.5f;           // Archive window width = 50% of screen width
    public static final float HEIGHT_RATIO = 0.75f;         // Archive window height = 75% of screen height
    public static final int MIN_WIDTH = 220;                // Minimum archive window width
    public static final int MIN_HEIGHT = 180;               // Minimum archive window height

    // Panels (applies in GuiSpellArchive when laying out left/right panels)
    public static final int MARGIN = 10;                    // Outer margin around the entire window
    public static final int PANEL_RADIUS = 6;               // Rounded corner radius for the main window background
    public static final int RIGHT_PANEL_MIN_WIDTH = 120;    // Minimum width of the right details panel
    public static final float RIGHT_PANEL_RATIO = 0.38f;    // Right panel width as a fraction of total window width
    public static final int RIGHT_PANEL_RADIUS = 4;         // Corner radius for right panel background

    // Colors (applies across backgrounds and accents in GuiSpellArchive)
    public static final int BACKGROUND_FILL = 0xFFE5E5E5;   // Light grey main background fill
    public static final int BACKGROUND_BORDER = 0xFFB0B0B0; // Main background border
    public static final int RIGHT_PANEL_FILL = 0xFF000000;  // Right panel background fill (black)
    public static final int RIGHT_PANEL_BORDER = 0xFF111111;// Right panel border (dark grey)
    public static final int DETAIL_TEXT = 0xFFAAAAAA;       // Right panel secondary text (properties)
    public static final int HOVER_BORDER = 0x90FFFFFF;      // Hover outline around spines on the left

    // Grid and grooves (applies to left panel flow/grid and shelf grooves)
    public static final int GRID_INNER_PADDING = 8;         // Padding between left panel edge and content grid (x & y)
    public static final int BOTTOM_BAR_HEIGHT = 28;         // Reserved height at bottom for navigation controls
    public static final int LEFT_PANEL_BOTTOM_PAD = 12;     // Extra bottom padding under grid (inside left panel)
    public static final int CELL_W = 10;                    // Spine width (book spine tile width)
    public static final int CELL_H = 18;                    // Spine height (book spine tile height)
    public static final int ROW_GAP = 3;                    // Vertical spacing between spine rows
    public static final int GROOVE_BASE = 0xFF9A9A9A;       // Shelf groove fill color
    public static final int GROOVE_HL = 0xFFFFFFFF;         // Shelf top highlight edge color
    public static final int GROOVE_SH = 0xFF6E6E6E;         // Shelf bottom shadow edge color

    // Headers / tabs (applies to tier labels above each groove)
    public static final int HEADER_EXTRA = 2;               // Extra pixels added to font height for header row height
    public static final int TAB_RADIUS = 4;                 // Corner radius of the tier header tab above grooves
    public static final int TAB_PADDING_X = 6;              // Horizontal padding inside the tier header tab
    public static final int TAB_OFFSET_X = 0;               // X offset from groove left where the tab starts
    public static final int HEADER_TEXT_OFFSET_Y = 2;       // Additional Y offset for header text within the tab

    // Right panel content (applies inside the right details panel)
    public static final int RIGHT_PANEL_INNER_MARGIN = 10;  // Inner margin from right panel edges to content
    public static final int RIGHT_PANEL_TEXT_SIDE_PAD = 10; // Side padding used for description wrap width
    public static final int RIGHT_TITLE_ICON_SIZE = 16;     // Size of the top-left icon in the right panel header
    public static final int RIGHT_TITLE_TEXT_GAP = 24;      // X gap from left content edge to header text start
    public static final int RIGHT_AFTER_HEADER_GAP = 22;    // Vertical gap after header row before next line
    public static final int RIGHT_LINE_GAP_SMALL = 12;      // Vertical spacing between property lines
    public static final int RIGHT_LINE_GAP_MEDIUM = 18;     // Vertical spacing for tier/element lines
    public static final int RIGHT_SECTION_GAP = 12;         // Extra space before description section
    public static final int RIGHT_ELEMENT_ICON_SIZE = 8;    // Size of the element icon in the right panel
    public static final int RIGHT_DESC_LINE_HEIGHT = 10;    // Line advance for wrapped description text
    public static final int RIGHT_BOTTOM_CLAMP_MARGIN = 14; // Bottom margin to stop drawing near panel edge

    // Navigation (applies to page arrows layout)
    public static final int NAV_BUTTON_SIZE = 20;           // Size of prev/next navigation buttons
    public static final int ARROWS_Y_OFFSET = 6;            // Additional Y offset for buttons row vs grid end

    // Left spine rendering (book spines in the left panel)
    public static final int SPINE_LEFT_BORDER = 1;           // Left spacing before spine fill
    public static final int SPINE_TOP_BORDER = 1;            // Top spacing inside groove
    public static final int SPINE_BOTTOM_BORDER = 1;         // Bottom spacing inside groove

    // Spine icon (small element icon drawn on each spine)
    public static final int SPINE_ICON_SIZE = 8;             // Size of the element icon on the spine
    public static final int SPINE_ICON_BOTTOM_MARGIN = 1;    // Bottom margin below the icon
    public static final int SPINE_ICON_Y_OFFSET = 0;         // Additional Y offset applied to icon position

    // Spine shading toggles and parameters
    public static final boolean SPINE_ENABLE_CURVATURE = true; // Enable curved brightness from center to edges
    public static final boolean SPINE_ENABLE_TILT = true;       // Asymmetric tilt for one side
    public static final boolean SPINE_ENABLE_NOISE = true;      // Subtle per-pixel noise on the spine
    public static final boolean SPINE_ENABLE_BANDS = true;      // Horizontal decorative bands
    public static final boolean SPINE_EMBED_ICON = true;        // Bake element icon into the spine texture instead of overlay

    public static final float SPINE_CENTER_BRIGHTEN = 1.07f;  // Brightness multiplier at center of spine
    public static final float SPINE_EDGE_FACTOR = 0.25f;       // How fast brightness falls to edges (larger = darker edges)
    public static final float SPINE_VSHADE_BASE = 0.96f;       // Vertical shading: base factor near top/bottom
    public static final float SPINE_VSHADE_RANGE = 0.08f;      // Vertical shading: range added toward center
    public static final float SPINE_NOISE_AMPLITUDE = 0.03f;   // Amplitude of noise (+/- percentage)

    // Bands: two lines near the top of available spine area (space, line, space, line)
    public static final int SPINE_BAND_THICKNESS = 2;         // Thickness in pixels of each band
    public static final int SPINE_BAND_GAP = 1;               // Gap between the two bands
    public static final int SPINE_BAND_TOP_SPACE = 1;         // Space from top of available area to first band
    public static final float SPINE_BAND1_DARKEN = 0.8f;     // Darkening factor for first band
    public static final float SPINE_BAND2_DARKEN = 0.85f;     // Darkening factor for second band
}
