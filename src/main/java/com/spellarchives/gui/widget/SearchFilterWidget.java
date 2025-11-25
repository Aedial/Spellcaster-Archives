package com.spellarchives.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

import com.spellarchives.SpellArchives;
import com.spellarchives.util.TextUtils;

/**
 * Simple search text box with suggestion list below.
 * Suggestions are provided externally (already filtered by other GUI filters).
 * The widget itself only handles drawing, scrolling and notifying on text changes.
 */
public class SearchFilterWidget extends BaseWidget {
    private final FontRenderer fontRenderer;
    private String text = ""; // raw user input
    private List<String> suggestions = new ArrayList<>();
    private int scrollOffset = 0; // index of first visible suggestion
    private int caretBlinkCounter = 0;
    private boolean focused = false;
    private int lineHeight;
    private int caretIndex = 0; // caret position within text
    private int caretTick = 0;

    // Hover tracking for suggestions (to allow preview/highlight without selection)
    private int lastHoverIndex = -1;

    // Styling
    private final int panelRadius = 3;
    private final int boxFill = 0x66000000;
    private final int boxBorder = 0xFF444444;
    private final int boxBorderFocused = 0xFFAAAAAA;
    private final int textColor = 0xFFFFFF;
    private final int suggestionFill = 0x22000000;
    private final int suggestionHoverFill = 0x44FFFFFF;
    private final int suggestionBorder = 0x55222222;

    private final int innerPadX = 4;
    private final int innerPadY = 3;
    private final int clearBtnSize = 9;  // 2 + 5 + 2

    private OnChangeListener onChange;
    private OnHoverListener onHover;

    public interface OnChangeListener {
        void onChanged(String newValue);
    }

    public interface OnHoverListener {
        /**
         * Called when the hovered suggestion changes. value will be null if no suggestion hovered.
         * External GUI can use this to navigate to the correct page and highlight entries without selecting.
         */
        void onHover(String value);
    }

    public SearchFilterWidget(Minecraft mc, FontRenderer fontRenderer) {
        super(mc);
        this.fontRenderer = fontRenderer;
        this.lineHeight = fontRenderer.FONT_HEIGHT + 2; // small vertical pad per suggestion line
    }

    public void setOnChange(OnChangeListener listener) { this.onChange = listener; }
    public void setOnHover(OnHoverListener listener) { this.onHover = listener; }
    public String getText() { return text; }
    public boolean isFocused() { return focused; }

    public void setText(String t) {
        this.text = t != null ? t : "";
        this.caretIndex = Math.min(this.text.length(), this.caretIndex);
    }

    public void setSuggestions(List<String> list) {
        this.suggestions = list != null ? list : new ArrayList<>();
        clampScroll();
    }

    private void clampScroll() {
        int visibleSlots = getVisibleSuggestionSlots();
        if (scrollOffset < 0) scrollOffset = 0;

        int maxStart = Math.max(0, suggestions.size() - visibleSlots);
        if (scrollOffset > maxStart) scrollOffset = maxStart;
    }

    private int getVisibleSuggestionSlots() {
        int listTop = y + getBoxHeight();
        int available = (y + height) - listTop;
        if (available <= 0) return 0;

        return Math.max(0, available / lineHeight);
    }

