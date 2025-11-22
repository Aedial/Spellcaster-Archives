package com.spellarchives.gui;

import net.minecraft.client.gui.Gui;

public class GuiUtils {
    public static void drawRoundedPanel(int x, int y, int w, int h, int radius, int fill, int border) {
        // Simple approximation: filled rect + border + clipped corners via small squares
        Gui.drawRect(x, y, x + w, y + h, fill);

        // border
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);

        // corner cutouts to fake roundness
        int r = Math.max(2, radius);

        // top-left
        Gui.drawRect(x, y, x + r - 1, y + 1, 0x00000000);
        Gui.drawRect(x, y, x + 1, y + r - 1, 0x00000000);

        // top-right
        Gui.drawRect(x + w - r + 1, y, x + w, y + 1, 0x00000000);
        Gui.drawRect(x + w - 1, y, x + w, y + r - 1, 0x00000000);

        // bottom-left
        Gui.drawRect(x, y + h - 1, x + r - 1, y + h, 0x00000000);
        Gui.drawRect(x, y + h - r + 1, x + 1, y + h, 0x00000000);

        // bottom-right
        Gui.drawRect(x + w - r + 1, y + h - 1, x + w, y + h, 0x00000000);
        Gui.drawRect(x + w - 1, y + h - r + 1, x + w, y + h, 0x00000000);
    }
}
