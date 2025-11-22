package com.spellarchives.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public abstract class BaseWidget extends Gui {
    protected final Minecraft mc;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    public BaseWidget(Minecraft mc) {
        this.mc = mc;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public abstract void draw(int mouseX, int mouseY, float partialTicks);

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    protected void drawRoundedPanel(int x, int y, int w, int h, int radius, int fill, int border) {
        // Simple approximation: filled rect + border + clipped corners via small squares
        drawRect(x, y, x + w, y + h, fill);

        // border
        drawRect(x, y, x + w, y + 1, border);
        drawRect(x, y + h - 1, x + w, y + h, border);
        drawRect(x, y, x + 1, y + h, border);
        drawRect(x + w - 1, y, x + w, y + h, border);

        // corner cutouts to fake roundness
        int r = Math.max(2, radius);

        // top-left
        drawRect(x, y, x + r - 1, y + 1, 0x00000000);
        drawRect(x, y, x + 1, y + r - 1, 0x00000000);

        // top-right
        drawRect(x + w - r + 1, y, x + w, y + 1, 0x00000000);
        drawRect(x + w - 1, y, x + w, y + r - 1, 0x00000000);

        // bottom-left
        drawRect(x, y + h - 1, x + r - 1, y + h, 0x00000000);
        drawRect(x, y + h - r + 1, x + 1, y + h, 0x00000000);

        // bottom-right
        drawRect(x + w - r + 1, y + h - 1, x + w, y + h, 0x00000000);
        drawRect(x + w - 1, y + h - r + 1, x + w, y + h, 0x00000000);
    }
}
