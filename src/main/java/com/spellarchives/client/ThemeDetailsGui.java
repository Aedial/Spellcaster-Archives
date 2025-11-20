package com.spellarchives.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.spellarchives.config.ClientConfig;


// Details screen for a single theme. Shows color swatches and per-color Apply buttons.
public class ThemeDetailsGui extends GuiScreen {
    private final GuiScreen parent;
    private final String theme;
    private final boolean leftPanel;

    private final List<String> colorKeys = new ArrayList<>();
    private final List<Integer> colorValues = new ArrayList<>();

    private static final int BTN_BACK = 900;
    private static final int BTN_APPLY_ALL = 901;
    // per-color buttons start at 1000

    public ThemeDetailsGui(GuiScreen parent, String theme, boolean leftPanel) {
        this.parent = parent;
        this.theme = theme;
        this.leftPanel = leftPanel;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.colorKeys.clear();
        this.colorValues.clear();

        if (theme == null) return;

        Map<String, Integer> m = ClientConfig.computeThemePresetColors(theme, leftPanel);
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            colorKeys.add(e.getKey());
            colorValues.add(e.getValue());
        }

        int startY = 60; // add spacing under title
        int xLeft = this.width / 2 - 180;
        int applyX = this.width - 110; // right-align per-color apply buttons
        for (int i = 0; i < colorKeys.size(); i++) {
            int y = startY + i * 26;
            // color preview area will be drawn; add an Apply button per color (right-aligned)
            this.buttonList.add(new GuiButton(1000 + i, applyX, y, 90, 18, I18n.format("config.spellarchives.apply")));
        }

        // Apply all + Back (centered)
        this.buttonList.add(new GuiButton(BTN_APPLY_ALL, this.width / 2 - 130, this.height - 40, 120, 20, I18n.format("config.spellarchives.apply_all")));
        this.buttonList.add(new GuiButton(BTN_BACK, this.width / 2 + 10, this.height - 40, 120, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        Configuration cfg = ClientConfig.getConfiguration();
        if (cfg == null) return;

        if (button.id >= 1000 && button.id < 2000) {
            int idx = button.id - 1000;
            if (idx >= 0 && idx < colorKeys.size()) {
                String key = colorKeys.get(idx);
                int val = colorValues.get(idx);
                String hex = String.format("0x%08X", ClientConfig.clampColor(val));

                // write to string property if necessary
                Property p = cfg.getCategory("gui").get(key);
                if (p != null && p.getType() == Property.Type.STRING) {
                    cfg.get("gui", key, hex).set(hex);
                } else {
                    cfg.get("gui", key, val).set(val);
                }

                ClientConfig.reloadFromConfig();
            }
        } else if (button.id == BTN_APPLY_ALL) {
            // apply all keys
            for (int i = 0; i < colorKeys.size(); i++) {
                String key = colorKeys.get(i);
                int val = colorValues.get(i);
                String hex = String.format("0x%08X", ClientConfig.clampColor(val));

                Property p = cfg.getCategory("gui").get(key);
                if (p != null && p.getType() == Property.Type.STRING) {
                    cfg.get("gui", key, hex).set(hex);
                } else {
                    cfg.get("gui", key, val).set(val);
                }
            }

            ClientConfig.reloadFromConfig();
        } else if (button.id == BTN_BACK) {
            this.mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRenderer, I18n.format("config.spellarchives.theme_picker"), this.width / 2, 12, 0xFFFFFF);

        int startY = 60;
        int xLeft = this.width / 2 - 180;
        for (int i = 0; i < colorKeys.size(); i++) {
            int y = startY + i * 26;
            String key = colorKeys.get(i);
            int val = colorValues.get(i);
            String name = I18n.format("config.spellarchives." + key.toLowerCase(Locale.ROOT));
            drawString(this.fontRenderer, name, xLeft, y + 6, 0xFFFFFF);

            // draw color swatch
            int swX = xLeft + 190;
            int swY = y;
            int swW = 40;
            int swH = 18;
            int color = ClientConfig.clampColor(val);
            // draw a filled rectangle for color swatch
            drawRect(swX, swY, swX + swW, swY + swH, color);

            // draw hex value (move further right to avoid squishing)
            String hx = String.format("0x%08X", color);
            int hexX = swX + swW + 12;
            drawString(this.fontRenderer, hx, hexX, y + 6, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