    private int getBoxHeight() {
        return fontRenderer.FONT_HEIGHT + innerPadY * 2;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        caretBlinkCounter++;

        // Draw input box
        int boxH = getBoxHeight();
        int borderCol = focused ? boxBorderFocused : boxBorder;
        drawRoundedPanel(x, y, width, boxH, panelRadius, boxFill, borderCol);

        // Use the actual clear button size (depends on box height) when reserving horizontal space
        int btnSize = boxH - 2; // keep 1px margin top/bottom inside rounded panel
        int maxTextWidth = width - innerPadX * 2 - 4 - btnSize;
        String fitted = TextUtils.trimToWidth(fontRenderer, text, maxTextWidth);

        // Current text
        int textX = x + innerPadX;
        int textY = y + innerPadY + 1;
        if (!text.isEmpty()) fontRenderer.drawString(fitted, textX, textY, textColor, false);

        // Caret draw (only if within visible text)
        if (focused && (caretBlinkCounter / 40) % 2 == 0) {
            String rawLeft = text.substring(0, Math.min(caretIndex, fitted.length()));
            String fittedLeft = TextUtils.trimToWidth(fontRenderer, rawLeft, maxTextWidth);
            int caretPixel = fontRenderer.getStringWidth(fittedLeft);
            if (caretPixel <= maxTextWidth) {
                int caretXComputed = textX + caretPixel - 1;
                drawRect(caretXComputed, textY - 1, caretXComputed + 1, textY + fontRenderer.FONT_HEIGHT, boxBorderFocused);
            }
        }

        // Expanded clear button area: full height square aligned to right inner padding
        int clearRight = x + width;
        int clearLeft = clearRight - btnSize;
        int clearTop = y + 1;
        int clearBottom = clearTop + btnSize;

        // Delimiter bar to visually separate from text area
        drawRect(clearLeft - 1, y + 1, clearLeft, y + boxH - 1, borderCol);

        // Button background
        drawRect(clearLeft, clearTop, clearRight, clearBottom, 0x55222222);

        // 'x' centered
        fontRenderer.drawString("x", clearLeft + 4, clearTop + 2, borderCol, false);

        // Suggestions list
        int visibleSlots = getVisibleSuggestionSlots();
        if (!text.isEmpty() && visibleSlots > 0 && !suggestions.isEmpty()) {
            int listTop = y + boxH;
            int listH = visibleSlots * lineHeight;

            // background panel (no rounded corners for inner list)
            drawRect(x, listTop, x + width, listTop + listH, 0x66000000);
            drawRect(x, listTop, x + width, listTop + 1, suggestionBorder);
            drawRect(x, listTop + listH - 1, x + width, listTop + listH, suggestionBorder);

            for (int i = 0; i < visibleSlots; i++) {
                int idx = scrollOffset + i;
                if (idx >= suggestions.size()) break;

                String s = suggestions.get(idx);
                // Adjust rowY by +1 to avoid overlapping top border (mirrors dropdown off-by-one fixes)
                int rowY = listTop + i * lineHeight + 1;
                boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + lineHeight;
                if (hover) drawRect(x + 1, rowY, x + width - 6, rowY + lineHeight, suggestionHoverFill);
                else drawRect(x + 1, rowY, x + width - 1, rowY + lineHeight, suggestionFill);

                // Notify hover change (only once per change)
                if (hover && lastHoverIndex != idx) {
                    lastHoverIndex = idx;
                    if (onHover != null) onHover.onHover(s);
                } else if (!hover && lastHoverIndex == idx) {
                    // Hover left current row; check if any other row hovered later; if not clear.
                    boolean stillHoveringAnother = false;
                }

                int sugMaxWidth = width - innerPadX * 2 - 2;
                String trimmed = TextUtils.trimToWidth(fontRenderer, s, sugMaxWidth);
                fontRenderer.drawString(trimmed, textX, rowY + 1, textColor, false);
            }

            // Scrollbar (visual only + click-to-jump). Draw after rows.
            if (suggestions.size() > visibleSlots) {
                // Track
                int trackW = 4;
                int trackX = x + width - trackW - 1;
                int trackY = listTop + 1;
                int trackH = listH - 2;
                drawRect(trackX, trackY, trackX + trackW, trackY + trackH, 0x22000000);

                // Thumb size proportional
                int viewportH = visibleSlots * lineHeight;
                int thumbH = Math.max(6, (int)((float)viewportH * visibleSlots / suggestions.size()));
                int maxOffset = suggestions.size() - visibleSlots;
                int thumbY = trackY + (maxOffset > 0 ? (int)((trackH - thumbH) * (float)scrollOffset / maxOffset) : 0);
                drawRect(trackX + 1, thumbY, trackX + trackW - 1, thumbY + thumbH, 0x55FFFFFF);
            }

            // If mouse no longer hovering any suggestion, clear hover state
            if (!(mouseY >= listTop && mouseY < listTop + listH && mouseX >= x && mouseX < x + width)) {
                if (lastHoverIndex != -1) {
                    lastHoverIndex = -1;
                    if (onHover != null) onHover.onHover(null);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        int boxH = getBoxHeight();
        boolean insideBox = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + boxH;
        focused = insideBox;
        if (!insideBox) return false;

        // Clear button hit test (expanded area)
        if (!text.isEmpty()) {
            int btnSize = boxH - 2;
            int clearRight = x + width;
            int clearLeft = clearRight - btnSize;
            int clearTop = y + 1;
            int clearBottom = clearTop + btnSize;
            if (mouseX >= clearLeft && mouseX < clearRight && mouseY >= clearTop && mouseY < clearBottom) {
                text = "";
                caretIndex = 0;
                notifyChanged();
                lastHoverIndex = -1;
                if (onHover != null) onHover.onHover(null);

                return true;
            }
        }

        // Scrollbar click jump
        int visibleSlots = getVisibleSuggestionSlots();
        if (!text.isEmpty() && visibleSlots > 0 && !suggestions.isEmpty() && suggestions.size() > visibleSlots) {
            int listTop = y + getBoxHeight();
            int listH = visibleSlots * lineHeight;
            int trackW = 4;
            int trackX = x + width - trackW - 1;
            int trackY = listTop + 1;
            int trackH = listH - 2;
            if (mouseX >= trackX && mouseX < trackX + trackW && mouseY >= trackY && mouseY < trackY + trackH) {
                int maxOffset = suggestions.size() - visibleSlots;
                if (maxOffset > 0) {
                    int relY = mouseY - trackY;
                    scrollOffset = Math.max(0, Math.min(maxOffset, (int)( (double)relY / (double)trackH * maxOffset )));
                    clampScroll();
                }

                return true;
            }
        }

        // Click-to-set-caret (approximate based on width)
        String display = text.isEmpty() ? "" : text;
        int maxTextWidth = width - innerPadX * 2 - 4 - clearBtnSize;
        int relX = Math.max(0, Math.min(mouseX - (x + innerPadX), maxTextWidth));
        caretIndex = computeCaretIndexForPixel(display, maxTextWidth, relX);

        return true;
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!focused) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            focused = false;

            return;
        }


        if (keyCode == Keyboard.KEY_BACK) {
            if (caretIndex > 0 && !text.isEmpty()) {
                text = text.substring(0, caretIndex - 1) + text.substring(caretIndex);
                caretIndex--;
                notifyChanged();
            }

            return;
        }

        if (keyCode == Keyboard.KEY_DELETE) {
            if (caretIndex < text.length() && !text.isEmpty()) {
                text = text.substring(0, caretIndex) + text.substring(caretIndex + 1);
                notifyChanged();
            }

            return;
        }

        // Left/right move caret
        if (keyCode == Keyboard.KEY_LEFT) {
            if (caretIndex > 0) caretIndex--;

            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            if (caretIndex < text.length()) caretIndex++;

            return;
        }

        // Up/Down scroll suggestions
        if (keyCode == Keyboard.KEY_UP) {
            scrollOffset -= 1;
            clampScroll();

            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            scrollOffset += 1;
            clampScroll();

            return;
        }

        // Page Up/Down
        if (keyCode == Keyboard.KEY_PRIOR) { // PAGE UP
            scrollOffset -= Math.max(1, getVisibleSuggestionSlots() - 1);
            clampScroll();

            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) { // PAGE DOWN
            scrollOffset += Math.max(1, getVisibleSuggestionSlots() - 1);
            clampScroll();

            return;
        }

        if (Character.isISOControl(typedChar)) return;

        // add char
        text = text.substring(0, caretIndex) + typedChar + text.substring(caretIndex);
        caretIndex++;
        notifyChanged();
    }

    public void handleMouseWheel(int delta) {
        if (delta == 0) return;
        int dir = delta < 0 ? 1 : -1;
        scrollOffset += dir;
        clampScroll();
    }

    private int computeCaretIndexForPixel(String display, int maxWidth, int relPixels) {
        if (display == null) return 0;
        int len = display.length();
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= len; i++) {
            String left = TextUtils.trimToWidth(fontRenderer, display.substring(0, i), maxWidth);
            int w = fontRenderer.getStringWidth(left);
            int dist = Math.abs(w - relPixels);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }

        return best;
    }

    private void notifyChanged() {
        if (onChange != null) onChange.onChanged(text);
    }
}
